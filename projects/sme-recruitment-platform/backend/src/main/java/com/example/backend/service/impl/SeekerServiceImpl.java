package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.JobDelivery;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.service.SeekerService;
import com.example.backend.vo.SeekerDashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SeekerServiceImpl implements SeekerService {

    @Autowired
    private ApplicantInfoMapper applicantInfoMapper;

    @Autowired
    private JobDeliveryMapper jobDeliveryMapper;

    @Override
    public SeekerDashboardVO getDashboardStats(Long userId) {
        SeekerDashboardVO vo = new SeekerDashboardVO();

        // 1. 查询在线简历信息（来自 applicant_profile）
        ApplicantInfo info = applicantInfoMapper.selectOne(new LambdaQueryWrapper<ApplicantInfo>()
                .eq(ApplicantInfo::getUserId, userId));

        if (info != null) {
            vo.setResumeFileName(extractFileName(info.getResumeUrl()));
            vo.setResumeUpdateTime(info.getUpdateTime());
            vo.setResumeCompleteness(calcCompleteness(info));
        } else {
            vo.setResumeCompleteness(0);
            vo.setResumeUpdateTime(null);
        }

        // 2. 查询投递统计（job_apply）
        List<JobDelivery> deliveries = jobDeliveryMapper.selectList(new LambdaQueryWrapper<JobDelivery>()
                .eq(JobDelivery::getApplicantId, userId));

        Map<Integer, Long> statusCount = deliveries.stream()
                .collect(Collectors.groupingBy(d -> d.getStatus() == null ? 0 : d.getStatus(), Collectors.counting()));

        // 状态: 0-已投递, 1-已初筛, 2-面试邀约, 3-不合适
        vo.setAppliedCount(statusCount.getOrDefault(0, 0L).intValue());
        vo.setViewedCount(statusCount.getOrDefault(1, 0L).intValue());
        vo.setInterviewCount(statusCount.getOrDefault(2, 0L).intValue());
        vo.setRejectedCount(statusCount.getOrDefault(3, 0L).intValue());

        return vo;
    }

    @Override
    public Map<String, Object> getInsightStats(Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 统计过去 7 天每天的简历活跃次数 (以投递记录状态更新为准)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(6).withHour(0).withMinute(0).withSecond(0);
        
        List<JobDelivery> recentActivities = jobDeliveryMapper.selectList(new LambdaQueryWrapper<JobDelivery>()
                .eq(JobDelivery::getApplicantId, userId)
                .ge(JobDelivery::getUpdateTime, sevenDaysAgo));
        
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, Long> dayCountMap = recentActivities.stream()
                .collect(Collectors.groupingBy(d -> d.getUpdateTime().format(df), Collectors.counting()));
        
        List<Integer> chartData = new ArrayList<>();
        List<String> days = new ArrayList<>();
        
        for (int i = 6; i >= 0; i--) {
            String dateKey = now.minusDays(i).format(df);
            days.add(dateKey);
            chartData.add(dayCountMap.getOrDefault(dateKey, 0L).intValue());
        }
        
        result.put("chartData", chartData); // 每天的活跃次数 [0, 2, 1, 0, ...]
        result.put("days", days);
        result.put("totalViewed", recentActivities.size());
        result.put("trend", "+5%"); 
        
        return result;
    }

    /**
     * 计算简历完善度（0-100）
     * 依据核心字段是否填写进行粗略评估。
     */
    private int calcCompleteness(ApplicantInfo info) {
        if (info == null) {
            return 0;
        }
        int total = 17;
        int filled = 0;
        if (StringUtils.hasText(info.getRealName())) filled++;
        if (StringUtils.hasText(info.getPhone())) filled++;
        if (StringUtils.hasText(info.getEmail())) filled++;
        if (StringUtils.hasText(info.getCurrentIdentity())) filled++;
        if (StringUtils.hasText(info.getWorkYears())) filled++;
        if (StringUtils.hasText(info.getCurrentStatus())) filled++;
        if (StringUtils.hasText(info.getExpectCity())) filled++;
        if (StringUtils.hasText(info.getExpectSalary())) filled++;
        if (StringUtils.hasText(info.getExpectJob())) filled++;
        if (StringUtils.hasText(info.getSkills())) filled++;
        if (hasJsonContent(info.getEducationJson())) filled++;
        if (hasJsonContent(info.getExperienceJson())) filled++;
        if (hasJsonContent(info.getProjectJson())) filled++;
        if (hasJsonContent(info.getCertificateJson())) filled++;
        if (hasJsonContent(info.getAwardJson())) filled++;
        if (hasJsonContent(info.getLanguageJson())) filled++;
        if (StringUtils.hasText(info.getAdvantage())) filled++;
        // 防止 total 过小导致溢出
        int safeTotal = Math.max(total, 1);
        int percent = (int) Math.round(filled * 100.0 / safeTotal);
        return Math.min(100, Math.max(0, percent));
    }

    /**
     * 从附件简历 URL 中提取文件名
     */
    private String extractFileName(String resumeUrl) {
        if (!StringUtils.hasText(resumeUrl)) {
            return null;
        }
        String normalized = resumeUrl.replace("\\", "/");
        int idx = normalized.lastIndexOf("/");
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    /**
     * 判断 JSON 字段是否包含有效内容
     */
    private boolean hasJsonContent(String json) {
        if (!StringUtils.hasText(json)) {
            return false;
        }
        String trimmed = json.trim();
        return !(trimmed.equals("[]") || trimmed.equals("{}") || trimmed.equals("null"));
    }
}
