/**
 * @file 速览索引
 * @summary M6 补货建议集成测试，覆盖权限矩阵、状态机闭环、参数校验与并发一致性。
 * @core 1. 校验管理员/仓库员/采购员在补货建议读写接口上的权限边界
 * @core 2. 校验生成->重算->确认->转采购草稿全流程及重复操作拦截
 * @core 3. 校验非法仓库、非法筛选、非法最终量与明细不存在等错误码
 * @core 4. 校验并发调最终量、并发确认、并发转草稿的状态机一致性
 * @entry 先看：flowShouldSupportCalculateRecalculateConfirmAndToPurchaseDraft、permissionMatrixShouldMatchM6、shouldRejectMissingItemAndKeepStateMachineConsistentUnderConcurrency
 * @deps 关键依赖：TestRestTemplate、/api/replenishments*、/api/auth/login、ApiResponse
 * @state 关键数据：adminToken/warehouseToken/purchaserToken、status(0草稿/1已确认/2已转草稿)、错误码 4601/4608/4610/4612/4613
 * @risk 高风险修改点：状态机错误码变更、items 结构字段名变更、并发测试时序与超时参数
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java、后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java
 */
package com.wms.backend;

import com.wms.backend.auth.dto.LoginRequest;
import com.wms.backend.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M6 联调测试：
 * 1) 覆盖权限矩阵；
 * 2) 覆盖“生成 -> 重算 -> 确认 -> 转采购草稿”状态机闭环；
 * 3) 覆盖关键错误码（重复确认、重复转草稿、非法仓库、非法状态筛选、明细不存在）；
 * 4) 覆盖并发场景（并发调最终量、并发确认、并发转草稿）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M6ReplenishmentIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;
    private String warehouseToken;
    private String purchaserToken;

    @BeforeEach
    void setUp() {
        adminToken = login("admin", "12345");
        warehouseToken = login("warehouse", "12345");
        purchaserToken = login("purchaser", "12345");
    }

    @Test
    void permissionMatrixShouldMatchM6() {
        // 读权限：管理员/仓库员/采购员均可查看建议列表。
        assertStatus(adminToken, "/api/replenishments?pageNo=1&pageSize=5", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/replenishments?pageNo=1&pageSize=5", HttpStatus.OK);
        assertStatus(purchaserToken, "/api/replenishments?pageNo=1&pageSize=5", HttpStatus.OK);

        // 写权限：仓库员无权写操作；管理员/采购员可执行生成建议。
        Map<String, Object> payload = Map.of(
                "warehouseId", 1,
                "calcDays", 7,
                "leadTimeDays", 2,
                "safetyDays", 1,
                "remark", "M6自动化测试-权限矩阵"
        );
        assertPostStatus(warehouseToken, "/api/replenishments/calculate", payload, HttpStatus.FORBIDDEN);
        assertPostStatus(adminToken, "/api/replenishments/calculate", payload, HttpStatus.OK);
        assertPostStatus(purchaserToken, "/api/replenishments/calculate", payload, HttpStatus.OK);

        // 明细写权限：仓库员禁止，管理员/采购员可在草稿态调整最终量。
        ApiResponse<Map<String, Object>> draft = post(adminToken, "/api/replenishments/calculate", payload);
        assertEquals(0, draft.code());
        assertNotNull(draft.data());
        Long planId = longValue(draft.data().get("id"));
        Long itemId = firstItemId(draft.data());
        assertPutStatus(warehouseToken, "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty", Map.of("finalQty", 7), HttpStatus.FORBIDDEN);
        assertPutStatus(adminToken, "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty", Map.of("finalQty", 7), HttpStatus.OK);
        assertPutStatus(purchaserToken, "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty", Map.of("finalQty", 8), HttpStatus.OK);
    }

    @Test
    void metricsEndpointShouldBeReadableAndReturnCoreFields() {
        // 读权限：管理员/仓库员/采购员均可访问统计接口。
        assertStatus(adminToken, "/api/replenishments/metrics", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/replenishments/metrics", HttpStatus.OK);
        assertStatus(purchaserToken, "/api/replenishments/metrics", HttpStatus.OK);

        ApiResponse<Map<String, Object>> metrics = get(adminToken, "/api/replenishments/metrics?startDate=2026-02-01&endDate=2026-02-28");
        assertEquals(0, metrics.code());
        assertNotNull(metrics.data());

        Object rawOverview = metrics.data().get("overview");
        assertTrue(rawOverview instanceof Map, "metrics.overview 应存在");
        @SuppressWarnings("unchecked")
        Map<String, Object> overview = (Map<String, Object>) rawOverview;
        assertTrue(overview.containsKey("adoptionRate"), "overview.adoptionRate 应存在");
        assertTrue(overview.containsKey("manualAdjustRate"), "overview.manualAdjustRate 应存在");
        assertTrue(overview.containsKey("shortageHitRate"), "overview.shortageHitRate 应存在");
        assertTrue(overview.containsKey("mape"), "overview.mape 应存在");
        assertTrue(overview.containsKey("inventoryTurnoverRate"), "overview.inventoryTurnoverRate 应存在");

        Object rawTop = metrics.data().get("topAdjustSkus");
        assertTrue(rawTop instanceof List, "metrics.topAdjustSkus 应存在");
    }

    @Test
    void flowShouldSupportCalculateRecalculateConfirmAndToPurchaseDraft() {
        // 1) 生成建议：落草稿状态，且应有明细数据。
        Map<String, Object> calculatePayload = Map.of(
                "warehouseId", 1,
                "calcDays", 7,
                "leadTimeDays", 2,
                "safetyDays", 1,
                "remark", "M6自动化测试-闭环流程"
        );
        ApiResponse<Map<String, Object>> calculated = post(adminToken, "/api/replenishments/calculate", calculatePayload);
        assertEquals(0, calculated.code());
        assertNotNull(calculated.data());
        Long planId = longValue(calculated.data().get("id"));
        String planNo = String.valueOf(calculated.data().get("planNo"));
        assertTrue(planNo.startsWith("rp"), "补货建议计划号应使用 rp 前缀");
        assertEquals(0, intValue(calculated.data().get("status")));
        assertHasItems(calculated.data());

        // 2) 重算建议：支持调参后重算，且仍保持草稿状态。
        Map<String, Object> recalculatePayload = Map.of(
                "calcDays", 9,
                "leadTimeDays", 3,
                "safetyDays", 2,
                "remark", "M6自动化测试-重算调参"
        );
        ApiResponse<Map<String, Object>> recalculated = put(adminToken, "/api/replenishments/" + planId + "/recalculate", recalculatePayload);
        assertEquals(0, recalculated.code());
        assertNotNull(recalculated.data());
        assertEquals(0, intValue(recalculated.data().get("status")));
        assertEquals(9, intValue(recalculated.data().get("calcDays")));
        assertEquals(3, intValue(recalculated.data().get("leadTimeDays")));
        assertEquals(2, intValue(recalculated.data().get("safetyDays")));
        assertHasItems(recalculated.data());

        // 3) 草稿态可手工微调最终量（finalQty），并可在详情中回读。
        Long itemId = firstItemId(recalculated.data());
        ApiResponse<Map<String, Object>> adjusted = put(
                adminToken,
                "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty",
                Map.of("finalQty", 9)
        );
        assertEquals(0, adjusted.code());
        assertNotNull(adjusted.data());
        assertItemFinalQty(adjusted.data(), itemId, 9);

        // 4) 确认建议：状态应从草稿变为已确认。
        ApiResponse<Map<String, Object>> confirmed = put(adminToken, "/api/replenishments/" + planId + "/confirm", null);
        assertEquals(0, confirmed.code());
        assertNotNull(confirmed.data());
        assertEquals(1, intValue(confirmed.data().get("status")));

        // 5) 已确认状态不允许再调整最终量。
        ApiResponse<Void> adjustAfterConfirm = putVoid(
                adminToken,
                "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty",
                Map.of("finalQty", 10)
        );
        assertEquals(4612, adjustAfterConfirm.code(), "已确认状态调整最终量应被状态机拦截");

        // 6) 重复确认应被状态机拦截。
        ApiResponse<Void> duplicateConfirm = putVoid(adminToken, "/api/replenishments/" + planId + "/confirm", null);
        assertEquals(4608, duplicateConfirm.code(), "重复确认应返回 4608");

        // 7) 转采购草稿：状态应变更为已转草稿，并回填采购草稿号。
        ApiResponse<Map<String, Object>> purchased = post(adminToken, "/api/replenishments/" + planId + "/to-purchase-draft", null);
        assertEquals(0, purchased.code());
        assertNotNull(purchased.data());
        assertEquals(2, intValue(purchased.data().get("status")));
        String purchaseDraftNo = String.valueOf(purchased.data().get("purchaseDraftNo"));
        assertTrue(purchaseDraftNo.startsWith("pd"), "采购草稿号应使用 pd 前缀");

        // 8) 重复转草稿应被状态机拦截（仅已确认可转草稿）。
        ApiResponse<Void> duplicateToDraft = postVoid(adminToken, "/api/replenishments/" + planId + "/to-purchase-draft", null);
        assertEquals(4610, duplicateToDraft.code(), "重复转草稿应返回 4610");

        // 9) 列表回查：应可按计划号检索到状态=2 且草稿号一致的记录。
        Map<String, Object> pageData = getPage(adminToken, "/api/replenishments?keyword=" + planNo + "&pageNo=1&pageSize=5");
        List<Map<String, Object>> records = records(pageData);
        Map<String, Object> row = records.stream()
                .filter(item -> Objects.equals(planNo, item.get("planNo")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未在列表中找到刚生成的补货建议"));
        assertEquals(2, intValue(row.get("status")));
        assertEquals(purchaseDraftNo, String.valueOf(row.get("purchaseDraftNo")));

        // 10) 详情回查：采购员可读，且状态应与列表一致。
        ApiResponse<Map<String, Object>> detailByPurchaser = get(purchaserToken, "/api/replenishments/" + planId);
        assertEquals(0, detailByPurchaser.code());
        assertNotNull(detailByPurchaser.data());
        assertEquals(2, intValue(detailByPurchaser.data().get("status")));
    }

    @Test
    void validationShouldRejectInvalidWarehouseAndInvalidStatusFilter() {
        // 非法仓库：应返回“仓库不存在或已停用”错误码。
        Map<String, Object> invalidWarehousePayload = Map.of(
                "warehouseId", 999999,
                "calcDays", 7,
                "leadTimeDays", 2,
                "safetyDays", 1,
                "remark", "M6自动化测试-非法仓库"
        );
        ApiResponse<Map<String, Object>> invalidWarehouse = post(adminToken, "/api/replenishments/calculate", invalidWarehousePayload);
        assertEquals(4601, invalidWarehouse.code(), "非法仓库应返回 4601");

        // 非法状态筛选：仅允许 0/1/2，其他值应拦截。
        ApiResponse<Void> invalidStatus = getVoid(adminToken, "/api/replenishments?status=9&pageNo=1&pageSize=5");
        assertEquals(4001, invalidStatus.code(), "非法状态筛选应返回 4001");

        // 非法日期范围：开始日期晚于结束日期应被参数校验拦截。
        ApiResponse<Void> invalidDateRange = getVoid(
                adminToken,
                "/api/replenishments?generatedDateStart=2026-02-28&generatedDateEnd=2026-02-27&pageNo=1&pageSize=5"
        );
        assertEquals(4001, invalidDateRange.code(), "非法日期范围应返回 4001");

        Map<String, Object> validPayload = Map.of(
                "warehouseId", 1,
                "calcDays", 7,
                "leadTimeDays", 2,
                "safetyDays", 1,
                "remark", "M6自动化测试-非法最终量"
        );
        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/replenishments/calculate", validPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long planId = longValue(created.data().get("id"));
        Long itemId = firstItemId(created.data());

        ApiResponse<Void> invalidFinalQty = putVoid(
                adminToken,
                "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty",
                Map.of("finalQty", -1)
        );
        assertEquals(4001, invalidFinalQty.code(), "最终量小于0应返回 4001");
    }

    @Test
    void shouldRejectMissingItemAndKeepStateMachineConsistentUnderConcurrency() {
        Map<String, Object> payload = Map.of(
                "warehouseId", 1,
                "calcDays", 7,
                "leadTimeDays", 2,
                "safetyDays", 1,
                "remark", "M6自动化测试-并发场景"
        );
        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/replenishments/calculate", payload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long planId = longValue(created.data().get("id"));
        Long itemId = firstItemId(created.data());

        // 1) 明细不存在：应返回 4613。
        ApiResponse<Void> missingItem = putVoid(
                adminToken,
                "/api/replenishments/" + planId + "/items/999999999/final-qty",
                Map.of("finalQty", 6)
        );
        assertEquals(4613, missingItem.code(), "明细不存在应返回 4613");

        // 2) 并发调最终量：两次请求都应成功，最终量应为其中一个提交值。
        List<ApiResponse<Void>> adjustResults = invokeConcurrently(List.of(
                () -> putVoid(adminToken, "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty", Map.of("finalQty", 11)),
                () -> putVoid(adminToken, "/api/replenishments/" + planId + "/items/" + itemId + "/final-qty", Map.of("finalQty", 13))
        ));
        long adjustSuccessCount = adjustResults.stream().filter(result -> result.code() == 0).count();
        assertEquals(2, adjustSuccessCount, "并发调最终量在草稿态应全部成功");

        ApiResponse<Map<String, Object>> adjustedDetail = get(adminToken, "/api/replenishments/" + planId);
        assertEquals(0, adjustedDetail.code());
        assertNotNull(adjustedDetail.data());
        int finalQtyAfterConcurrentAdjust = itemFinalQty(adjustedDetail.data(), itemId);
        assertTrue(finalQtyAfterConcurrentAdjust == 11 || finalQtyAfterConcurrentAdjust == 13, "最终量应等于并发请求中的一个目标值");

        // 3) 并发确认：应最多一个成功，另一条返回 4608。
        List<ApiResponse<Void>> confirmResults = invokeConcurrently(List.of(
                () -> putVoid(adminToken, "/api/replenishments/" + planId + "/confirm", null),
                () -> putVoid(adminToken, "/api/replenishments/" + planId + "/confirm", null)
        ));
        assertOneSuccessAndOneExpectedError(confirmResults, 4608, "并发确认");

        // 4) 并发转草稿：应最多一个成功，另一条返回 4610。
        List<ApiResponse<Void>> toDraftResults = invokeConcurrently(List.of(
                () -> postVoid(adminToken, "/api/replenishments/" + planId + "/to-purchase-draft", null),
                () -> postVoid(adminToken, "/api/replenishments/" + planId + "/to-purchase-draft", null)
        ));
        assertOneSuccessAndOneExpectedError(toDraftResults, 4610, "并发转草稿");

        ApiResponse<Map<String, Object>> finalDetail = get(adminToken, "/api/replenishments/" + planId);
        assertEquals(0, finalDetail.code());
        assertNotNull(finalDetail.data());
        assertEquals(2, intValue(finalDetail.data().get("status")), "并发后最终状态应为已转草稿");
        String purchaseDraftNo = String.valueOf(finalDetail.data().get("purchaseDraftNo"));
        assertTrue(purchaseDraftNo.startsWith("pd"), "并发后采购草稿号应存在且以 pd 开头");
    }

    @SuppressWarnings("unchecked")
    private void assertHasItems(Map<String, Object> detailData) {
        Object rawItems = detailData.get("items");
        assertTrue(rawItems instanceof List, "详情中应包含明细数组");
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        assertTrue(!items.isEmpty(), "建议明细至少应有一条");
    }

    @SuppressWarnings("unchecked")
    private Long firstItemId(Map<String, Object> detailData) {
        Object rawItems = detailData.get("items");
        assertTrue(rawItems instanceof List, "详情中应包含明细数组");
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        assertTrue(!items.isEmpty(), "建议明细至少应有一条");
        return longValue(items.get(0).get("id"));
    }

    @SuppressWarnings("unchecked")
    private void assertItemFinalQty(Map<String, Object> detailData, Long itemId, int expectedFinalQty) {
        Object rawItems = detailData.get("items");
        assertTrue(rawItems instanceof List, "详情中应包含明细数组");
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        Map<String, Object> target = items.stream()
                .filter(item -> longValue(item.get("id")) == itemId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到指定明细行"));
        assertEquals(expectedFinalQty, intValue(target.get("finalQty")));
    }

    @SuppressWarnings("unchecked")
    private int itemFinalQty(Map<String, Object> detailData, Long itemId) {
        Object rawItems = detailData.get("items");
        assertTrue(rawItems instanceof List, "详情中应包含明细数组");
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        Map<String, Object> target = items.stream()
                .filter(item -> longValue(item.get("id")) == itemId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到指定明细行"));
        return intValue(target.get("finalQty"));
    }

    private void assertOneSuccessAndOneExpectedError(List<ApiResponse<Void>> results, int expectedErrorCode, String scene) {
        long successCount = results.stream().filter(result -> result.code() == 0).count();
        long expectedErrorCount = results.stream().filter(result -> result.code() == expectedErrorCode).count();
        assertEquals(1, successCount, scene + "应且仅应有 1 个成功结果");
        assertEquals(1, expectedErrorCount, scene + "应且仅应有 1 个错误码 " + expectedErrorCode + " 结果");
    }

    private List<ApiResponse<Void>> invokeConcurrently(List<Callable<ApiResponse<Void>>> tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApiResponse<Void>>> futures = new ArrayList<>();
        try {
            for (Callable<ApiResponse<Void>> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    boolean started = start.await(5, TimeUnit.SECONDS);
                    if (!started) {
                        throw new IllegalStateException("并发任务启动超时");
                    }
                    return task.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发任务准备超时");
            start.countDown();

            List<ApiResponse<Void>> results = new ArrayList<>();
            for (Future<ApiResponse<Void>> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("并发测试执行失败", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> getPage(String token, String path) {
        ApiResponse<Map<String, Object>> result = get(token, path);
        assertEquals(0, result.code());
        assertNotNull(result.data());
        return result.data();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> pageData) {
        assertNotNull(pageData);
        Object rawRecords = pageData.get("records");
        assertTrue(rawRecords instanceof List);
        return (List<Map<String, Object>>) rawRecords;
    }

    private ApiResponse<Map<String, Object>> get(String token, String path) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(token));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ApiResponse<Void> getVoid(String token, String path) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(token));
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ApiResponse<Map<String, Object>> post(String token, String path, Map<String, Object> payload) {
        HttpEntity<?> request = payload == null ? new HttpEntity<>(authHeaders(token)) : new HttpEntity<>(payload, authHeaders(token));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ApiResponse<Void> postVoid(String token, String path, Map<String, Object> payload) {
        HttpEntity<?> request = payload == null ? new HttpEntity<>(authHeaders(token)) : new HttpEntity<>(payload, authHeaders(token));
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url(path),
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ApiResponse<Map<String, Object>> put(String token, String path, Map<String, Object> payload) {
        HttpEntity<?> request = payload == null ? new HttpEntity<>(authHeaders(token)) : new HttpEntity<>(payload, authHeaders(token));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.PUT,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ApiResponse<Void> putVoid(String token, String path, Map<String, Object> payload) {
        HttpEntity<?> request = payload == null ? new HttpEntity<>(authHeaders(token)) : new HttpEntity<>(payload, authHeaders(token));
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url(path),
                HttpMethod.PUT,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private void assertStatus(String token, String path, HttpStatus expected) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(token));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(expected, response.getStatusCode());
    }

    private void assertPostStatus(String token, String path, Map<String, Object> payload, HttpStatus expected) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authHeaders(token));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(expected, response.getStatusCode());
    }

    private void assertPutStatus(String token, String path, Map<String, Object> payload, HttpStatus expected) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authHeaders(token));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.PUT,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(expected, response.getStatusCode());
    }

    private String login(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
        );
        Map<String, Object> data = response.getBody() == null ? null : response.getBody().data();
        return data == null ? null : String.valueOf(data.get("token"));
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
