/**
 * @file 速览索引
 * @summary M1 集成测试，负责验证账号权限、管理员治理能力与核心业务限制是否符合预期。
 * @core 1. 验证 ADMIN / PURCHASER / WAREHOUSE 的权限矩阵
 * @core 2. 验证用户新增、密码重置、角色状态保护等治理规则
 * @core 3. 作为管理员端改动后的关键回归测试入口
 * @entry 先看：adminShouldAccessAllM1Apis、warehousePermissionShouldMatchMatrix、adminRoleShouldNotBeDisabled
 * @deps 关键依赖：测试环境数据库、AuthController、UserController、RoleController、SecurityConfig
 * @risk 高风险修改点：权限矩阵、接口路径、业务码断言，任何变动都要同步测试
 * @link 相关文件：后端/src/main/java/com/wms/backend/security/SecurityConfig.java、后端/src/main/java/com/wms/backend/auth/service/MenuService.java
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
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M1SecurityIntegrationTest {

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
    void adminShouldAccessAllM1Apis() {
        assertStatus(adminToken, "/api/users", HttpStatus.OK);
        assertStatus(adminToken, "/api/roles", HttpStatus.OK);
        assertStatus(adminToken, "/api/warehouses", HttpStatus.OK);
        assertStatus(adminToken, "/api/locations", HttpStatus.OK);
        assertStatus(adminToken, "/api/skus", HttpStatus.OK);
        assertStatus(adminToken, "/api/suppliers", HttpStatus.OK);
    }

    @Test
    void warehousePermissionShouldMatchMatrix() {
        assertStatus(warehouseToken, "/api/warehouses", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/locations", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/skus", HttpStatus.OK);
        assertStatus(warehouseToken, "/api/users", HttpStatus.FORBIDDEN);
        assertStatus(warehouseToken, "/api/roles", HttpStatus.FORBIDDEN);
        assertStatus(warehouseToken, "/api/suppliers", HttpStatus.FORBIDDEN);
    }

    @Test
    void purchaserPermissionShouldMatchMatrix() {
        assertStatus(purchaserToken, "/api/skus", HttpStatus.OK);
        assertStatus(purchaserToken, "/api/suppliers", HttpStatus.OK);
        // 采购员仅可读 SKU，写接口应被鉴权拦截。
        assertPostForbidden(
                purchaserToken,
                "/api/skus",
                Map.of(
                        "skuCode", "SKU-PUR-READONLY",
                        "skuName", "采购员写入拦截验证",
                        "specification", "M1",
                        "unit", "件",
                        "safeStock", 10,
                        "remark", "权限校验"
                )
        );
        // 采购员需要读取仓库下拉选项以支持 M6 生成建议。
        assertRawStatus(purchaserToken, "/api/warehouses/options", HttpStatus.OK);
        assertStatus(purchaserToken, "/api/users", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/roles", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/warehouses", HttpStatus.FORBIDDEN);
        assertStatus(purchaserToken, "/api/locations", HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateWarehouseCodeShouldFail() {
        Map<String, Object> payload = Map.of(
                "warehouseCode", "WH001",
                "warehouseName", "重复仓库",
                "address", "测试地址",
                "managerName", "测试员",
                "contactPhone", "13800000003",
                "remark", "唯一性校验"
        );
        assertBusinessCode(adminToken, "/api/warehouses", payload, 4006);
    }

    @Test
    void duplicateLocationCodeShouldFail() {
        Map<String, Object> payload = Map.of(
                "warehouseId", 1,
                "locationCode", "A01-01",
                "areaName", "A区",
                "locationType", "标准货架位",
                "capacity", 10,
                "remark", "唯一性校验"
        );
        assertBusinessCode(adminToken, "/api/locations", payload, 4008);
    }

    @Test
    void duplicateSkuCodeShouldFail() {
        Map<String, Object> payload = Map.of(
                "skuCode", "SKU001",
                "skuName", "重复SKU",
                "specification", "M4*20",
                "unit", "盒",
                "safeStock", 20,
                "remark", "唯一性校验"
        );
        assertBusinessCode(adminToken, "/api/skus", payload, 4010);
    }

    @Test
    void duplicateSupplierCodeShouldFail() {
        Map<String, Object> payload = Map.of(
                "supplierCode", "SUP001",
                "supplierName", "重复供应商",
                "contactName", "测试",
                "contactPhone", "13800000003",
                "leadTimeDays", 2,
                "remark", "唯一性校验"
        );
        assertBusinessCode(adminToken, "/api/suppliers", payload, 4011);
    }

    @Test
    void duplicateUsernameShouldFail() {
        Map<String, Object> payload = Map.of(
                "username", "admin",
                "realName", "重复账号",
                "mobile", "13800000003",
                "email", "dup@example.com",
                "roleId", 1
        );
        assertBusinessCode(adminToken, "/api/users", payload, 4030);
    }

    @Test
    void adminShouldNotCreateAdminUserByApi() {
        Map<String, Object> payload = Map.of(
                "username", "admin_api_block_" + System.currentTimeMillis(),
                "password", "Admin12345",
                "realName", "管理员新增管理员拦截",
                "mobile", "13800000004",
                "email", "admin-block@example.com",
                "roleId", 1
        );
        assertBusinessCode(adminToken, "/api/users", payload, 4032);
    }

    @Test
    void adminShouldCreateWarehouseUserWithCustomPassword() {
        String username = "warehouse_new_" + System.currentTimeMillis();
        String password = "Wh12345";
        Map<String, Object> payload = Map.of(
                "username", username,
                "password", password,
                "realName", "仓库员新增",
                "mobile", "13800000005",
                "email", "warehouse-new@example.com",
                "roleId", 2
        );
        assertBusinessCode(adminToken, "/api/users", payload, 0);
        assertLoginSuccess(username, password);
    }

    @Test
    void invalidPhoneShouldFail() {
        Map<String, Object> payload = Map.of(
                "supplierCode", "SUP-TEMP-01",
                "supplierName", "手机号非法",
                "contactName", "测试",
                "contactPhone", "abc",
                "leadTimeDays", 2,
                "remark", "格式校验"
        );
        HttpHeaders headers = authHeaders(adminToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/suppliers"),
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4001, response.getBody().code());
    }

    @Test
    void adminShouldResetUserPassword() {
        HttpHeaders headers = authHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/users/2/reset-password"),
                HttpMethod.PUT,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().code());
    }

    @Test
    void disabledRoleUserShouldNotLogin() {
        updateRoleStatus(2, 0);
        try {
            assertLoginBusinessCode("warehouse", "12345", 4009);
        } finally {
            updateRoleStatus(2, 1);
        }
    }

    @Test
    void adminRoleShouldNotBeDisabled() {
        HttpHeaders headers = authHeaders(adminToken);
        HttpEntity<Map<String, Integer>> request = new HttpEntity<>(Map.of("status", 0), headers);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/roles/1/status"),
                HttpMethod.PUT,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4024, response.getBody().code());
    }

    private void assertStatus(String token, String path, HttpStatus expected) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(token));
        ResponseEntity<ApiResponse<PageResult<Object>>> response = restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(expected, response.getStatusCode());
    }

    private void assertRawStatus(String token, String path, HttpStatus expected) {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(token));
        ResponseEntity<String> response = restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                request,
                String.class
        );
        assertEquals(expected, response.getStatusCode());
    }

    private void assertPostForbidden(String token, String path, Map<String, Object> payload) {
        HttpHeaders headers = authHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                url(path),
                HttpMethod.POST,
                request,
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    private void assertBusinessCode(String token, String path, Map<String, Object> payload, int expectedCode) {
        HttpHeaders headers = authHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url(path),
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedCode, response.getBody().code());
    }

    private void assertLoginBusinessCode(String username, String password, int expectedCode) {
        LoginRequest request = new LoginRequest(username, password);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedCode, response.getBody().code());
    }

    private void assertLoginSuccess(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().code());
        assertNotNull(response.getBody().data());
        assertNotNull(response.getBody().data().get("token"));
    }

    private void updateRoleStatus(long roleId, int status) {
        HttpHeaders headers = authHeaders(adminToken);
        HttpEntity<Map<String, Integer>> request = new HttpEntity<>(Map.of("status", status), headers);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/roles/" + roleId + "/status"),
                HttpMethod.PUT,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().code());
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

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
