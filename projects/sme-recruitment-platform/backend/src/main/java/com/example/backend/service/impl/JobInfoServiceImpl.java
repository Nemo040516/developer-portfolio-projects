/*
 * 文件速览：
 * 1. 文件职责：实现商家职位的新建、编辑、上下架、复审提交与公开查询逻辑。
 * 2. 对外入口：JobInfoService，由职位控制器与后续治理通知联动逻辑调用。
 * 3. 关键结构：唯一性校验、修改摘要生成、审核状态流转、职位整改通知联动。
 * 4. 阅读建议：先看 create/update/resubmitAudit，再看唯一性校验与修改摘要。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.dto.JobPostDTO;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.JobViewLog;
import com.example.backend.exception.ApiException;
import com.example.backend.mapper.JobInfoMapper;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.JobViewLogService;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.JobDetailVO;
import com.example.backend.vo.JobInfoVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements JobInfoService {

    private final AuditLogService auditLogService;

    @Autowired
    private JobViewLogService jobViewLogService;

    @Autowired
    private GovernanceNoticeService governanceNoticeService;

    public JobInfoServiceImpl(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // 辅助方法：处理 tags (兼容 String 和 List)
    private String resolveTags(Object tags) {
        if (tags == null) {
            return null;
        }
        if (tags instanceof String) {
            String s = (String) tags;
            return s.isEmpty() ? null : s;
        }
        if (tags instanceof Iterable) {
            List<String> list = new ArrayList<>();
            for (Object item : (Iterable<?>) tags) {
                if (item != null) {
                    list.add(item.toString());
                }
            }
            return list.isEmpty() ? null : String.join(",", list);
        }
        return tags.toString();
    }

    @Override
    public void createJob(JobPostDTO dto, Long merchantId) {
        // 避免同一岗位重复创建：同一商家 + 同名 + 同城市/区域视为同岗位
        // 需要多人时请调整招聘人数，而不是重复建岗
        ensureUniqueJobTitle(merchantId, dto.getTitle(), dto.getWorkLocation(), dto.getDistrict(), null);

        JobInfo jobInfo = new JobInfo();
        BeanUtils.copyProperties(dto, jobInfo, "tags"); // Ignore tags during copy

        // 手动处理 tags
        jobInfo.setTags(resolveTags(dto.getTags()));

        // 招聘人数兜底（允许前端不传时默认 1）
        if (jobInfo.getHeadcount() == null || jobInfo.getHeadcount() < 1) {
            jobInfo.setHeadcount(1);
        }

        jobInfo.setMerchantId(merchantId);
        jobInfo.setStatus(1); // Default to recruiting
        // 新发布职位进入待审核
        jobInfo.setAuditStatus(0);
        jobInfo.setAuditReason(null);
        jobInfo.setAuditTime(null);
        jobInfo.setLastEditSummary("新建职位");
        jobInfo.setLastEditTime(LocalDateTime.now());
        jobInfo.setCreateTime(LocalDateTime.now());
        jobInfo.setUpdateTime(LocalDateTime.now());
        save(jobInfo);

        auditLogService.record("JOB", "CREATE", jobInfo.getId(), "新建职位");
    }

    @Override
    public void updateJob(JobPostDTO dto, Long merchantId) {
        JobInfo jobInfo = getById(dto.getId());
        if (jobInfo == null) {
            throw new ApiException(404, "职位不存在");
        }
        if (!jobInfo.getMerchantId().equals(merchantId)) {
            throw new ApiException(403, "无权修改该职位");
        }

        // 更新时也避免与其它岗位重复
        ensureUniqueJobTitle(merchantId, dto.getTitle(), dto.getWorkLocation(), dto.getDistrict(), dto.getId());

        // 先拷贝旧数据，用于计算修改摘要
        JobInfo oldJob = new JobInfo();
        BeanUtils.copyProperties(jobInfo, oldJob);
        
        BeanUtils.copyProperties(dto, jobInfo, "tags"); // Ignore tags during copy

        // 手动处理 tags
        jobInfo.setTags(resolveTags(dto.getTags()));

        // 招聘人数兜底
        if (jobInfo.getHeadcount() == null || jobInfo.getHeadcount() < 1) {
            jobInfo.setHeadcount(1);
        }

        String changeSummary = buildJobChangeSummary(oldJob, dto);
        jobInfo.setLastEditSummary(changeSummary);
        jobInfo.setLastEditTime(LocalDateTime.now());

        // 如果职位处于驳回或待审核状态，更新时保持/回到待审核
        // 已通过的职位默认保持通过，避免无谓阻断发布流程
        if (jobInfo.getAuditStatus() == null || jobInfo.getAuditStatus() != 1) {
            jobInfo.setAuditStatus(0);
            jobInfo.setAuditReason(null);
            jobInfo.setAuditTime(null);
        }

        jobInfo.setUpdateTime(LocalDateTime.now());
        updateById(jobInfo);

        auditLogService.record("JOB", "UPDATE", jobInfo.getId(), changeSummary);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // 创建一个只包含 ID 和 Status 的对象，MP 会自动生成只更新 status 的 SQL
        JobInfo job = new JobInfo();
        job.setId(id);
        job.setStatus(status);
        job.setUpdateTime(LocalDateTime.now()); // 顺便更新一下时间
        
        // 生成 SQL: UPDATE job_post SET status=?, update_time=? WHERE id=?
        updateById(job);

        String actionDetail = "更新职位状态为 " + status;
        auditLogService.record("JOB", "STATUS", id, actionDetail);
    }

    @Override
    public IPage<JobInfo> getMerchantJobList(Page<JobInfo> page, Long merchantId, Integer status, Integer auditStatus) {
        // 使用自定义 Mapper 方法进行关联查询
        return baseMapper.selectMerchantJobList(page, merchantId, status, auditStatus);
    }

    @Override
    public IPage<JobInfoVO> getPublicJobList(Page<JobInfoVO> page, String keyword, String degree, String experience, String location,
                                             Long categoryId, Integer minSalary, Integer maxSalary, String city, String district,
                                             String industry, String scale, String financing, String sort,
                                             String smartDegreeCap, String smartExperienceCap, Integer smartMinSalary) {
        return baseMapper.selectPublicList(page, keyword, degree, experience, location,
                categoryId, minSalary, maxSalary, city, district, industry, scale, financing, sort,
                smartDegreeCap, smartExperienceCap, smartMinSalary);
    }

    @Override
    public JobDetailVO getPublicJobDetail(Long id) {
        JobDetailVO detail = baseMapper.selectPublicDetail(id);
        if (detail == null) {
            throw new ApiException(404, "职位不存在或已下架");
        }
        // 浏览量自增（避免影响主查询，这里使用原子更新）
        UpdateWrapper<JobInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .setSql("view_count = IFNULL(view_count, 0) + 1");
        this.update(wrapper);

        // 记录浏览日志（按周期统计用）
        try {
            JobInfo jobInfo = getById(id);
            if (jobInfo != null) {
                JobViewLog log = new JobViewLog();
                log.setJobId(id);
                log.setMerchantId(jobInfo.getMerchantId());
                log.setViewerId(SecurityUtils.getUserId());
                log.setViewTime(LocalDateTime.now());
                jobViewLogService.save(log);
            }
        } catch (Exception e) {
            // 表不存在或写入失败时忽略，不影响详情展示
        }
        return detail;
    }

    @Override
    public void resubmitAudit(Long jobId, Long merchantId) {
        JobInfo jobInfo = getById(jobId);
        if (jobInfo == null) {
            throw new ApiException(404, "职位不存在");
        }
        if (!jobInfo.getMerchantId().equals(merchantId)) {
            throw new ApiException(403, "无权操作该职位");
        }
        jobInfo.setAuditStatus(0);
        jobInfo.setAuditReason(null);
        jobInfo.setAuditTime(null);
        String summary = jobInfo.getLastEditSummary();
        if (summary == null || summary.trim().isEmpty()) {
            summary = "提交复审";
        }
        jobInfo.setLastEditSummary(summary);
        jobInfo.setLastEditTime(LocalDateTime.now());
        jobInfo.setUpdateTime(LocalDateTime.now());
        updateById(jobInfo);

        governanceNoticeService.syncJobRectifyNoticeOnResubmit(jobId, merchantId, summary);

        auditLogService.record("JOB", "RESUBMIT", jobId, "提交复审");
    }

    // 构建职位变更摘要，便于管理员快速识别修改点
    private String buildJobChangeSummary(JobInfo oldJob, JobPostDTO dto) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(oldJob.getTitle(), dto.getTitle())) {
            changes.add("职位名称");
        }
        if (!Objects.equals(oldJob.getCategoryId(), dto.getCategoryId())) {
            changes.add("职位分类");
        }
        if (!Objects.equals(oldJob.getMinSalary(), dto.getMinSalary()) ||
                !Objects.equals(oldJob.getMaxSalary(), dto.getMaxSalary())) {
            changes.add("薪资范围");
        }
        if (!Objects.equals(oldJob.getWorkLocation(), dto.getWorkLocation())) {
            changes.add("工作城市");
        }
        if (!Objects.equals(oldJob.getDistrict(), dto.getDistrict())) {
            changes.add("工作区域");
        }
        if (!Objects.equals(oldJob.getExperience(), dto.getExperience())) {
            changes.add("经验要求");
        }
        if (!Objects.equals(oldJob.getDegree(), dto.getDegree())) {
            changes.add("学历要求");
        }
        String newTags = resolveTags(dto.getTags());
        if (!Objects.equals(oldJob.getTags(), newTags)) {
            changes.add("职位标签");
        }
        if (!Objects.equals(oldJob.getDescription(), dto.getDescription())) {
            changes.add("职位描述");
        }
        if (!Objects.equals(oldJob.getRequirement(), dto.getRequirement())) {
            changes.add("任职要求");
        }
        if (!Objects.equals(oldJob.getHeadcount(), dto.getHeadcount())) {
            changes.add("招聘人数");
        }
        if (changes.isEmpty()) {
            return "未检测到明显修改";
        }
        return String.join("、", changes);
    }

    // 校验同一商家同岗位重复创建
    private void ensureUniqueJobTitle(Long merchantId, String title, String workLocation, String district, Long excludeId) {
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobInfo::getMerchantId, merchantId)
                .eq(JobInfo::getTitle, title)
                .eq(JobInfo::getWorkLocation, workLocation);

        // district 为空时，统一按照空字符串判断，避免 NULL/空字符串混用导致重复
        String normalizedDistrict = (district == null || district.trim().isEmpty()) ? "" : district.trim();
        if (normalizedDistrict.isEmpty()) {
            wrapper.and(q -> q.eq(JobInfo::getDistrict, "").or().isNull(JobInfo::getDistrict));
        } else {
            wrapper.eq(JobInfo::getDistrict, normalizedDistrict);
        }

        // 排除当前编辑项
        if (excludeId != null) {
            wrapper.ne(JobInfo::getId, excludeId);
        }

        // 仅限制非归档岗位，归档后可重新发布
        // 兼容历史空状态数据：NULL 也视为非归档
        wrapper.and(q -> q.ne(JobInfo::getStatus, 2).or().isNull(JobInfo::getStatus));

        if (count(wrapper) > 0) {
            throw new ApiException(400, "同一岗位已存在，请调整招聘人数或修改岗位信息");
        }
    }
}
