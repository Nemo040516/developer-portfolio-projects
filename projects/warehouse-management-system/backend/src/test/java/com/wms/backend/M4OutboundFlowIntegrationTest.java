/**
 * @file 速览索引
 * @summary M4 出库流程集成测试，覆盖权限矩阵、出库状态机、库存扣减与双流水一致性。
 * @core 1. 校验 admin/warehouse/purchaser 对出库接口的权限边界
 * @core 2. 校验出库单草稿->提交->确认状态机与重复操作拦截
 * @core 3. 校验确认后库位库存与总库存同步减少
 * @core 4. 校验库存不足、重复明细、删除规则等异常分支
 * @entry 先看：outboundFlowShouldDecreaseLocationAndTotalStock、confirmShouldFailWhenLocationStockInsufficient、createShouldFailWhenDuplicateSkuAndLocationExistsInItems
 * @deps 关键依赖：TestRestTemplate、/api/outbounds、/api/inventory/location-stocks、/api/inventory/location-txns、/api/inventory/txns
 * @state 关键数据：adminToken/warehouseToken/purchaserToken、status(0/1/2)、错误码 4306/4309/4316/4318/4319
 * @risk 高风险修改点：库存扣减口径、重复 SKU+库位 业务校验、出库流水 qtyChange 负值约定
 * @link 相关文件：后端/src/test/java/com/wms/backend/M3PutawayFlowIntegrationTest.java、后端/src/test/java/com/wms/backend/M5InventoryControlIntegrationTest.java
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
class M4OutboundFlowIntegrationTest {

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
    void permissionMatrixShouldMatchM4() {
        assertStatus(adminToken, "/api/outbounds", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/outbounds", HttpStatus.OK);
        assertStatus(purchaserToken, "/api/outbounds", HttpStatus.FORBIDDEN);
    }

    @Test
    void outboundFlowShouldDecreaseLocationAndTotalStock() {
        int beforeLocationQty = getLocationStockQty("WH001", "A01-01", "SKU001");
        int beforeTotalQty = getTotalStockQty("WH001", "SKU001");

        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "outboundType", "SALES",
                "targetName", "M4自动化测试客户",
                "remark", "M4自动化测试-出库流程",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", 2,
                                "actualQty", 2,
                                "remark", "自动化测试明细"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/outbounds", createPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long orderId = longValue(created.data().get("id"));
        String outboundNo = String.valueOf(created.data().get("outboundNo"));
        assertEquals(0, intValue(created.data().get("status")));
        assertTrue(outboundNo.startsWith("out"), "出库单号应使用 out 前缀");

        ApiResponse<Map<String, Object>> submitted = put(adminToken, "/api/outbounds/" + orderId + "/submit", null);
        assertEquals(0, submitted.code());
        assertNotNull(submitted.data());
        assertEquals(1, intValue(submitted.data().get("status")));

        ApiResponse<Void> duplicateSubmit = putVoid(adminToken, "/api/outbounds/" + orderId + "/submit", null);
        assertEquals(4306, duplicateSubmit.code(), "重复提交应被状态机拦截");

        ApiResponse<Map<String, Object>> confirmed = put(adminToken, "/api/outbounds/" + orderId + "/confirm", null);
        assertEquals(0, confirmed.code());
        assertNotNull(confirmed.data());
        assertEquals(2, intValue(confirmed.data().get("status")));

        ApiResponse<Void> duplicateConfirm = putVoid(adminToken, "/api/outbounds/" + orderId + "/confirm", null);
        assertEquals(4309, duplicateConfirm.code(), "重复确认应被状态机拦截");

        int afterLocationQty = getLocationStockQty("WH001", "A01-01", "SKU001");
        int afterTotalQty = getTotalStockQty("WH001", "SKU001");
        assertEquals(beforeLocationQty - 2, afterLocationQty, "确认出库后库位库存应减少 2");
        assertEquals(beforeTotalQty - 2, afterTotalQty, "确认出库后仓库总库存应减少 2");

        Map<String, Object> locationTxnPage = getPage(adminToken, "/api/inventory/location-txns?keyword=" + outboundNo);
        List<Map<String, Object>> locationRecords = records(locationTxnPage);
        assertTrue(locationRecords.stream().anyMatch(r ->
                        Objects.equals("OUTBOUND", r.get("bizType"))
                                && Objects.equals(outboundNo, r.get("bizNo"))
                                && intValue(r.get("qtyChange")) == -2),
                "库位流水应存在 OUTBOUND 记录且数量变更为 -2");

        Map<String, Object> inventoryTxnPage = getPage(adminToken, "/api/inventory/txns?keyword=" + outboundNo);
        List<Map<String, Object>> inventoryRecords = records(inventoryTxnPage);
        assertTrue(inventoryRecords.stream().anyMatch(r ->
                        Objects.equals("OUTBOUND", r.get("bizType"))
                                && Objects.equals(outboundNo, r.get("bizNo"))
                                && intValue(r.get("qtyChange")) == -2),
                "仓库流水应存在 OUTBOUND 记录且数量变更为 -2");
    }

    @Test
    void confirmShouldFailWhenLocationStockInsufficient() {
        int currentLocationQty = getLocationStockQty("WH001", "A01-01", "SKU001");
        int excessiveQty = currentLocationQty + 1000;

        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "outboundType", "SALES",
                "targetName", "M4自动化测试客户-库存不足",
                "remark", "M4自动化测试-库存不足",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", excessiveQty,
                                "actualQty", excessiveQty,
                                "remark", "库存不足校验"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/outbounds", createPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long orderId = longValue(created.data().get("id"));

        ApiResponse<Map<String, Object>> submitted = put(adminToken, "/api/outbounds/" + orderId + "/submit", null);
        assertEquals(0, submitted.code());

        ApiResponse<Void> confirmed = putVoid(adminToken, "/api/outbounds/" + orderId + "/confirm", null);
        assertEquals(4316, confirmed.code(), "库存不足时应返回库位库存不足错误码");
    }

    @Test
    void createShouldFailWhenDuplicateSkuAndLocationExistsInItems() {
        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "outboundType", "SALES",
                "targetName", "M4自动化测试客户-重复明细",
                "remark", "M4自动化测试-重复明细",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", 1,
                                "actualQty", 0,
                                "remark", "重复明细-1"
                        ),
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", 2,
                                "actualQty", 0,
                                "remark", "重复明细-2"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/outbounds", createPayload);
        assertEquals(4318, created.code(), "重复的 SKU+库位 明细应返回业务错误码，避免落入 5000");
    }

    @Test
    void draftDeleteShouldWorkAndSubmittedDeleteShouldBeRejected() {
        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "outboundType", "SALES",
                "targetName", "M4自动化测试客户-删除草稿",
                "remark", "M4自动化测试-删除草稿",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", 1,
                                "actualQty", 1,
                                "remark", "删除草稿明细"
                        )
                )
        );

        // 1) 草稿可删：删除后详情应返回“出库单不存在”。
        ApiResponse<Map<String, Object>> createdDraft = post(adminToken, "/api/outbounds", createPayload);
        assertEquals(0, createdDraft.code());
        assertNotNull(createdDraft.data());
        Long draftId = longValue(createdDraft.data().get("id"));

        ApiResponse<Void> deleted = deleteVoid(adminToken, "/api/outbounds/" + draftId);
        assertEquals(0, deleted.code(), "草稿删除应成功");

        ApiResponse<Void> deletedDetail = getVoid(adminToken, "/api/outbounds/" + draftId);
        assertEquals(4301, deletedDetail.code(), "删除后详情应不可再读取");

        // 2) 已提交不可删：应返回“仅草稿状态可删除”。
        ApiResponse<Map<String, Object>> createdSubmitted = post(adminToken, "/api/outbounds", createPayload);
        assertEquals(0, createdSubmitted.code());
        assertNotNull(createdSubmitted.data());
        Long submittedId = longValue(createdSubmitted.data().get("id"));

        ApiResponse<Map<String, Object>> submitResult = put(adminToken, "/api/outbounds/" + submittedId + "/submit", null);
        assertEquals(0, submitResult.code());

        ApiResponse<Void> deleteSubmitted = deleteVoid(adminToken, "/api/outbounds/" + submittedId);
        assertEquals(4319, deleteSubmitted.code(), "已提交单据删除应被状态机拦截");
    }

    private int getTotalStockQty(String warehouseCode, String skuCode) {
        Map<String, Object> data = getPage(adminToken, "/api/inventory/stocks?keyword=" + skuCode);
        List<Map<String, Object>> records = records(data);
        return records.stream()
                .filter(row -> Objects.equals(warehouseCode, row.get("warehouseCode")) && Objects.equals(skuCode, row.get("skuCode")))
                .map(row -> intValue(row.get("onHandQty")))
                .findFirst()
                .orElse(0);
    }

    private int getLocationStockQty(String warehouseCode, String locationCode, String skuCode) {
        Map<String, Object> data = getPage(adminToken, "/api/inventory/location-stocks?keyword=" + skuCode);
        List<Map<String, Object>> records = records(data);
        return records.stream()
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
