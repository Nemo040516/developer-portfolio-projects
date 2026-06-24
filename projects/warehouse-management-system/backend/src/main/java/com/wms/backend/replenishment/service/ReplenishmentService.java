/**
 * @file 速览索引
 * @summary 补货业务服务，负责补货建议生成、E1统计指标、重算、确认、最终数量调整与采购草稿转换。
 * @core 1. 生成补货计划与建议明细
 * @core 2. 提供E1统计指标聚合与Top SKU
 * @core 3. 支持补货计划重算
 * @core 4. 支持确认建议与调整最终数量
 * @core 5. 支持转采购草稿
 * @entry 先看：metrics、calculate、recalculate、confirm、updateFinalQty、toPurchaseDraft
 * @deps 关键依赖：补货仓储、库存数据、SKU/仓库/供应商数据
 * @risk 高风险修改点：统计口径、推荐口径、状态流转、采购草稿转换、首页快捷筛选关联
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java、前端/src/components/ReplenishmentPanel.vue
 */
package com.wms.backend.replenishment.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.replenishment.dto.ReplenishmentCalculateRequest;
import com.wms.backend.replenishment.dto.ReplenishmentDetailResponse;
import com.wms.backend.replenishment.dto.ReplenishmentItemResponse;
import com.wms.backend.replenishment.dto.ReplenishmentMetricsOverviewResponse;
import com.wms.backend.replenishment.dto.ReplenishmentMetricsResponse;
import com.wms.backend.replenishment.dto.ReplenishmentPlanResponse;
import com.wms.backend.replenishment.dto.ReplenishmentRecalculateRequest;
import com.wms.backend.replenishment.dto.ReplenishmentTopAdjustSkuResponse;
import com.wms.backend.replenishment.repository.ReplenishmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * M6 业务服务：
 * 1) 提供补货建议分页、详情、生成、重算、确认、转采购草稿；
 * 2) 保证建议数量“可解释、可复算”。
 */
@Service
public class ReplenishmentService {

    private static final Logger log = LoggerFactory.getLogger(ReplenishmentService.class);

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_CONFIRMED = 1;
    private static final int STATUS_PURCHASED = 2;

    private static final int DEFAULT_CALC_DAYS = 15;
    private static final int DEFAULT_LEAD_TIME_DAYS = 3;
    private static final int DEFAULT_SAFETY_DAYS = 2;

    private static final int MIN_CALC_DAYS = 1;
    private static final int MAX_CALC_DAYS = 90;
    private static final int MIN_LEAD_DAYS = 0;
    private static final int MAX_LEAD_DAYS = 60;
    private static final int MIN_SAFETY_DAYS = 0;
    private static final int MAX_SAFETY_DAYS = 30;
    private static final int METRICS_DEFAULT_DAYS = 30;
    private static final int METRICS_MAX_DAYS = 180;
    private static final int METRICS_TOP_LIMIT = 5;
    private static final long METRICS_CACHE_TTL_MILLIS = 60_000L;

    private static final String PLAN_NO_PREFIX = "rp";
    private static final String PURCHASE_DRAFT_PREFIX = "pd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReplenishmentRepository replenishmentRepository;
    private final ConcurrentMap<String, CacheEntry<ReplenishmentMetricsResponse>> metricsCache = new ConcurrentHashMap<>();

    public ReplenishmentService(ReplenishmentRepository replenishmentRepository) {
        this.replenishmentRepository = replenishmentRepository;
    }

