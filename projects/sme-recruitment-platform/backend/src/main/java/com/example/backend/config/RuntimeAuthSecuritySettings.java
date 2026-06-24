/*
 * 文件速览：
 * 1. 文件职责：维护认证安全开关的运行时状态，并保留配置默认值。
 * 2. 对外入口：isForcePasswordChangeEnabled、updateForcePasswordChangeEnabled、getDefaultForcePasswordChangeEnabled。
 * 3. 关键结构：defaultForcePasswordChangeEnabled、AtomicBoolean forcePasswordChangeEnabled。
 * 4. 阅读建议：先看构造函数中的默认值初始化，再看三个公开访问方法。
 */
package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 认证安全运行时设置。
 * 当前用于承接“临时密码登录后强制修改密码”开关，并允许管理员在运行时调整。
 */
@Component
public class RuntimeAuthSecuritySettings {

    private final boolean defaultForcePasswordChangeEnabled;
    private final AtomicBoolean forcePasswordChangeEnabled;

    public RuntimeAuthSecuritySettings(
            @Value("${app.auth.force-password-change.enabled:false}") boolean defaultForcePasswordChangeEnabled) {
        this.defaultForcePasswordChangeEnabled = defaultForcePasswordChangeEnabled;
        this.forcePasswordChangeEnabled = new AtomicBoolean(defaultForcePasswordChangeEnabled);
    }

    public boolean isForcePasswordChangeEnabled() {
        return forcePasswordChangeEnabled.get();
    }

    public void updateForcePasswordChangeEnabled(boolean enabled) {
        forcePasswordChangeEnabled.set(enabled);
    }

    public boolean getDefaultForcePasswordChangeEnabled() {
        return defaultForcePasswordChangeEnabled;
    }
}
