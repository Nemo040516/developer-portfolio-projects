package com.example.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * API 自动化测试基类。
 * Task 1 阶段先统一测试 Profile、MockMvc 与常用对象入口，
 * 后续 Task 2 起的接口测试统一继承该基类，避免重复配置。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 统一构造鉴权上下文，保证 principal 为数值型字符串，
     * 便于控制器里的 SecurityUtils / SecurityContext 解析用户 ID。
     */
    protected RequestPostProcessor authorizedAs(long userId, String role) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return authentication(authToken);
    }

    /**
     * 统一序列化 JSON，请求体构造保持简洁。
     */
    protected String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected MediaType json() {
        return MediaType.APPLICATION_JSON;
    }
}
