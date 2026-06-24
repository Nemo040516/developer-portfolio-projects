package com.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.entity.ResumeAttachmentPermission;

import java.time.LocalDateTime;

public interface ResumeAttachmentPermissionService extends IService<ResumeAttachmentPermission> {

    ResumeAttachmentPermission getPermission(Long applicantId, Long merchantId);

    ResumeAttachmentPermission requestPermission(Long applicantId, Long merchantId, LocalDateTime expireTime);

    ResumeAttachmentPermission grantPermission(Long applicantId, Long merchantId, LocalDateTime expireTime);
}
