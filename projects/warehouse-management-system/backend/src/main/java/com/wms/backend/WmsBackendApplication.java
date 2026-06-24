/**
 * @file 速览索引
 * @summary 后端应用启动入口，负责引导 Spring Boot 容器启动。
 * @core 1. 声明应用主配置注解 @SpringBootApplication
 * @core 2. 提供标准 main 方法启动上下文
 * @entry 先看：main
 * @deps 关键依赖：SpringApplication、SpringBoot 自动配置
 * @state 关键数据：启动参数 args
 * @risk 高风险修改点：包扫描根路径与启动类位置变更会影响 Bean 装配
 * @link 相关文件：后端/src/main/resources/application.yml、后端/src/test/java/com/wms/backend/M1MasterDataReadinessTest.java
 */
package com.wms.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsBackendApplication.class, args);
    }
}
