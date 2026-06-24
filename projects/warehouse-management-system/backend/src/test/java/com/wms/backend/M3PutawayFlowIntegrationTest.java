/**
 * @file 速览索引
 * @summary M3 上架流程集成测试，覆盖权限矩阵、上架状态机、库位库存增量与流水一致性。
 * @core 1. 校验 admin/warehouse/purchaser 对上架与库位库存接口的权限
 * @core 2. 校验上架单草稿->提交->确认状态机与重复操作拦截
 * @core 3. 校验确认后总库存不变、库位库存增加
 * @core 4. 校验超量上架拦截与草稿删除规则
 * @entry 先看：putawayFlowShouldKeepTotalStockAndIncreaseLocationStock、submitShouldFailWhenRequestedQtyExceedsAvailable、permissionMatrixShouldMatchM3
 * @deps 关键依赖：TestRestTemplate、/api/putaways、/api/inventory/location-stocks、/api/inventory/location-txns、/api/auth/login
 * @state 关键数据：adminToken/warehouseToken/purchaserToken、status(0/1/2)、错误码 4206/4209/4216/4217
 * @risk 高风险修改点：可上架数量校验、库存口径(总库存 vs 库位库存)、流水 bizType=PUTAWAY
 * @link 相关文件：后端/src/test/java/com/wms/backend/M2InboundFlowIntegrationTest.java、后端/src/test/java/com/wms/backend/M4OutboundFlowIntegrationTest.java
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
class M3PutawayFlowIntegrationTest {

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
    void permissionMatrixShouldMatchM3() {
        // 管理员可访问上架与库位库存相关接口。
        assertStatus(adminToken, "/api/putaways", HttpStatus.OK);
        assertStatus(adminToken, "/api/inventory/location-stocks", HttpStatus.OK);
        assertStatus(adminToken, "/api/inventory/location-txns", HttpStatus.OK);

        // 仓库员可访问上架与库位库存相关接口。
        assertStatus(warehouseToken, "/api/putaways", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/inventory/location-stocks", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/inventory/location-txns", HttpStatus.OK);

        // 采购员不可访问上架与库位库存相关接口，应被 403 拦截。
        assertStatus(purchaserToken, "/api/putaways", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/inventory/location-stocks", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/inventory/location-txns", HttpStatus.FORBIDDEN);
    }

    @Test
    void putawayFlowShouldKeepTotalStockAndIncreaseLocationStock() {
        int beforeTotalQty = getTotalStockQty("WH001", "SKU001");
        int beforeLocationQty = getLocationStockQty("WH001", "A01-01", "SKU001");

        // 新建一张草稿上架单，明细数量控制为 3，便于验证增量。
        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "sourceType", "INBOUND",
                "sourceOrderId", 2,
                "sourceOrderNo", "in202602250002",
                "remark", "M3自动化测试-上架流程",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", 3,
                                "actualQty", 3,
                                "remark", "自动化测试明细"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/putaways", createPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long orderId = longValue(created.data().get("id"));
        String putawayNo = String.valueOf(created.data().get("putawayNo"));
        assertEquals(0, intValue(created.data().get("status")));
        assertTrue(putawayNo.startsWith("pa"), "上架单号应使用 pa 前缀");

        // 草稿 -> 已提交。
        ApiResponse<Map<String, Object>> submitted = put(adminToken, "/api/putaways/" + orderId + "/submit", null);
        assertEquals(0, submitted.code());
        assertNotNull(submitted.data());
        assertEquals(1, intValue(submitted.data().get("status")));

        // 重复提交应被状态机拦截。
        ApiResponse<Void> duplicateSubmit = putVoid(adminToken, "/api/putaways/" + orderId + "/submit", null);
        assertEquals(4206, duplicateSubmit.code(), "重复提交应被状态机拦截");

        // 已提交 -> 已完成。
        ApiResponse<Map<String, Object>> confirmed = put(adminToken, "/api/putaways/" + orderId + "/confirm", null);
        assertEquals(0, confirmed.code());
        assertNotNull(confirmed.data());
        assertEquals(2, intValue(confirmed.data().get("status")));

        // 重复确认应被状态机拦截。
        ApiResponse<Void> duplicateConfirm = putVoid(adminToken, "/api/putaways/" + orderId + "/confirm", null);
        assertEquals(4209, duplicateConfirm.code(), "重复确认应被状态机拦截");

        // 验证上架后“总库存不变、库位库存增加”。
        int afterTotalQty = getTotalStockQty("WH001", "SKU001");
        int afterLocationQty = getLocationStockQty("WH001", "A01-01", "SKU001");
        assertEquals(beforeTotalQty, afterTotalQty, "确认上架后仓库总库存应保持不变");
        assertEquals(beforeLocationQty + 3, afterLocationQty, "确认上架后目标库位库存应增加 3");

        // 验证库位流水存在 PUTAWAY 记录且数量变更为 3。
        Map<String, Object> locationTxnPage = getPage(adminToken, "/api/inventory/location-txns?keyword=" + putawayNo);
        List<Map<String, Object>> records = records(locationTxnPage);
        assertTrue(records.stream().anyMatch(r ->
                        Objects.equals("PUTAWAY", r.get("bizType"))
                                && Objects.equals(putawayNo, r.get("bizNo"))
                                && intValue(r.get("qtyChange")) == 3),
                "库位流水应存在 PUTAWAY 记录且数量变更为 3");
    }

    @Test
    void submitShouldFailWhenRequestedQtyExceedsAvailable() {
        // 创建一张超量上架草稿单，提交时应命中“可上架数量不足”错误。
        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "sourceType", "INBOUND",
                "sourceOrderId", 2,
                "sourceOrderNo", "in202602250002",
                "remark", "M3自动化测试-超量上架",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "locationId", 1,
                                "planQty", 100000,
                                "actualQty", 0,
                                "remark", "超量校验"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/putaways", createPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long orderId = longValue(created.data().get("id"));

        ApiResponse<Void> submitted = putVoid(adminToken, "/api/putaways/" + orderId + "/submit", null);
        assertEquals(4216, submitted.code(), "超量上架应返回可上架数量不足错误码");
    }

    @Test
    void draftDeleteShouldWorkAndSubmittedDeleteShouldBeRejected() {
        Map<String, Object> createPayload = Map.of(
                "warehouseId", 1,
                "sourceType", "INBOUND",
                "sourceOrderId", 2,
                "sourceOrderNo", "in202602250002",
                "remark", "M3自动化测试-删除草稿",
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

        // 1) 草稿可删：删除后详情应返回“上架单不存在”。
        ApiResponse<Map<String, Object>> createdDraft = post(adminToken, "/api/putaways", createPayload);
        assertEquals(0, createdDraft.code());
        assertNotNull(createdDraft.data());
        Long draftId = longValue(createdDraft.data().get("id"));

        ApiResponse<Void> deleted = deleteVoid(adminToken, "/api/putaways/" + draftId);
        assertEquals(0, deleted.code(), "草稿删除应成功");

        ApiResponse<Void> deletedDetail = getVoid(adminToken, "/api/putaways/" + draftId);
        assertEquals(4201, deletedDetail.code(), "删除后详情应不可再读取");

        // 2) 已提交不可删：应返回“仅草稿状态可删除”。
        ApiResponse<Map<String, Object>> createdSubmitted = post(adminToken, "/api/putaways", createPayload);
        assertEquals(0, createdSubmitted.code());
        assertNotNull(createdSubmitted.data());
        Long submittedId = longValue(createdSubmitted.data().get("id"));

        ApiResponse<Map<String, Object>> submitResult = put(adminToken, "/api/putaways/" + submittedId + "/submit", null);
        assertEquals(0, submitResult.code());

        ApiResponse<Void> deleteSubmitted = deleteVoid(adminToken, "/api/putaways/" + submittedId);
        assertEquals(4217, deleteSubmitted.code(), "已提交单据删除应被状态机拦截");
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
