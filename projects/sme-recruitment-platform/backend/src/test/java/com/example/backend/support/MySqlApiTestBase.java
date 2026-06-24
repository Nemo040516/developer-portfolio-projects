package com.example.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 真实 MySQL 口径的 MockMvc 基类。
 * 说明：保留与 H2 基线并存，避免默认 `mvn test` 被真实数据库专项强绑定。
 */
@AutoConfigureMockMvc
@ActiveProfiles("mysql-test")
public abstract class MySqlApiTestBase extends MySqlPersistenceTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected RequestPostProcessor authorizedAs(long userId, String role) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return authentication(authToken);
    }

    protected MediaType json() {
        return MediaType.APPLICATION_JSON;
    }
}
