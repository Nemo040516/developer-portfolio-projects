package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.ResumeAttachmentPermission;
import com.example.backend.mapper.ResumeAttachmentPermissionMapper;
import com.example.backend.service.ResumeAttachmentPermissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ResumeAttachmentPermissionServiceImpl
        extends ServiceImpl<ResumeAttachmentPermissionMapper, ResumeAttachmentPermission>
        implements ResumeAttachmentPermissionService {

    private static final String DEFAULT_TYPE = "RESUME";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_GRANTED = 1;
    private static final int STATUS_REJECTED = 2;

    @Override
    public ResumeAttachmentPermission getPermission(Long applicantId, Long merchantId) {
        if (applicantId == null || merchantId == null) {
            return null;
        }
        LambdaQueryWrapper<ResumeAttachmentPermission> query = new LambdaQueryWrapper<>();
        query.eq(ResumeAttachmentPermission::getApplicantId, applicantId)
             .eq(ResumeAttachmentPermission::getMerchantId, merchantId)
             .eq(ResumeAttachmentPermission::getAttachmentType, DEFAULT_TYPE)
             .last("LIMIT 1");
        return this.getOne(query);
    }

    @Override
    public ResumeAttachmentPermission requestPermission(Long applicantId, Long merchantId, LocalDateTime expireTime) {
        ResumeAttachmentPermission existing = getPermission(applicantId, merchantId);
        if (existing == null) {
            ResumeAttachmentPermission record = new ResumeAttachmentPermission();
            record.setApplicantId(applicantId);
            record.setMerchantId(merchantId);
            record.setAttachmentType(DEFAULT_TYPE);
            record.setStatus(STATUS_PENDING);
            record.setExpireTime(expireTime);
            this.save(record);
            return record;
        }

        // 已授权则直接返回，拒绝或待同意则刷新为待同意
        if (existing.getStatus() != null && existing.getStatus() == STATUS_GRANTED) {
            if (expireTime != null) {
                existing.setExpireTime(expireTime);
                this.updateById(existing);
            }
            return existing;
        }

        existing.setStatus(STATUS_PENDING);
        if (expireTime != null) {
            existing.setExpireTime(expireTime);
        }
        this.updateById(existing);
        return existing;
    }

    @Override
    public ResumeAttachmentPermission grantPermission(Long applicantId, Long merchantId, LocalDateTime expireTime) {
        ResumeAttachmentPermission existing = getPermission(applicantId, merchantId);
        if (existing == null) {
            ResumeAttachmentPermission record = new ResumeAttachmentPermission();
            record.setApplicantId(applicantId);
            record.setMerchantId(merchantId);
            record.setAttachmentType(DEFAULT_TYPE);
            record.setStatus(STATUS_GRANTED);
            record.setExpireTime(expireTime);
            this.save(record);
            return record;
        }

        existing.setStatus(STATUS_GRANTED);
        if (expireTime != null) {
            existing.setExpireTime(expireTime);
        }
        this.updateById(existing);
        return existing;
    }
}
