package com.example.backend;

import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BackendApplicationTests extends ApiTestBase {

    @jakarta.annotation.Resource
    private DataSource dataSource;

    @Test
    void shouldLoadSmokeEndpointWithTestProfile() throws Exception {
        // Task 1 先保证基础 smoke 入口可运行，后续 Task 2 再逐步补充业务接口用例。
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    @Test
    void shouldConnectToTestDatasource() throws Exception {
        assertNotNull(dataSource, "测试数据源不应为空");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            resultSet.next();
            assertEquals(1, resultSet.getInt(1));
        }
    }

}
