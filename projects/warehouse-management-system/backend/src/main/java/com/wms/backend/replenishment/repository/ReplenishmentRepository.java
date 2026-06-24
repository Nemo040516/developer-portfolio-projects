/**
 * @file 速览索引
 * @summary 补货仓储层，负责补货主表/明细读写、计算输入聚合与E1统计指标查询。
 * @core 1. 补货计划分页与详情查询
 * @core 2. 补货计划与明细写入/状态更新
 * @core 3. 计算输入聚合（库存+销量+推荐）
 * @core 4. 指标统计查询（采纳率/干预率/命中率/MAPE/周转率）
 * @entry 先看：pagePlansByKeyword、listCalcInputs、queryMetricsOverview、queryMetricsMape
 * @deps 关键依赖：JdbcTemplate、wms_replenishment_*、wms_sales_daily、wms_inventory_stock
 * @risk 高风险修改点：统计SQL口径、状态过滤范围、慢查询阈值日志
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java
 */
package com.wms.backend.replenishment.repository;

import com.wms.backend.replenishment.dto.ReplenishmentItemResponse;
import com.wms.backend.replenishment.dto.ReplenishmentPlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * M6 仓储访问层：
 * 1) 负责补货建议主表/明细读写；
 * 2) 负责提供建议计算的输入数据聚合。
 */
@Repository
public class ReplenishmentRepository {

