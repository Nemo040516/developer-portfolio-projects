/*
 * 文件速览：
 * 1. 文件职责：预留 Spring MVC 自定义配置入口。
 * 2. 关键说明：uploads 目录自 2026-04-21 起不再通过静态资源映射公开直出，统一改由受控控制器输出。
 * 3. 对外入口：当前无自定义 MVC 规则，保留配置类便于后续集中扩展。
 * 4. 阅读建议：本文件目前只承担占位职责，可优先阅读 UploadAccessController。
 */
package com.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 预留 MVC 配置。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
}
