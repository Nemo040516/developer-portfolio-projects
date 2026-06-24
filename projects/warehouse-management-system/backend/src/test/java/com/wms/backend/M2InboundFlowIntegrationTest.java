/**
 * @file 速览索引
 * @summary M2 入库流程集成测试，覆盖权限矩阵、入库状态机、库存增量与流水一致性。
 * @core 1. 校验 admin/warehouse/purchaser 对入库与库存接口的权限差异
 * @core 2. 校验入库单草稿->提交->确认状态机与重复操作拦截
 * @core 3. 校验确认后总库存增加与 INBOUND 流水落账
 * @core 4. 校验草稿可删、已提交不可删
 * @entry 先看：inboundFlowShouldUpdateStockAndTxnConsistently、permissionMatrixShouldMatchM2、draftDeleteShouldWorkAndSubmittedDeleteShouldBeRejected
 * @deps 关键依赖：TestRestTemplate、/api/inbounds、/api/inventory/stocks、/api/inventory/txns、/api/auth/login
 * @state 关键数据：adminToken/warehouseToken/purchaserToken、status(0/1/2)、错误码 4105/4108/4114
 * @risk 高风险修改点：状态机错误码、流水 bizType/bizNo 字段、库存字段 onHandQty 命名
 * @link 相关文件：后端/src/test/java/com/wms/backend/M1MasterDataReadinessTest.java、后端/src/test/java/com/wms/backend/M3PutawayFlowIntegrationTest.java
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
class M2InboundFlowIntegrationTest {

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
    void permissionMatrixShouldMatchM2() {
        assertStatus(adminToken, "/api/inbounds", HttpStatus.OK);
        assertStatus(adminToken, "/api/inventory/stocks", HttpStatus.OK);

        assertStatus(warehouseToken, "/api/inbounds", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/inventory/txns", HttpStatus.OK);

        assertStatus(purchaserToken, "/api/inbounds", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/inventory/stocks", HttpStatus.FORBIDDEN);
    }

    @Test
    void inboundFlowShouldUpdateStockAndTxnConsistently() {
        int beforeQty = getStockQty("WH001", "SKU001");

        Map<String, Object> createPayload = Map.of(
                "supplierId", 1,
                "warehouseId", 1,
                "remark", "M2自动化测试-入库流程",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "planQty", 5,
                                "receivedQty", 5,
                                "remark", "自动化测试明细"
                        )
                )
        );

        ApiResponse<Map<String, Object>> created = post(adminToken, "/api/inbounds", createPayload);
        assertEquals(0, created.code());
        assertNotNull(created.data());
        Long orderId = longValue(created.data().get("id"));
        String inboundNo = String.valueOf(created.data().get("inboundNo"));
        assertEquals(0, intValue(created.data().get("status")));
        assertTrue(inboundNo.startsWith("in"), "入库单号应使用 in 前缀");

        ApiResponse<Map<String, Object>> submitted = put(adminToken, "/api/inbounds/" + orderId + "/submit", null);
        assertEquals(0, submitted.code());
        assertNotNull(submitted.data());
        assertEquals(1, intValue(submitted.data().get("status")));

        ApiResponse<Void> duplicateSubmit = putVoid(adminToken, "/api/inbounds/" + orderId + "/submit", null);
        assertEquals(4105, duplicateSubmit.code(), "重复提交应被状态机拦截");

        ApiResponse<Map<String, Object>> confirmed = put(adminToken, "/api/inbounds/" + orderId + "/confirm", null);
        assertEquals(0, confirmed.code());
        assertNotNull(confirmed.data());
        assertEquals(2, intValue(confirmed.data().get("status")));

        ApiResponse<Void> duplicateConfirm = putVoid(adminToken, "/api/inbounds/" + orderId + "/confirm", null);
        assertEquals(4108, duplicateConfirm.code(), "重复确认应被状态机拦截");

        int afterQty = getStockQty("WH001", "SKU001");
        assertEquals(beforeQty + 5, afterQty, "确认入库后库存应增加 5");

        Map<String, Object> txnPage = getPage(adminToken, "/api/inventory/txns?keyword=" + inboundNo);
        List<Map<String, Object>> records = records(txnPage);
        assertTrue(records.stream().anyMatch(r ->
                        Objects.equals("INBOUND", r.get("bizType"))
                                && Objects.equals(inboundNo, r.get("bizNo"))
                                && intValue(r.get("qtyChange")) == 5),
                "库存流水应存在 INBOUND 记录且数量变更为 5");
    }

    @Test
    void draftDeleteShouldWorkAndSubmittedDeleteShouldBeRejected() {
        Map<String, Object> createPayload = Map.of(
                "supplierId", 1,
                "warehouseId", 1,
                "remark", "M2自动化测试-删除草稿",
                "items", List.of(
                        Map.of(
                                "skuId", 1,
                                "planQty", 1,
                                "receivedQty", 1,
                                "remark", "删除草稿明细"
                        )
                )
        );

        // 1) 草稿单据应可直接删除。
        ApiResponse<Map<String, Object>> createdDraft = post(adminToken, "/api/inbounds", createPayload);
        assertEquals(0, createdDraft.code());
        assertNotNull(createdDraft.data());
        Long draftId = longValue(createdDraft.data().get("id"));

        ApiResponse<Void> deleted = deleteVoid(adminToken, "/api/inbounds/" + draftId);
        assertEquals(0, deleted.code(), "草稿删除应成功");

        ApiResponse<Void> deletedDetail = getVoid(adminToken, "/api/inbounds/" + draftId);
        assertEquals(4041, deletedDetail.code(), "删除后详情应不可再读取");

        // 2) 已提交单据不允许删除。
        ApiResponse<Map<String, Object>> createdSubmitted = post(adminToken, "/api/inbounds", createPayload);
        assertEquals(0, createdSubmitted.code());
        assertNotNull(createdSubmitted.data());
        Long submittedId = longValue(createdSubmitted.data().get("id"));

        ApiResponse<Map<String, Object>> submitResult = put(adminToken, "/api/inbounds/" + submittedId + "/submit", null);
        assertEquals(0, submitResult.code());

        ApiResponse<Void> deleteSubmitted = deleteVoid(adminToken, "/api/inbounds/" + submittedId);
        assertEquals(4114, deleteSubmitted.code(), "已提交单据删除应被状态机拦截");
    }

    private int getStockQty(String warehouseCode, String skuCode) {
        Map<String, Object> data = getPage(adminToken, "/api/inventory/stocks?keyword=" + skuCode);
        List<Map<String, Object>> records = records(data);
        return records.stream()
                .filter(row -> Objects.equals(warehouseCode, row.get("warehouseCode")) && Objects.equals(skuCode, row.get("skuCode")))
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
