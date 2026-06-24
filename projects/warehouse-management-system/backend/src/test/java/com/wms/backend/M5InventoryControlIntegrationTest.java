/**
 * @file 速览索引
 * @summary M5 库存控制集成测试，覆盖预警权限、盘点状态机、差异修正与预警规则校验。
 * @core 1. 校验 admin/warehouse/purchaser 对预警与盘点接口的权限矩阵
 * @core 2. 校验盘点单草稿->提交->确认状态机与重复操作拦截
 * @core 3. 校验盘点差异修正后库位库存与总库存同步变更并写入双流水
 * @core 4. 校验预警规则阈值关系与重复规则业务拦截
 * @entry 先看：stocktakeFlowShouldAdjustStocksAndWriteDualTxns、alertRuleValidationShouldRejectInvalidThresholdAndDuplicateRule、permissionMatrixShouldMatchM5
 * @deps 关键依赖：TestRestTemplate、/api/stocktakes、/api/inventory/alerts、/api/inventory/alert-rules、/api/inventory/location-stocks
 * @state 关键数据：adminToken/warehouseToken/purchaserToken、status(0/1/2)、错误码 4406/4409/4418/4505/4508
 * @risk 高风险修改点：差异修正 qtyChange 口径、STOCKTAKE_ADJUST 流水写入、规则阈值约束(min<=safe<=max)
 * @link 相关文件：后端/src/test/java/com/wms/backend/M4OutboundFlowIntegrationTest.java、后端/src/main/java/com/wms/backend/stocktake/repository/StocktakeRepository.java
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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M5InventoryControlIntegrationTest {

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
    void permissionMatrixShouldMatchM5() {
        // 管理员可访问预警查询、预警规则与盘点管理。
        assertStatus(adminToken, "/api/inventory/alerts", HttpStatus.OK);
        assertStatus(adminToken, "/api/inventory/alert-rules", HttpStatus.OK);
        assertStatus(adminToken, "/api/stocktakes", HttpStatus.OK);

        // 仓库员可访问预警查询、预警规则与盘点管理。
        assertStatus(warehouseToken, "/api/inventory/alerts", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/inventory/alert-rules", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/stocktakes", HttpStatus.OK);

        // 采购员仅允许查看预警结果，不允许维护规则和盘点作业。
        assertStatus(purchaserToken, "/api/inventory/alerts", HttpStatus.OK);
        assertStatus(purchaserToken, "/api/inventory/alert-rules", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/stocktakes", HttpStatus.FORBIDDEN);
    }

    @Test
    void stocktakeFlowShouldAdjustStocksAndWriteDualTxns() {
        // 先读取一个可用的库位库存样本，确保测试基于真实库存。
        Map<String, Object> locationRow = findLocationStock("WH001", "SKU001");
        Long warehouseId = longValue(locationRow.get("warehouseId"));
        Long locationId = longValue(locationRow.get("locationId"));
        Long skuId = longValue(locationRow.get("skuId"));
        String locationCode = String.valueOf(locationRow.get("locationCode"));
        int beforeLocationQty = intValue(locationRow.get("onHandQty"));
        int beforeTotalQty = getTotalStockQty("WH001", "SKU001");

        // 盘点时把实盘数量设置为“账面 + 2”，用于验证差异修正增量链路。
        int delta = 2;
        int countQty = beforeLocationQty + delta;
        Map<String, Object> createPayload = Map.of(
                "warehouseId", warehouseId,
                "scopeType", "BY_WAREHOUSE",
                "remark", "M5自动化测试-盘点差异修正",
                "items", List.of(
                        Map.of(
                                "skuId", skuId,
                                "locationId", locationId,
                                "countQty", countQty,
                                "reason", "自动化测试-盘盈",
                                "remark", "自动化测试明细"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/stocktakes", createPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long orderId = longValue(created.data().get("id"));
        String stocktakeNo = String.valueOf(created.data().get("stocktakeNo"));
        assertEquals(0, intValue(created.data().get("status")));
        assertTrue(stocktakeNo.startsWith("st"), "盘点单号应使用 st 前缀");

        // 状态机：草稿 -> 已提交，重复提交应被拦截。
        ApiResponse<Map<String, Object>> submitted = put(adminToken, "/api/stocktakes/" + orderId + "/submit", null);
        assertEquals(0, submitted.code());
        assertNotNull(submitted.data());
        assertEquals(1, intValue(submitted.data().get("status")));

        ApiResponse<Void> duplicateSubmit = putVoid(adminToken, "/api/stocktakes/" + orderId + "/submit", null);
        assertEquals(4406, duplicateSubmit.code(), "重复提交应被状态机拦截");

        // 状态机：已提交 -> 已完成，重复确认应被拦截。
        ApiResponse<Map<String, Object>> confirmed = put(adminToken, "/api/stocktakes/" + orderId + "/confirm", null);
        assertEquals(0, confirmed.code());
        assertNotNull(confirmed.data());
        assertEquals(2, intValue(confirmed.data().get("status")));

        ApiResponse<Void> duplicateConfirm = putVoid(adminToken, "/api/stocktakes/" + orderId + "/confirm", null);
        assertEquals(4409, duplicateConfirm.code(), "重复确认应被状态机拦截");

        // 核验差异修正：库位与总库存都应增加 delta。
        int afterLocationQty = getLocationStockQty("WH001", locationCode, "SKU001");
        int afterTotalQty = getTotalStockQty("WH001", "SKU001");
        assertEquals(beforeLocationQty + delta, afterLocationQty, "盘点确认后库位库存应按差异增加");
        assertEquals(beforeTotalQty + delta, afterTotalQty, "盘点确认后仓库总库存应按差异增加");

        // 核验双流水：库位流水与仓库流水都必须写入 STOCKTAKE_ADJUST。
        Map<String, Object> locationTxnPage = getPage(adminToken, "/api/inventory/location-txns?keyword=" + stocktakeNo);
        List<Map<String, Object>> locationRecords = records(locationTxnPage);
        assertTrue(locationRecords.stream().anyMatch(r ->
                        Objects.equals("STOCKTAKE_ADJUST", r.get("bizType"))
                                && Objects.equals(stocktakeNo, r.get("bizNo"))
                                && intValue(r.get("qtyChange")) == delta),
                "库位流水应存在 STOCKTAKE_ADJUST 记录且数量变更为 +2");

        Map<String, Object> inventoryTxnPage = getPage(adminToken, "/api/inventory/txns?keyword=" + stocktakeNo);
        List<Map<String, Object>> inventoryRecords = records(inventoryTxnPage);
        assertTrue(inventoryRecords.stream().anyMatch(r ->
                        Objects.equals("STOCKTAKE_ADJUST", r.get("bizType"))
                                && Objects.equals(stocktakeNo, r.get("bizNo"))
                                && intValue(r.get("qtyChange")) == delta),
                "仓库流水应存在 STOCKTAKE_ADJUST 记录且数量变更为 +2");
    }

    @Test
    void alertRuleValidationShouldRejectInvalidThresholdAndDuplicateRule() {
        // 规则校验 1：阈值关系必须满足 min <= safe <= max。
        Map<String, Object> invalidThresholdPayload = Map.of(
                "warehouseId", 1,
                "skuId", 2,
                "minQty", 10,
                "safeQty", 5,
                "maxQty", 20,
                "status", 1,
                "remark", "M5自动化测试-阈值非法"
        );
        ApiResponse<Map<String, Object>> invalidThresholdResult = post(adminToken, "/api/inventory/alert-rules", invalidThresholdPayload);
        assertEquals(4505, invalidThresholdResult.code(), "阈值关系非法应返回 4505");

        // 规则校验 2：同一仓库 + SKU 只能存在一条规则（初始化数据已存在 WH001+SKU001）。
        Map<String, Object> duplicatePayload = Map.of(
                "warehouseId", 1,
                "skuId", 1,
                "minQty", 5,
                "safeQty", 10,
                "maxQty", 200,
                "status", 1,
                "remark", "M5自动化测试-重复规则"
        );
        ApiResponse<Map<String, Object>> duplicateResult = post(adminToken, "/api/inventory/alert-rules", duplicatePayload);
        assertEquals(4508, duplicateResult.code(), "重复规则应返回 4508");
    }

    @Test
    void draftDeleteShouldWorkAndSubmittedDeleteShouldBeRejected() {
        Map<String, Object> locationRow = findLocationStock("WH001", "SKU001");
        Long warehouseId = longValue(locationRow.get("warehouseId"));
        Long locationId = longValue(locationRow.get("locationId"));
        Long skuId = longValue(locationRow.get("skuId"));
        int bookQty = intValue(locationRow.get("onHandQty"));

        Map<String, Object> createPayload = Map.of(
                "warehouseId", warehouseId,
                "scopeType", "BY_WAREHOUSE",
                "remark", "M5自动化测试-删除草稿",
                "items", List.of(
                        Map.of(
                                "skuId", skuId,
                                "locationId", locationId,
                                "countQty", bookQty,
                                "reason", "自动化测试-删除草稿",
                                "remark", "删除草稿明细"
                        )
                )
        );

        // 1) 草稿可删：删除后详情应返回“盘点单不存在”。
        ApiResponse<Map<String, Object>> createdDraft = post(adminToken, "/api/stocktakes", createPayload);
        assertEquals(0, createdDraft.code());
        assertNotNull(createdDraft.data());
        Long draftId = longValue(createdDraft.data().get("id"));

        ApiResponse<Void> deleted = deleteVoid(adminToken, "/api/stocktakes/" + draftId);
        assertEquals(0, deleted.code(), "草稿删除应成功");

        ApiResponse<Void> deletedDetail = getVoid(adminToken, "/api/stocktakes/" + draftId);
        assertEquals(4401, deletedDetail.code(), "删除后详情应不可再读取");

        // 2) 已提交不可删：应返回“仅草稿状态可删除”。
        ApiResponse<Map<String, Object>> createdSubmitted = post(adminToken, "/api/stocktakes", createPayload);
        assertEquals(0, createdSubmitted.code());
        assertNotNull(createdSubmitted.data());
        Long submittedId = longValue(createdSubmitted.data().get("id"));

        ApiResponse<Map<String, Object>> submitResult = put(adminToken, "/api/stocktakes/" + submittedId + "/submit", null);
        assertEquals(0, submitResult.code());

        ApiResponse<Void> deleteSubmitted = deleteVoid(adminToken, "/api/stocktakes/" + submittedId);
        assertEquals(4418, deleteSubmitted.code(), "已提交单据删除应被状态机拦截");
    }

    private Map<String, Object> findLocationStock(String warehouseCode, String skuCode) {
        Map<String, Object> data = getPage(adminToken, "/api/inventory/location-stocks?keyword=" + skuCode + "&pageNo=1&pageSize=50");
        List<Map<String, Object>> stockRows = records(data);
        return stockRows.stream()
                .filter(row -> Objects.equals(warehouseCode, row.get("warehouseCode")) && Objects.equals(skuCode, row.get("skuCode")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到可用库位库存样本"));
    }

    private int getTotalStockQty(String warehouseCode, String skuCode) {
        Map<String, Object> data = getPage(adminToken, "/api/inventory/stocks?keyword=" + skuCode);
        List<Map<String, Object>> stockRows = records(data);
        return stockRows.stream()
                .filter(row -> Objects.equals(warehouseCode, row.get("warehouseCode")) && Objects.equals(skuCode, row.get("skuCode")))
                .map(row -> intValue(row.get("onHandQty")))
                .findFirst()
                .orElse(0);
    }

    private int getLocationStockQty(String warehouseCode, String locationCode, String skuCode) {
        Map<String, Object> data = getPage(adminToken, "/api/inventory/location-stocks?keyword=" + skuCode + "&pageNo=1&pageSize=50");
        List<Map<String, Object>> stockRows = records(data);
        return stockRows.stream()
                .filter(row ->
                        Objects.equals(warehouseCode, row.get("warehouseCode"))
                                && Objects.equals(locationCode, row.get("locationCode"))
                                && Objects.equals(skuCode, row.get("skuCode")))
                .map(row -> intValue(row.get("onHandQty")))
                .findFirst()
                .orElse(0);
    }

    private Map<String, Object> getPage(String token, String path) {
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
        assertEquals(0, response.getBody().code());
        return response.getBody().data();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> pageData) {
        assertNotNull(pageData);
        Object rawRecords = pageData.get("records");
        assertTrue(rawRecords instanceof List);
        return (List<Map<String, Object>>) rawRecords;
    }

    private ApiResponse<Map<String, Object>> post(String token, String path, Map<String, Object> payload) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authHeaders(token));
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

    private ApiResponse<Void> deleteVoid(String token, String path) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(token));
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url(path),
                HttpMethod.DELETE,
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