    private static final Logger log = LoggerFactory.getLogger(ReplenishmentRepository.class);
    private static final long SLOW_QUERY_THRESHOLD_MILLIS = 300L;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ReplenishmentPlanResponse> planRowMapper = (rs, rowNum) ->
            new ReplenishmentPlanResponse(
                    rs.getLong("id"),
                    rs.getString("plan_no"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getInt("status"),
                    rs.getInt("calc_days"),
                    rs.getInt("lead_time_days"),
                    rs.getInt("safety_days"),
                    rs.getString("purchase_draft_no"),
                    rs.getString("remark"),
                    rs.getObject("created_by", Long.class),
                    toLocalDateTime(rs.getTimestamp("generated_at")),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<ReplenishmentItemResponse> itemRowMapper = (rs, rowNum) ->
            new ReplenishmentItemResponse(
                    rs.getLong("id"),
                    rs.getLong("plan_id"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("current_qty"),
                    rs.getInt("safe_qty"),
                    rs.getBigDecimal("predicted_daily_sales"),
                    rs.getInt("predicted_total_qty"),
                    rs.getInt("shortage_qty"),
                    rs.getInt("suggested_qty"),
                    rs.getInt("final_qty"),
                    rs.getString("reco_source"),
                    rs.getBigDecimal("confidence"),
                    rs.getString("reason")
            );

    private final RowMapper<CalcInput> calcInputRowMapper = (rs, rowNum) ->
            new CalcInput(
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("current_qty"),
                    rs.getInt("safe_qty"),
                    rs.getBigDecimal("predicted_daily_sales"),
                    rs.getString("related_sku_code"),
                    rs.getBigDecimal("confidence")
            );

    private final RowMapper<MetricsTopAdjustSkuRaw> topAdjustSkuRowMapper = (rs, rowNum) ->
            new MetricsTopAdjustSkuRaw(
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("adjust_abs_qty_total"),
                    rs.getInt("adjust_item_count")
            );

    public ReplenishmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分页计数：支持按计划号/仓库关键字 + 状态过滤。
     */
    public long countPlansByKeyword(
            String keyword,
            Integer status,
            LocalDateTime generatedDateStart,
            LocalDateTime generatedDateEnd
    ) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_replenishment_plan p
                JOIN wms_warehouse w ON p.warehouse_id = w.id
                WHERE (? = '' OR p.plan_no LIKE ? OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR p.status = ?)
                  AND (? IS NULL OR p.generated_at >= ?)
                  AND (? IS NULL OR p.generated_at < ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                keyword,
                like,
                like,
                like,
                status,
                status,
                generatedDateStart,
                generatedDateStart,
                generatedDateEnd,
                generatedDateEnd
        );
        return count == null ? 0L : count;
    }

    /**
     * 分页查询补货建议主表。
     */
    public List<ReplenishmentPlanResponse> pagePlansByKeyword(
            String keyword,
            Integer status,
            LocalDateTime generatedDateStart,
            LocalDateTime generatedDateEnd,
            int offset,
            int size
    ) {
        String sql = """
                SELECT p.id, p.plan_no, p.warehouse_id, w.warehouse_code, w.warehouse_name,
                       p.status, p.calc_days, p.lead_time_days, p.safety_days, p.purchase_draft_no,
                       p.remark, p.created_by, p.generated_at, p.created_at, p.updated_at
                FROM wms_replenishment_plan p
                JOIN wms_warehouse w ON p.warehouse_id = w.id
                WHERE (? = '' OR p.plan_no LIKE ? OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR p.status = ?)
                  AND (? IS NULL OR p.generated_at >= ?)
                  AND (? IS NULL OR p.generated_at < ?)
                ORDER BY p.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(
                sql,
                planRowMapper,
                keyword,
                like,
                like,
                like,
                status,
                status,
                generatedDateStart,
                generatedDateStart,
                generatedDateEnd,
                generatedDateEnd,
                size,
                offset
        );
    }

    /**
     * 按主键查询计划。
     */
    public Optional<ReplenishmentPlanResponse> findPlanById(Long id) {
        String sql = """
                SELECT p.id, p.plan_no, p.warehouse_id, w.warehouse_code, w.warehouse_name,
                       p.status, p.calc_days, p.lead_time_days, p.safety_days, p.purchase_draft_no,
                       p.remark, p.created_by, p.generated_at, p.created_at, p.updated_at
                FROM wms_replenishment_plan p
                JOIN wms_warehouse w ON p.warehouse_id = w.id
                WHERE p.id = ?
                """;
        return jdbcTemplate.query(sql, planRowMapper, id).stream().findFirst();
    }

    /**
     * 查询计划明细。
     */
    public List<ReplenishmentItemResponse> listItemsByPlanId(Long planId) {
        String sql = """
                SELECT i.id, i.plan_id, i.sku_id, s.sku_code, s.sku_name,
                       i.current_qty, i.safe_qty, i.predicted_daily_sales, i.predicted_total_qty,
                       i.shortage_qty, i.suggested_qty, i.final_qty, i.reco_source, i.confidence, i.reason
                FROM wms_replenishment_item i
                JOIN wms_sku s ON i.sku_id = s.id
                WHERE i.plan_id = ?
                ORDER BY i.id ASC
                """;
        return jdbcTemplate.query(sql, itemRowMapper, planId);
    }

    /**
     * 读取建议计算输入：
     * - 当前库存来自 wms_inventory_stock；
     * - 安全库存优先取预警规则 safe_qty，再回退 SKU.safe_stock；
     * - 预测日均来自销量日聚合表；
     * - 推荐来源取同 SKU 置信度最高的一条关联。
     */
    public List<CalcInput> listCalcInputs(Long warehouseId, LocalDate startDate) {
        String sql = """
                SELECT s.id AS sku_id,
                       s.sku_code,
                       s.sku_name,
                       COALESCE(st.on_hand_qty, 0) AS current_qty,
                       COALESCE(ar.safe_qty, s.safe_stock, 0) AS safe_qty,
                       COALESCE(ds.avg_daily_sales, 0.00) AS predicted_daily_sales,
                       rs.sku_code AS related_sku_code,
                       rp.confidence
                FROM wms_sku s
                LEFT JOIN wms_inventory_stock st
                    ON st.warehouse_id = ?
                   AND st.sku_id = s.id
                LEFT JOIN wms_stock_alert_rule ar
                    ON ar.warehouse_id = ?
                   AND ar.sku_id = s.id
                   AND ar.status = 1
                LEFT JOIN (
                    SELECT sku_id, ROUND(AVG(outbound_qty), 2) AS avg_daily_sales
                    FROM wms_sales_daily
                    WHERE warehouse_id = ?
                      AND stat_date >= ?
                    GROUP BY sku_id
                ) ds
                    ON ds.sku_id = s.id
                LEFT JOIN wms_reco_pair rp
                    ON rp.id = (
                        SELECT x.id
                        FROM wms_reco_pair x
                        WHERE x.sku_id = s.id
                        ORDER BY x.confidence DESC, x.support_count DESC, x.id ASC
                        LIMIT 1
                    )
                LEFT JOIN wms_sku rs ON rs.id = rp.related_sku_id
                WHERE s.status = 1
                ORDER BY s.id ASC
                """;
        return jdbcTemplate.query(sql, calcInputRowMapper, warehouseId, warehouseId, warehouseId, startDate);
    }

    /**
     * 校验仓库是否可用。
     */
    public boolean existsWarehouseActive(Long warehouseId) {
        String sql = "SELECT COUNT(1) FROM wms_warehouse WHERE id = ? AND status = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, warehouseId);
        return count != null && count > 0;
    }

    /**
     * 通过用户名查询用户ID，用于落创建人信息。
     */
    public Optional<Long> findUserIdByUsername(String username) {
        String sql = "SELECT id FROM sys_user WHERE username = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), username);
        return list.stream().findFirst();
    }

    /**
     * 新增建议计划主表。
     */
    public int insertPlan(
            String planNo,
            Long warehouseId,
            int calcDays,
            int leadTimeDays,
            int safetyDays,
            String remark,
            Long createdBy
    ) {
        String sql = """
                INSERT INTO wms_replenishment_plan
                (plan_no, warehouse_id, status, calc_days, lead_time_days, safety_days, purchase_draft_no, remark, created_by)
                VALUES (?, ?, 0, ?, ?, ?, NULL, ?, ?)
                """;
        return jdbcTemplate.update(sql, planNo, warehouseId, calcDays, leadTimeDays, safetyDays, remark, createdBy);
    }

    /**
     * 新增建议明细。
     */
    public int insertItem(
            Long planId,
            Long skuId,
            int currentQty,
            int safeQty,
            BigDecimal predictedDailySales,
            int predictedTotalQty,
            int shortageQty,
            int suggestedQty,
            int finalQty,
            String recoSource,
            BigDecimal confidence,
            String reason
    ) {
        String sql = """
                INSERT INTO wms_replenishment_item
                (plan_id, sku_id, current_qty, safe_qty, predicted_daily_sales, predicted_total_qty,
                 shortage_qty, suggested_qty, final_qty, reco_source, confidence, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql,
                planId, skuId, currentQty, safeQty, predictedDailySales, predictedTotalQty,
                shortageQty, suggestedQty, finalQty, recoSource, confidence, reason
        );
    }

    /**
     * 删除计划明细（用于重算）。
     */
    public int deleteItemsByPlanId(Long planId) {
        String sql = "DELETE FROM wms_replenishment_item WHERE plan_id = ?";
        return jdbcTemplate.update(sql, planId);
    }

    /**
     * 按预期状态更新计划状态（并发安全）。
     */
    public int updatePlanStatus(Long id, int targetStatus, int expectedStatus) {
        String sql = """
                UPDATE wms_replenishment_plan
                SET status = ?
                WHERE id = ?
                  AND status = ?
                """;
        return jdbcTemplate.update(sql, targetStatus, id, expectedStatus);
    }

    /**
     * 更新计划重算参数（仅待确认状态允许），用于“调参后重算”。
     */
    public int updatePlanParamsInDraft(Long id, int calcDays, int leadTimeDays, int safetyDays, String remark) {
        String sql = """
                UPDATE wms_replenishment_plan
                SET calc_days = ?,
                    lead_time_days = ?,
                    safety_days = ?,
                    remark = ?
                WHERE id = ?
                  AND status = 0
                """;
        return jdbcTemplate.update(sql, calcDays, leadTimeDays, safetyDays, remark, id);
    }

    /**
     * 按预期状态更新计划状态并写入采购草稿号（并发安全）。
     */
    public int updatePlanStatusAndPurchaseDraftNo(Long id, int targetStatus, String purchaseDraftNo, int expectedStatus) {
        String sql = """
                UPDATE wms_replenishment_plan
                SET status = ?, purchase_draft_no = ?
                WHERE id = ?
                  AND status = ?
                  AND purchase_draft_no IS NULL
                """;
        return jdbcTemplate.update(sql, targetStatus, purchaseDraftNo, id, expectedStatus);
    }

    /**
     * 更新建议明细最终量（仅草稿状态有效）。
     */
    public int updateItemFinalQtyInDraft(Long planId, Long itemId, Integer finalQty) {
        String sql = """
                UPDATE wms_replenishment_item i
                JOIN wms_replenishment_plan p ON p.id = i.plan_id
                SET i.final_qty = ?
                WHERE i.id = ?
                  AND i.plan_id = ?
                  AND p.status = 0
                """;
        return jdbcTemplate.update(sql, finalQty, itemId, planId);
    }

    /**
     * 判断指定计划下明细是否存在。
     */
    public boolean existsItemByPlanAndId(Long planId, Long itemId) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_replenishment_item
                WHERE plan_id = ?
                  AND id = ?
                """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, planId, itemId);
        return count != null && count > 0;
    }

    /**
     * 查询日流水最大计划号（用于生成下一个计划号）。
     */
    public Optional<String> findMaxPlanNoByPrefix(String prefix) {
        String sql = """
                SELECT plan_no
                FROM wms_replenishment_plan
                WHERE plan_no LIKE CONCAT(?, '%')
                ORDER BY plan_no DESC
                LIMIT 1
                """;
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("plan_no"), prefix);
        return list.stream().findFirst();
    }

    /**
     * 查询日流水最大采购草稿号（用于生成下一个草稿号）。
     */
    public Optional<String> findMaxPurchaseDraftNoByPrefix(String prefix) {
        String sql = """
                SELECT purchase_draft_no
                FROM wms_replenishment_plan
                WHERE purchase_draft_no IS NOT NULL
                  AND purchase_draft_no LIKE CONCAT(?, '%')
                ORDER BY purchase_draft_no DESC
                LIMIT 1
                """;
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("purchase_draft_no"), prefix);
        return list.stream().findFirst();
    }

    /**
     * 按计划号回查主键。
     */
    public Optional<Long> findPlanIdByPlanNo(String planNo) {
        String sql = "SELECT id FROM wms_replenishment_plan WHERE plan_no = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), planNo);
        return list.stream().findFirst();
    }

    /**
     * 读取指标时间段内的基础统计量（用于计算采纳率/干预率/缺口命中率）。
     */
    public MetricsOverviewRaw queryMetricsOverview(Long warehouseId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        long startedAt = System.nanoTime();
        String sql = """
                SELECT COALESCE(COUNT(DISTINCT p.id), 0) AS plan_count,
                       COALESCE(COUNT(i.id), 0) AS item_count,
                       COALESCE(SUM(i.final_qty), 0) AS sum_final_qty,
                       COALESCE(SUM(i.suggested_qty), 0) AS sum_suggested_qty,
                       COALESCE(SUM(CASE WHEN i.final_qty <> i.suggested_qty THEN 1 ELSE 0 END), 0) AS adjust_item_count,
                       COALESCE(SUM(CASE WHEN i.shortage_qty > 0 AND i.final_qty > 0 THEN 1 ELSE 0 END), 0) AS shortage_hit_count,
                       COALESCE(SUM(CASE WHEN i.shortage_qty > 0 THEN 1 ELSE 0 END), 0) AS shortage_total_count
                FROM wms_replenishment_plan p
                JOIN wms_replenishment_item i ON i.plan_id = p.id
                WHERE p.status IN (1, 2)
                  AND (? IS NULL OR p.warehouse_id = ?)
                  AND p.generated_at >= ?
                  AND p.generated_at < ?
                """;
        MetricsOverviewRaw raw = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return new MetricsOverviewRaw(0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0L);
            }
            return new MetricsOverviewRaw(
                    rs.getLong("plan_count"),
                    rs.getLong("item_count"),
                    rs.getBigDecimal("sum_final_qty"),
                    rs.getBigDecimal("sum_suggested_qty"),
                    rs.getLong("adjust_item_count"),
                    rs.getLong("shortage_hit_count"),
                    rs.getLong("shortage_total_count")
            );
        }, warehouseId, warehouseId, startInclusive, endExclusive);
        logSlowQuery("queryMetricsOverview", startedAt, "warehouseId=" + warehouseId + ", start=" + startInclusive + ", end=" + endExclusive);
        return raw;
    }

    /**
     * 读取统计期内预测误差MAPE（近似口径：计划明细预测日均 vs 销量日聚合真实日均）。
     */
    public BigDecimal queryMetricsMape(Long warehouseId, LocalDate startDate, LocalDate endDate) {
        long startedAt = System.nanoTime();
        String sql = """
                SELECT ROUND(
                         AVG(
                           CASE
                             WHEN actual.actual_daily <= 0 THEN NULL
                             ELSE ABS(actual.actual_daily - pred.pred_daily) / actual.actual_daily
                           END
                         ),
                       4) AS mape_value
                FROM (
                    SELECT p.warehouse_id,
                           i.sku_id,
                           AVG(i.predicted_daily_sales) AS pred_daily
                    FROM wms_replenishment_plan p
                    JOIN wms_replenishment_item i ON i.plan_id = p.id
                    WHERE p.status IN (1, 2)
                      AND (? IS NULL OR p.warehouse_id = ?)
                      AND p.generated_at >= ?
                      AND p.generated_at < ?
                    GROUP BY p.warehouse_id, i.sku_id
                ) pred
                JOIN (
                    SELECT warehouse_id,
                           sku_id,
                           AVG(outbound_qty) AS actual_daily
                    FROM wms_sales_daily
                    WHERE stat_date >= ?
                      AND stat_date <= ?
                      AND (? IS NULL OR warehouse_id = ?)
                    GROUP BY warehouse_id, sku_id
                ) actual
                  ON actual.warehouse_id = pred.warehouse_id
                 AND actual.sku_id = pred.sku_id
                """;
        BigDecimal mape = jdbcTemplate.queryForObject(
                sql,
                BigDecimal.class,
                warehouseId, warehouseId, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(),
                startDate, endDate, warehouseId, warehouseId
        );
        logSlowQuery("queryMetricsMape", startedAt, "warehouseId=" + warehouseId + ", start=" + startDate + ", end=" + endDate);
        return mape;
    }

    /**
     * 读取统计期销量与当前均库存，用于计算库存周转率。
     */
    public MetricsTurnoverRaw queryMetricsTurnover(Long warehouseId, LocalDate startDate, LocalDate endDate) {
        long startedAt = System.nanoTime();
        String sql = """
                SELECT COALESCE((
                           SELECT SUM(outbound_qty)
                           FROM wms_sales_daily
                           WHERE stat_date >= ?
                             AND stat_date <= ?
                             AND (? IS NULL OR warehouse_id = ?)
                       ), 0) AS total_outbound_qty,
                       COALESCE((
                           SELECT AVG(on_hand_qty)
                           FROM wms_inventory_stock
                           WHERE (? IS NULL OR warehouse_id = ?)
                       ), 0) AS avg_on_hand_qty
                """;
        MetricsTurnoverRaw raw = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return new MetricsTurnoverRaw(BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return new MetricsTurnoverRaw(
                    rs.getBigDecimal("total_outbound_qty"),
                    rs.getBigDecimal("avg_on_hand_qty")
            );
        }, startDate, endDate, warehouseId, warehouseId, warehouseId, warehouseId);
        logSlowQuery("queryMetricsTurnover", startedAt, "warehouseId=" + warehouseId + ", start=" + startDate + ", end=" + endDate);
        return raw;
    }

    /**
     * 查询人工干预Top SKU，便于定位规则偏差集中点。
     */
    public List<MetricsTopAdjustSkuRaw> listTopAdjustSkus(
            Long warehouseId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            int limit
    ) {
        long startedAt = System.nanoTime();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String sql = """
                SELECT i.sku_id,
                       s.sku_code,
                       s.sku_name,
                       SUM(ABS(i.final_qty - i.suggested_qty)) AS adjust_abs_qty_total,
                       SUM(CASE WHEN i.final_qty <> i.suggested_qty THEN 1 ELSE 0 END) AS adjust_item_count
                FROM wms_replenishment_plan p
                JOIN wms_replenishment_item i ON i.plan_id = p.id
                JOIN wms_sku s ON s.id = i.sku_id
                WHERE p.status IN (1, 2)
                  AND (? IS NULL OR p.warehouse_id = ?)
                  AND p.generated_at >= ?
                  AND p.generated_at < ?
                GROUP BY i.sku_id, s.sku_code, s.sku_name
                HAVING SUM(CASE WHEN i.final_qty <> i.suggested_qty THEN 1 ELSE 0 END) > 0
                ORDER BY adjust_abs_qty_total DESC, adjust_item_count DESC, i.sku_id ASC
                LIMIT ?
                """;
        List<MetricsTopAdjustSkuRaw> rows = jdbcTemplate.query(
                sql,
                topAdjustSkuRowMapper,
                warehouseId, warehouseId, startInclusive, endExclusive, safeLimit
        );
        logSlowQuery("listTopAdjustSkus", startedAt, "warehouseId=" + warehouseId + ", start=" + startInclusive + ", end=" + endExclusive + ", limit=" + safeLimit);
        return rows;
    }

    /**
     * 按仓库ID回查基础信息（用于指标响应展示）。
     */
    public Optional<WarehouseSimple> findWarehouseSimpleById(Long warehouseId) {
        String sql = """
                SELECT id, warehouse_code, warehouse_name
                FROM wms_warehouse
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new WarehouseSimple(
                rs.getLong("id"),
                rs.getString("warehouse_code"),
                rs.getString("warehouse_name")
        ), warehouseId).stream().findFirst();
    }

