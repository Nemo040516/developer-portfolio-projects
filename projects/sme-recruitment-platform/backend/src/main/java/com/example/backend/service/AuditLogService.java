package com.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.entity.AuditLog;

public interface AuditLogService extends IService<AuditLog> {
    // 记录敏感操作日志
    void record(String module, String action, Long targetId, String detail);
}
