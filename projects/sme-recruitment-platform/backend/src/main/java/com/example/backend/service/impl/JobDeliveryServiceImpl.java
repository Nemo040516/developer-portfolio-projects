/**
 * 文件速览：
 * 1. 文件职责：实现投递模块核心业务，包括投递提交、列表查询、状态更新与关系校验。
 * 2. 关键升级：商家列表返回前会执行联系方式脱敏、附件授权过期回收与真实投递关系判断。
 * 3. 关键依赖：JobDeliveryMapper、ApplicantPrivacyGuard、JobInfoService、InterviewScheduleService。
 * 4. 阅读建议：先看 getMerchantDeliveries、submit/update 主流程，再看 hasDeliveryRelation 辅助方法。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.JobDelivery;
import com.example.backend.entity.JobInfo;
import com.example.backend.exception.ApiException;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.support.ApplicantPrivacyGuard;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.vo.ApplicantSimpleVO;
import com.example.backend.vo.DeliveryVO;
import com.example.backend.vo.MerchantDeliveryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobDeliveryServiceImpl extends ServiceImpl<JobDeliveryMapper, JobDelivery> implements JobDeliveryService {

    @Autowired
    private ApplicantInfoService applicantInfoService;

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private InterviewScheduleService interviewScheduleService;

    @Autowired
    private ApplicantPrivacyGuard applicantPrivacyGuard;

    @Override
    public boolean submitDelivery(Long userId, Long jobId) {
        // 0. 校验职位是否可投递（招聘中 + 已通过审核）
        JobInfo jobInfo = jobInfoService.getById(jobId);
        if (jobInfo == null || jobInfo.getStatus() == null || jobInfo.getAuditStatus() == null
                || jobInfo.getStatus() != 1 || jobInfo.getAuditStatus() != 1) {
            throw new ApiException(400, "职位已下架或未通过审核，无法投递");
        }

        // 1. 校验是否重复投递
        LambdaQueryWrapper<JobDelivery> query = new LambdaQueryWrapper<>();
        query.eq(JobDelivery::getApplicantId, userId)
             .eq(JobDelivery::getJobId, jobId);
        
        if (this.count(query) > 0) {
            throw new ApiException(400, "您已投递过该职位，请勿重复操作");
        }

        // 2. 创建投递记录
        JobDelivery delivery = new JobDelivery();
        delivery.setApplicantId(userId);
        delivery.setJobId(jobId);
        delivery.setResumeUrl(applicantInfoService.getResumeUrl(userId));
        delivery.setStatus(0); // 0-已投递
        // 初始化创建/更新时间，便于列表排序与追踪
        delivery.setCreateTime(LocalDateTime.now());
        delivery.setUpdateTime(LocalDateTime.now());
        
        return this.save(delivery);
    }

    @Override
    public List<DeliveryVO> getMyDeliveries(Long userId) {
        return baseMapper.selectMyDeliveryList(userId);
    }

    @Override
    public IPage<MerchantDeliveryVO> getMerchantDeliveries(Page<?> page, Long merchantId, Long jobId, Integer status, String degree) {
        IPage<MerchantDeliveryVO> result = baseMapper.selectMerchantDeliveryPage(page, merchantId, jobId, status, degree);
        if (result == null || result.getRecords() == null) {
            return result;
        }
        result.getRecords().forEach(item -> applyApplicantPrivacy(item, merchantId));
        return result;
    }

    @Override
    @Transactional
    public boolean updateDeliveryStatus(Long merchantId, Long deliveryId, Integer status, String feedback,
                                        LocalDateTime interviewTime, String interviewLocation,
                                        String interviewMethod, String interviewRemark) {
        // 面试邀约状态建议至少包含时间（前端已强制填写）
        if (status != null && status == 2 && interviewTime == null) {
            throw new ApiException(400, "面试时间不能为空");
        }
        boolean updated = baseMapper.updateDeliveryStatus(deliveryId, status, feedback, merchantId) > 0;
        if (!updated) {
            return false;
        }
        if (status != null && status == 2) {
            interviewScheduleService.createSchedule(
                    deliveryId,
                    merchantId,
                    interviewTime,
                    interviewLocation,
                    interviewMethod,
                    interviewRemark
            );
        }
        return true;
    }

    @Override
    public IPage<DeliveryVO> getMyDeliveriesPage(Page<?> page, Long userId, Integer status, String keyword, String timeOrder) {
        return baseMapper.selectMyDeliveryPage(page, userId, status, keyword, timeOrder);
    }

    @Override
    public Integer getDeliveryStatus(Long userId, Long jobId) {
        return baseMapper.selectDeliveryStatus(userId, jobId);
    }

    @Override
    public boolean hasDeliveryRelation(Long merchantId, Long applicantId) {
        if (merchantId == null || applicantId == null) {
            return false;
        }
        return baseMapper.countMerchantApplicantRelation(merchantId, applicantId) > 0;
    }

    /**
     * 商家查看投递详情时，需要同时收口联系方式隐私与附件授权有效期。
     */
    private void applyApplicantPrivacy(MerchantDeliveryVO item, Long merchantId) {
        if (item == null || merchantId == null) {
            return;
        }
        ApplicantSimpleVO applicant = item.getApplicant();
        Long applicantId = applicant == null ? null : applicant.getUserId();
        if (applicantId == null) {
            return;
        }
        boolean canViewContact = applicantPrivacyGuard.canMerchantViewContact(merchantId, applicantId);
        if (!canViewContact) {
            applicantPrivacyGuard.maskApplicantContactFields(applicant);
        }
        boolean hasActiveAttachmentAuth = applicantPrivacyGuard.hasActiveAttachmentAuthorization(applicantId, merchantId);
        if (!hasActiveAttachmentAuth) {
            item.setResumeUrl(null);
            if (Integer.valueOf(1).equals(item.getAttachmentStatus())) {
                item.setAttachmentStatus(null);
            }
        }
    }
}