    /**
     * 慢查询日志：超过阈值时输出告警，便于后续慢SQL分析。
     */
    private void logSlowQuery(String method, long startedAtNanos, String context) {
        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        if (elapsedMillis >= SLOW_QUERY_THRESHOLD_MILLIS) {
            log.warn("E3-慢查询告警：method={}, costMs={}, context={}", method, elapsedMillis, context);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * 建议计算输入模型。
     */
    public record CalcInput(
            Long skuId,
            String skuCode,
            String skuName,
            Integer currentQty,
            Integer safeQty,
            BigDecimal predictedDailySales,
            String relatedSkuCode,
            BigDecimal confidence
    ) {
    }

    /**
     * 指标概览原始值：用于服务层统一算比率并保留口径一致性。
     */
    public record MetricsOverviewRaw(
            Long planCount,
            Long itemCount,
            BigDecimal sumFinalQty,
            BigDecimal sumSuggestedQty,
            Long adjustItemCount,
            Long shortageHitCount,
            Long shortageTotalCount
    ) {
    }

    /**
     * 人工干预Top SKU原始行。
     */
    public record MetricsTopAdjustSkuRaw(
            Long skuId,
            String skuCode,
            String skuName,
            Integer adjustAbsQtyTotal,
            Integer adjustItemCount
    ) {
    }

    /**
     * 周转率计算输入：统计期总出库与当前均库存。
     */
    public record MetricsTurnoverRaw(
            BigDecimal totalOutboundQty,
            BigDecimal avgOnHandQty
    ) {
    }

    /**
     * 仓库简要信息模型。
     */
    public record WarehouseSimple(
            Long id,
            String warehouseCode,
            String warehouseName
    ) {
    }
}
