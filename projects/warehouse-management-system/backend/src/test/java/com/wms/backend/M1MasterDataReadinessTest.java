/**
 * @file 速览索引
 * @summary M1 主数据就绪集成测试，验证仓库/库位/SKU/供应商在进入 M2 前已具备基础数据。
 * @core 1. 登录管理员并携带 Bearer Token 发起 API 访问
 * @core 2. 校验四类主数据分页 total 均大于 0
 * @core 3. 校验库位记录包含 warehouseId 与 locationCode
 * @entry 先看：m1MasterDataShouldBeReadyForM2、fetchPage、login
 * @deps 关键依赖：TestRestTemplate、/api/auth/login、/api/warehouses、/api/locations、/api/skus、/api/suppliers
 * @state 关键数据：adminToken、ApiResponse<Map<String,Object>>、page.records
 * @risk 高风险修改点：分页返回结构(records/total)或鉴权头口径变化会导致基线测试失效
 * @link 相关文件：后端/src/main/java/com/wms/backend/common/ApiResponse.java、后端/src/test/java/com/wms/backend/M2InboundFlowIntegrationTest.java
 */
package com.wms.backend;

import com.wms.backend.auth.dto.LoginRequest;
import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M1MasterDataReadinessTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = login("admin", "12345");
    }

    @Test
    void m1MasterDataShouldBeReadyForM2() {
        Map<String, Object> warehousePage = fetchPage("/api/warehouses");
        Map<String, Object> locationPage = fetchPage("/api/locations");
        Map<String, Object> skuPage = fetchPage("/api/skus");
        Map<String, Object> supplierPage = fetchPage("/api/suppliers");

        assertTrue(((Number) warehousePage.get("total")).longValue() > 0, "仓库主数据不能为空");
        assertTrue(((Number) locationPage.get("total")).longValue() > 0, "库位主数据不能为空");
        assertTrue(((Number) skuPage.get("total")).longValue() > 0, "SKU主数据不能为空");
        assertTrue(((Number) supplierPage.get("total")).longValue() > 0, "供应商主数据不能为空");

        List<?> locationRecords = (List<?>) locationPage.get("records");
        assertFalse(locationRecords.isEmpty(), "库位记录不能为空");
        Object firstLocationObj = locationRecords.get(0);
        assertTrue(firstLocationObj instanceof Map, "库位记录结构异常");
        Map<?, ?> firstLocation = (Map<?, ?>) firstLocationObj;
        assertNotNull(firstLocation.get("warehouseId"), "库位必须关联仓库ID");
        assertNotNull(firstLocation.get("locationCode"), "库位编码不能为空");
    }

    private Map<String, Object> fetchPage(String path) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().code());
        Map<String, Object> data = response.getBody().data();
        assertNotNull(data);
        return data;
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
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
