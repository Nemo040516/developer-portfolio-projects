/*
 * 文件速览：
 * 1. 文件职责：实现面试安排创建、列表查询与状态更新等核心业务逻辑。
 * 2. 对外入口：InterviewScheduleService，由投递模块和面试控制器调用。
 * 3. 关键结构：按投递归属校验商家/求职者权限，并对面试轮次与状态流转做约束。
 * 4. 阅读建议：先看 createSchedule，再看 getScheduleList 与 updateStatus。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.InterviewSchedule;
import com.example.backend.entity.JobDelivery;
import com.example.backend.entity.JobInfo;
import com.example.backend.exception.ApiException;
import com.example.backend.mapper.InterviewScheduleMapper;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.service.JobInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewScheduleServiceImpl
        extends ServiceImpl<InterviewScheduleMapper, InterviewSchedule>
        implements InterviewScheduleService {

    private static final int STATUS_PENDING = 0;
    private static final String METHOD_ONLINE = "线上面试";
    private static final String METHOD_OFFLINE = "线下面试";

    @Autowired
    private JobDeliveryMapper jobDeliveryMapper;

    @Autowired
    private JobInfoService jobInfoService;

    @Override
    public InterviewSchedule createSchedule(Long deliveryId,
                                            Long merchantId,
                                            LocalDateTime scheduleTime,
                                            String location,
                                            String method,
                                            String remark) {
        if (deliveryId == null || merchantId == null) {
            throw new ApiException(400, "缺少投递记录或商家信息");
        }
        if (scheduleTime == null) {
            throw new ApiException(400, "面试时间不能为空");
        }

        Integer maxRound = baseMapper.selectMaxRound(deliveryId);
        int roundNo = (maxRound == null ? 1 : maxRound + 1);

        String normalizedMethod = normalizeMethod(method);
        if (!StringUtils.hasText(normalizedMethod)) {
            throw new ApiException(400, "请明确面试方式（线上/线下）");
        }
        if (METHOD_OFFLINE.equals(normalizedMethod) && !StringUtils.hasText(location)) {
            throw new ApiException(400, "线下面试请填写面试地点");
        }
        if (METHOD_ONLINE.equals(normalizedMethod) && !StringUtils.hasText(location)) {
            throw new ApiException(400, "线上面试请填写会议链接/方式");
        }

        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setDeliveryId(deliveryId);
        schedule.setRoundNo(roundNo);
        schedule.setStatus(STATUS_PENDING);
        schedule.setScheduleTime(scheduleTime);
        schedule.setLocation(StringUtils.hasText(location) ? location : null);
        schedule.setMethod(normalizedMethod);
        schedule.setRemark(StringUtils.hasText(remark) ? remark : null);
        schedule.setCreatorId(merchantId);
        this.save(schedule);
        return schedule;
    }

    @Override
    public List<InterviewSchedule> getScheduleList(Long deliveryId, Long userId, String role) {
        if (deliveryId == null || userId == null) {
            throw new ApiException(400, "缺少必要参数");
        }
        JobDelivery delivery = jobDeliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new ApiException(404, "投递记录不存在");
        }

        boolean permitted = false;
        if ("APPLICANT".equals(role)) {
            permitted = userId.equals(delivery.getApplicantId());
        } else if ("MERCHANT".equals(role)) {
            JobInfo jobInfo = jobInfoService.getById(delivery.getJobId());
            permitted = jobInfo != null && userId.equals(jobInfo.getMerchantId());
        }

        if (!permitted) {
            throw new ApiException(403, "无权限查看该面试安排");
        }

        return baseMapper.selectByDelivery(deliveryId);
    }

    @Override
    public boolean updateStatus(Long scheduleId, Integer status, Long userId, String role) {
        if (scheduleId == null || status == null || userId == null) {
            throw new ApiException(400, "缺少必要参数");
        }
        InterviewSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new ApiException(404, "面试安排不存在");
        }
        JobDelivery delivery = jobDeliveryMapper.selectById(schedule.getDeliveryId());
        if (delivery == null) {
            throw new ApiException(404, "投递记录不存在");
        }

        boolean permitted = false;
        if ("APPLICANT".equals(role)) {
            permitted = userId.equals(delivery.getApplicantId()) && (status == 1 || status == 2);
        } else if ("MERCHANT".equals(role)) {
            JobInfo jobInfo = jobInfoService.getById(delivery.getJobId());
            permitted = jobInfo != null && userId.equals(jobInfo.getMerchantId())
                && (status == 3 || status == 4);
        }

        if (!permitted) {
            throw new ApiException(403, "无权限更新面试状态");
        }

        // 规则：必须按轮次顺序处理，上一轮未确认不得处理下一轮
        if ("APPLICANT".equals(role) && (status == 1 || status == 2)) {
            int pendingBefore = baseMapper.countPendingBefore(schedule.getDeliveryId(), schedule.getRoundNo());
            if (pendingBefore > 0) {
                throw new ApiException(400, "请先确认上一轮面试");
            }
        }

        schedule.setStatus(status);
        return this.updateById(schedule);
    }

    // 统一面试方式存储，保证线上/线下明确可区分
    private String normalizeMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return null;
        }
        if (method.contains("线下")) {
            return METHOD_OFFLINE;
        }
        if (method.contains("线上")) {
            return METHOD_ONLINE;
        }
        return null;
    }
}