    /**
     * 分页查询建议主表。
     */
    public PageResult<ReplenishmentPlanResponse> page(
            String keyword,
            Integer status,
            LocalDate generatedDateStart,
            LocalDate generatedDateEnd,
            int pageNo,
            int pageSize
    ) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Integer safeStatus = normalizeStatusForPage(status);
        DateRange generatedDateRange = normalizeGeneratedDateRange(generatedDateStart, generatedDateEnd);
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = replenishmentRepository.countPlansByKeyword(
                safeKeyword,
                safeStatus,
                generatedDateRange.startInclusive(),
                generatedDateRange.endExclusive()
        );
        List<ReplenishmentPlanResponse> records = replenishmentRepository.pagePlansByKeyword(
                safeKeyword,
                safeStatus,
                generatedDateRange.startInclusive(),
                generatedDateRange.endExclusive(),
                offset,
                safePageSize
        );
        return new PageResult<>(total, records);
    }

    /**
     * 查询建议详情（主表 + 明细）。
     */
    public ReplenishmentDetailResponse detail(Long id) {
        ReplenishmentPlanResponse plan = findPlanOrThrow(id);
        List<ReplenishmentItemResponse> items = replenishmentRepository.listItemsByPlanId(id);
        return new ReplenishmentDetailResponse(
                plan.id(),
                plan.planNo(),
                plan.warehouseId(),
                plan.warehouseCode(),
                plan.warehouseName(),
                plan.status(),
                plan.calcDays(),
                plan.leadTimeDays(),
                plan.safetyDays(),
                plan.purchaseDraftNo(),
                plan.remark(),
                plan.createdBy(),
                plan.generatedAt(),
                plan.createdAt(),
                plan.updatedAt(),
                items
        );
    }

    /**
     * E1 统计概览：
     * 1) 输出采纳率/干预率/缺口命中率/MAPE/周转率；
     * 2) 输出人工干预Top SKU；
     * 3) 按（仓库+时间范围）做短TTL缓存，降低重复查询压力。
     */
    public ReplenishmentMetricsResponse metrics(Long warehouseId, LocalDate startDate, LocalDate endDate) {
        MetricsDateRange range = normalizeMetricsDateRange(startDate, endDate);
        if (warehouseId != null) {
            validateWarehouse(warehouseId);
        }
        String cacheKey = "metrics:" + (warehouseId == null ? "ALL" : warehouseId) + ":" + range.startDate() + ":" + range.endDate();
        ReplenishmentMetricsResponse cached = readMetricsCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        long startedAt = System.nanoTime();
        try {
            ReplenishmentRepository.MetricsOverviewRaw raw = replenishmentRepository.queryMetricsOverview(
                    warehouseId,
                    range.startDate().atStartOfDay(),
                    range.endDate().plusDays(1).atStartOfDay()
            );
            BigDecimal adoptionRate = safeRatio(raw.sumFinalQty(), raw.sumSuggestedQty(), 4);
            BigDecimal manualAdjustRate = safeRatio(BigDecimal.valueOf(raw.adjustItemCount()), BigDecimal.valueOf(raw.itemCount()), 4);
            BigDecimal shortageHitRate = safeRatio(BigDecimal.valueOf(raw.shortageHitCount()), BigDecimal.valueOf(raw.shortageTotalCount()), 4);
            BigDecimal mape = normalizeDecimalOrNull(replenishmentRepository.queryMetricsMape(warehouseId, range.startDate(), range.endDate()));
            ReplenishmentRepository.MetricsTurnoverRaw turnoverRaw = replenishmentRepository.queryMetricsTurnover(warehouseId, range.startDate(), range.endDate());
            BigDecimal inventoryTurnoverRate = safeRatio(turnoverRaw.totalOutboundQty(), turnoverRaw.avgOnHandQty(), 4);

            ReplenishmentMetricsOverviewResponse overview = new ReplenishmentMetricsOverviewResponse(
                    raw.planCount(),
                    raw.itemCount(),
                    adoptionRate,
                    manualAdjustRate,
                    shortageHitRate,
                    mape,
                    inventoryTurnoverRate
            );
            List<ReplenishmentTopAdjustSkuResponse> topAdjustSkus = replenishmentRepository.listTopAdjustSkus(
                    warehouseId,
                    range.startDate().atStartOfDay(),
                    range.endDate().plusDays(1).atStartOfDay(),
                    METRICS_TOP_LIMIT
            ).stream().map(row -> new ReplenishmentTopAdjustSkuResponse(
                    row.skuId(),
                    row.skuCode(),
                    row.skuName(),
                    row.adjustAbsQtyTotal(),
                    row.adjustItemCount()
            )).toList();

            String warehouseCode = null;
            String warehouseName = warehouseId == null ? "全部仓库" : null;
            if (warehouseId != null) {
                ReplenishmentRepository.WarehouseSimple warehouse = replenishmentRepository.findWarehouseSimpleById(warehouseId)
                        .orElseThrow(() -> new BusinessException(4601, "仓库不存在或已停用"));
                warehouseCode = warehouse.warehouseCode();
                warehouseName = warehouse.warehouseName();
            }

            ReplenishmentMetricsResponse response = new ReplenishmentMetricsResponse(
                    range.startDate(),
                    range.endDate(),
                    warehouseId,
                    warehouseCode,
                    warehouseName,
                    overview,
                    topAdjustSkus
            );
            writeMetricsCache(cacheKey, response);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            if (elapsedMillis >= 800) {
                log.warn("E3-统计接口慢请求：warehouseId={}, startDate={}, endDate={}, costMs={}",
                        warehouseId, range.startDate(), range.endDate(), elapsedMillis);
            }
            return response;
        } catch (RuntimeException ex) {
            log.error("E3-统计接口异常告警：warehouseId={}, startDate={}, endDate={}, message={}",
                    warehouseId, range.startDate(), range.endDate(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * 生成新的补货建议计划（待确认）。
     */
    @Transactional
    public ReplenishmentDetailResponse calculate(ReplenishmentCalculateRequest request, String username) {
        log.info("M6-生成建议开始：operator={}, warehouseId={}", username, request.warehouseId());
        int calcDays = normalizeRange(request.calcDays(), DEFAULT_CALC_DAYS, MIN_CALC_DAYS, MAX_CALC_DAYS, "计算窗口天数");
        int leadTimeDays = normalizeRange(request.leadTimeDays(), DEFAULT_LEAD_TIME_DAYS, MIN_LEAD_DAYS, MAX_LEAD_DAYS, "交期天数");
        int safetyDays = normalizeRange(request.safetyDays(), DEFAULT_SAFETY_DAYS, MIN_SAFETY_DAYS, MAX_SAFETY_DAYS, "安全覆盖天数");
        validateWarehouse(request.warehouseId());

        String planNo = generatePlanNo();
        Long createdBy = replenishmentRepository.findUserIdByUsername(username).orElse(null);
        int affected = replenishmentRepository.insertPlan(
                planNo,
                request.warehouseId(),
                calcDays,
                leadTimeDays,
                safetyDays,
                request.remark(),
                createdBy
        );
        if (affected != 1) {
            throw new BusinessException(4603, "补货建议生成失败");
        }
        Long planId = replenishmentRepository.findPlanIdByPlanNo(planNo)
                .orElseThrow(() -> new BusinessException(4604, "补货建议生成后读取失败"));
        rebuildItems(planId, request.warehouseId(), calcDays, leadTimeDays, safetyDays);
        evictMetricsCache();
        ReplenishmentDetailResponse detail = detail(planId);
        int itemSize = detail.items() == null ? 0 : detail.items().size();
        log.info("M6-生成建议成功：operator={}, planId={}, planNo={}, itemCount={}", username, planId, detail.planNo(), itemSize);
        return detail;
    }

    /**
     * 重算建议（仅待确认状态允许）：
     * 1) 支持可选调参（计算天数/交期天数/安全天数/备注）；
     * 2) 调参后先更新主表，再重建明细；
     * 3) 不传参数时沿用原计划值，实现“原参数重算”兼容。
     */
    @Transactional
    public ReplenishmentDetailResponse recalculate(Long id, ReplenishmentRecalculateRequest request, String operator) {
        log.info("M6-重算建议开始：operator={}, planId={}", operator, id);
        ReplenishmentPlanResponse plan = findPlanOrThrow(id);
        if (plan.status() != STATUS_DRAFT) {
            throw new BusinessException(4607, "仅待确认状态可重算");
        }

        int calcDays = resolveRecalculateRange(
                request == null ? null : request.calcDays(),
                plan.calcDays(),
                DEFAULT_CALC_DAYS,
                MIN_CALC_DAYS,
                MAX_CALC_DAYS,
                "计算窗口天数"
        );
        int leadTimeDays = resolveRecalculateRange(
                request == null ? null : request.leadTimeDays(),
                plan.leadTimeDays(),
                DEFAULT_LEAD_TIME_DAYS,
                MIN_LEAD_DAYS,
                MAX_LEAD_DAYS,
                "交期天数"
        );
        int safetyDays = resolveRecalculateRange(
                request == null ? null : request.safetyDays(),
                plan.safetyDays(),
                DEFAULT_SAFETY_DAYS,
                MIN_SAFETY_DAYS,
                MAX_SAFETY_DAYS,
                "安全覆盖天数"
        );
        String remark = resolveRecalculateRemark(request, plan.remark());

        int affected = replenishmentRepository.updatePlanParamsInDraft(id, calcDays, leadTimeDays, safetyDays, remark);
        if (affected != 1) {
            // 并发下若状态已变化，统一按状态机错误返回。
            throw new BusinessException(4607, "仅待确认状态可重算");
        }

        replenishmentRepository.deleteItemsByPlanId(id);
        rebuildItems(id, plan.warehouseId(), calcDays, leadTimeDays, safetyDays);
        evictMetricsCache();
        ReplenishmentDetailResponse detail = detail(id);
        int itemSize = detail.items() == null ? 0 : detail.items().size();
        log.info(
                "M6-重算建议成功：operator={}, planId={}, planNo={}, calcDays={}, leadTimeDays={}, safetyDays={}, itemCount={}",
                operator, id, detail.planNo(), calcDays, leadTimeDays, safetyDays, itemSize
        );
        return detail;
    }

    /**
     * 确认建议（待确认 -> 待转采购）。
     */
    @Transactional
    public ReplenishmentDetailResponse confirm(Long id, String operator) {
        log.info("M6-确认建议开始：operator={}, planId={}", operator, id);
        ReplenishmentPlanResponse plan = findPlanOrThrow(id);
        if (plan.status() != STATUS_DRAFT) {
            throw new BusinessException(4608, "仅待确认状态可确认");
        }
        int affected = replenishmentRepository.updatePlanStatus(id, STATUS_CONFIRMED, STATUS_DRAFT);
        if (affected != 1) {
            // 并发下若已被其他请求确认，统一按状态机错误返回 4608。
            throw new BusinessException(4608, "仅待确认状态可确认");
        }
        evictMetricsCache();
        ReplenishmentDetailResponse detail = detail(id);
        log.info("M6-确认建议成功：operator={}, planId={}, planNo={}, status={}", operator, id, detail.planNo(), detail.status());
        return detail;
    }

    /**
     * 调整建议明细最终量（仅待确认状态允许）。
     */
    @Transactional
    public ReplenishmentDetailResponse updateFinalQty(Long id, Long itemId, Integer finalQty, String operator) {
        log.info("M6-调整最终量开始：operator={}, planId={}, itemId={}, finalQty={}", operator, id, itemId, finalQty);
        ReplenishmentPlanResponse plan = findPlanOrThrow(id);
        if (plan.status() != STATUS_DRAFT) {
            throw new BusinessException(4612, "仅待确认状态可调整最终量");
        }
        int affected = replenishmentRepository.updateItemFinalQtyInDraft(id, itemId, finalQty);
        if (affected != 1) {
            if (!replenishmentRepository.existsItemByPlanAndId(id, itemId)) {
                throw new BusinessException(4613, "补货建议明细不存在");
            }
            // 明细存在但更新失败，说明并发下状态已变更为非待确认。
            throw new BusinessException(4612, "仅待确认状态可调整最终量");
        }
        evictMetricsCache();
        ReplenishmentDetailResponse detail = detail(id);
        log.info("M6-调整最终量成功：operator={}, planId={}, itemId={}, finalQty={}", operator, id, itemId, finalQty);
        return detail;
    }

    /**
     * 转采购草稿（待转采购 -> 已生成采购草稿）。
     * 说明：V1 仅在建议计划上落采购草稿号，不新建采购业务表。
     */
    @Transactional
    public ReplenishmentDetailResponse toPurchaseDraft(Long id, String operator) {
        log.info("M6-转采购草稿开始：operator={}, planId={}", operator, id);
        ReplenishmentPlanResponse plan = findPlanOrThrow(id);
        if (plan.status() != STATUS_CONFIRMED) {
            throw new BusinessException(4610, "仅待转采购状态可生成采购草稿");
        }
        String purchaseDraftNo = generatePurchaseDraftNo();
        int affected = replenishmentRepository.updatePlanStatusAndPurchaseDraftNo(id, STATUS_PURCHASED, purchaseDraftNo, STATUS_CONFIRMED);
        if (affected != 1) {
            // 并发下若已被其他请求转草稿，统一按状态机错误返回 4610。
            throw new BusinessException(4610, "仅待转采购状态可生成采购草稿");
        }
        evictMetricsCache();
        ReplenishmentDetailResponse detail = detail(id);
        log.info(
                "M6-转采购草稿成功：operator={}, planId={}, planNo={}, purchaseDraftNo={}",
                operator, id, detail.planNo(), detail.purchaseDraftNo()
        );
        return detail;
    }

    /**
     * 按当前库存、历史销量、安全库存、推荐关联重建建议明细。
     */
    private void rebuildItems(Long planId, Long warehouseId, int calcDays, int leadTimeDays, int safetyDays) {
        LocalDate startDate = LocalDate.now().minusDays(Math.max(calcDays - 1L, 0L));
        List<ReplenishmentRepository.CalcInput> inputs = replenishmentRepository.listCalcInputs(warehouseId, startDate);
        if (inputs.isEmpty()) {
            throw new BusinessException(4602, "当前仓库无可计算SKU");
        }

        int coverDays = Math.max(0, leadTimeDays + safetyDays);
        for (ReplenishmentRepository.CalcInput input : inputs) {
            int currentQty = Math.max(0, nullToZero(input.currentQty()));
            int safeQty = Math.max(0, nullToZero(input.safeQty()));
            BigDecimal predictedDaily = normalizeDecimal(input.predictedDailySales());
            int predictedTotal = ceilToInt(predictedDaily.multiply(BigDecimal.valueOf(coverDays)));
            int shortageQty = Math.max(0, safeQty + predictedTotal - currentQty);
            int suggestedQty = shortageQty;
            int finalQty = suggestedQty;

            String recoSource = input.relatedSkuCode() == null ? null : ("关联SKU:" + input.relatedSkuCode());
            BigDecimal confidence = input.confidence();
            String reason = shortageQty > 0
                    ? "预测覆盖不足，建议补货"
                    : "库存覆盖充足，建议观察";

            replenishmentRepository.insertItem(
                    planId,
                    input.skuId(),
                    currentQty,
                    safeQty,
                    predictedDaily,
                    predictedTotal,
                    shortageQty,
                    suggestedQty,
                    finalQty,
                    recoSource,
                    confidence,
                    reason
            );
        }
    }

    /**
     * 统一校验仓库可用性。
     */
    private void validateWarehouse(Long warehouseId) {
        if (warehouseId == null || !replenishmentRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4601, "仓库不存在或已停用");
        }
    }

    /**
     * 统一主表读取，不存在直接抛业务异常。
     */
    private ReplenishmentPlanResponse findPlanOrThrow(Long id) {
        return replenishmentRepository.findPlanById(id)
                .orElseThrow(() -> new BusinessException(4605, "补货建议不存在"));
    }

    /**
     * 分页状态入参校验：仅允许 0/1/2 或空。
     */
    private Integer normalizeStatusForPage(Integer status) {
        if (status == null) {
            return null;
        }
        if (status < STATUS_DRAFT || status > STATUS_PURCHASED) {
            throw new BusinessException(4001, "状态仅支持 0/1/2");
        }
        return status;
    }

    /**
     * 分页日期入参标准化：
     * 1) 同时传入时要求开始 <= 结束；
     * 2) 转为 [startInclusive, endExclusive) 供 SQL 范围查询。
     */
    private DateRange normalizeGeneratedDateRange(LocalDate generatedDateStart, LocalDate generatedDateEnd) {
        if (generatedDateStart != null && generatedDateEnd != null && generatedDateStart.isAfter(generatedDateEnd)) {
            throw new BusinessException(4001, "生成日期范围不合法：开始日期不能晚于结束日期");
        }
        LocalDateTime startInclusive = generatedDateStart == null ? null : generatedDateStart.atStartOfDay();
        LocalDateTime endExclusive = generatedDateEnd == null ? null : generatedDateEnd.plusDays(1).atStartOfDay();
        return new DateRange(startInclusive, endExclusive);
    }

    /**
     * 数值区间标准化：为空回默认，超出区间即报错。
     */
    private int normalizeRange(Integer value, int defaultValue, int min, int max, String fieldName) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < min || resolved > max) {
            throw new BusinessException(4001, fieldName + "超出允许范围");
        }
        return resolved;
    }

    /**
     * 重算参数解析：
     * 1) 先用请求值；
     * 2) 请求为空则回退到计划当前值；
     * 3) 计划值为空再回退到系统默认值；
     * 4) 最终统一走区间校验。
     */
    private int resolveRecalculateRange(
            Integer requestValue,
            Integer currentPlanValue,
            int defaultValue,
            int min,
            int max,
            String fieldName
    ) {
        Integer fallback = currentPlanValue == null ? defaultValue : currentPlanValue;
        Integer candidate = requestValue == null ? fallback : requestValue;
        return normalizeRange(candidate, defaultValue, min, max, fieldName);
    }

    /**
     * 重算备注解析：
     * 1) 不传 remark：沿用原备注；
     * 2) 传空字符串：清空备注（落 null）；
     * 3) 传非空：写入去首尾空格后的值。
     */
    private String resolveRecalculateRemark(ReplenishmentRecalculateRequest request, String currentRemark) {
        if (request == null || request.remark() == null) {
            return currentRemark;
        }
        String trimmed = request.remark().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 生成补货建议号：rp + yyyyMMdd + 4位流水。
     */
    private String generatePlanNo() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String prefix = PLAN_NO_PREFIX + datePart;
        String currentMax = replenishmentRepository.findMaxPlanNoByPrefix(prefix).orElse(null);
        int nextSeq = nextSeqFromNo(currentMax, prefix);
        return prefix + String.format("%04d", nextSeq);
    }

    /**
     * 生成采购草稿号：pd + yyyyMMdd + 4位流水。
     */
    private String generatePurchaseDraftNo() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String prefix = PURCHASE_DRAFT_PREFIX + datePart;
        String currentMax = replenishmentRepository.findMaxPurchaseDraftNoByPrefix(prefix).orElse(null);
        int nextSeq = nextSeqFromNo(currentMax, prefix);
        return prefix + String.format("%04d", nextSeq);
    }

    /**
     * 从最大单号中解析下一流水序号。
     */
    private int nextSeqFromNo(String currentMax, String prefix) {
        int nextSeq = 1;
        if (currentMax != null && currentMax.length() >= prefix.length() + 4) {
            String suffix = currentMax.substring(currentMax.length() - 4);
            if (suffix.chars().allMatch(Character::isDigit)) {
                nextSeq = Integer.parseInt(suffix) + 1;
            }
        }
        return nextSeq;
    }

    /**
     * 小工具：空值转 0。
     */
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 小工具：负数预测值兜底归零。
     */
    private BigDecimal normalizeDecimal(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    /**
     * 小工具：按向上取整转整数，避免低估需求。
     */
    private int ceilToInt(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return value.setScale(0, RoundingMode.CEILING).intValue();
    }

    /**
     * 统计日期范围标准化：
     * 1) 默认最近30天；
     * 2) 只传一个边界时自动补齐另一个边界；
     * 3) 限制区间跨度，避免超大范围压垮查询。
     */
    private MetricsDateRange normalizeMetricsDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate safeEnd = endDate == null ? today : endDate;
        LocalDate safeStart = startDate == null ? safeEnd.minusDays(METRICS_DEFAULT_DAYS - 1L) : startDate;
        if (safeStart.isAfter(safeEnd)) {
            throw new BusinessException(4001, "统计日期范围不合法：开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(safeStart, safeEnd) + 1;
        if (days > METRICS_MAX_DAYS) {
            throw new BusinessException(4001, "统计日期范围过大，最大支持180天");
        }
        return new MetricsDateRange(safeStart, safeEnd);
    }

    /**
     * 安全比率计算：分母为0时返回0，统一保留固定小数位。
     */
    private BigDecimal safeRatio(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    /**
     * 指标空值归一：null 时回0，避免前端额外空值判断。
     */
    private BigDecimal normalizeDecimalOrNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 读取统计缓存：命中且未过期返回结果，否则淘汰并返回null。
     */
    private ReplenishmentMetricsResponse readMetricsCache(String cacheKey) {
        CacheEntry<ReplenishmentMetricsResponse> entry = metricsCache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAtMillis()) {
            metricsCache.remove(cacheKey);
            return null;
        }
        return entry.data();
    }

    /**
     * 写入统计缓存（短TTL），用于削峰与提升页面二次刷新响应。
     */
    private void writeMetricsCache(String cacheKey, ReplenishmentMetricsResponse data) {
        metricsCache.put(cacheKey, new CacheEntry<>(data, System.currentTimeMillis() + METRICS_CACHE_TTL_MILLIS));
    }

    /**
     * 数据变更后清空统计缓存，保证指标读取到最新业务结果。
     */
    private void evictMetricsCache() {
        metricsCache.clear();
    }

    /**
     * 分页日期范围对象：
     * startInclusive: 过滤开始时间（含）；
     * endExclusive: 过滤结束时间（不含）。
     */
    private record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
    }

    /**
     * 统计日期范围对象：按自然日闭区间表达。
     */
    private record MetricsDateRange(LocalDate startDate, LocalDate endDate) {
    }

    /**
     * 简单缓存条目：数据与过期时间戳。
     */
    private record CacheEntry<T>(T data, long expireAtMillis) {
    }
}
