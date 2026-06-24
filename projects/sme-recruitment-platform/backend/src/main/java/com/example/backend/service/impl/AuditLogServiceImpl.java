package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.AuditLog;
import com.example.backend.mapper.AuditLogMapper;
import com.example.backend.service.AuditLogService;
import com.example.backend.utils.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    @Override
    public void record(String module, String action, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setModule(module);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorRole(SecurityUtils.getRole());
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        this.save(log);
    }
}
