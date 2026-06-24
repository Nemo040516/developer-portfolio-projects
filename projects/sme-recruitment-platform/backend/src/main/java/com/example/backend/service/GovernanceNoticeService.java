/*
 * 文件速览：
 * 1. 文件职责：声明平台治理通知模块的服务能力边界，作为 Phase 1 的后端实现草案。
 * 2. 对外入口：供后续管理员控制器、商家/求职者通知中心控制器调用。
 * 3. 关键结构：管理员创建/复核、用户按状态/阶段查询、已读/提交动作，以及职位/商家整改、举报结果、封禁联动方法。
 * 4. 阅读建议：先看用户查询方法，再看各类业务同步方法。
 */
package com.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.dto.AdminGovernanceNoticeCreateDTO;
import com.example.backend.dto.AdminGovernanceNoticeReviewDTO;
import com.example.backend.dto.GovernanceNoticeActionDTO;
import com.example.backend.entity.ReportInfo;
import com.example.backend.vo.AdminGovernanceNoticeVO;
import com.example.backend.vo.GovernanceNoticeVO;

import java.time.LocalDateTime;

/**
 * 平台治理通知服务接口
 */
public interface GovernanceNoticeService {

    /**
     * 管理员查询治理事项列表
     */
    IPage<AdminGovernanceNoticeVO> getAdminNoticePage(Page<?> page,
                                                      String targetRole,
                                                      String noticeType,
                                                      String status,
                                                      String sourceModule,
                                                      Boolean overdueOnly);

    /**
     * 管理员查询治理事项详情
     */
    AdminGovernanceNoticeVO getAdminNoticeDetail(Long noticeId);

    /**
     * 管理员创建治理通知 / 整改单
     */
    Long createNotice(AdminGovernanceNoticeCreateDTO dto, Long adminUserId);

    /**
     * 管理员复核治理事项
     */
    void reviewNotice(Long noticeId, AdminGovernanceNoticeReviewDTO dto, Long adminUserId);

    /**
     * 用户查询自己的治理事项列表
     */
    IPage<GovernanceNoticeVO> getMyNoticePage(Page<?> page, Long userId, String status, String noticeType, String stage);

    /**
     * 用户查询自己的治理事项详情
     */
    GovernanceNoticeVO getMyNoticeDetail(Long noticeId, Long userId);

    /**
     * 用户标记已读
     */
    void markRead(Long noticeId, Long userId, String userRole);

    /**
     * 用户提交整改说明 / 补充说明 / 申诉
     */
    void submitAction(Long noticeId, Long userId, String userRole, GovernanceNoticeActionDTO dto, boolean restrictedMode);

    /**
     * 职位驳回时创建或刷新职位整改通知
     */
    void syncJobRectifyNoticeOnReject(Long jobId, Long merchantUserId, String rejectReason, Long adminUserId);

    /**
     * 职位审核通过时完成对应整改通知
     */
    void syncJobRectifyNoticeOnApprove(Long jobId, Long merchantUserId, Long adminUserId);

    /**
     * 商家提交职位复审时，将整改通知推进为待管理员复核
     */
    void syncJobRectifyNoticeOnResubmit(Long jobId, Long merchantUserId, String submitSummary);

    /**
     * 商家资料驳回时创建或刷新商家整改通知
     */
    void syncMerchantRectifyNoticeOnReject(Long merchantProfileId, Long merchantUserId, String rejectReason, Long adminUserId);

    /**
     * 商家资料审核通过时完成对应整改通知
     */
    void syncMerchantRectifyNoticeOnApprove(Long merchantProfileId, Long merchantUserId, Long adminUserId);

    /**
     * 商家修改企业资料并重新提交后，将商家整改通知推进为待管理员复核
     */
    void syncMerchantRectifyNoticeOnResubmit(Long merchantProfileId, Long merchantUserId, String submitSummary);

    /**
     * 举报处理完成后同步举报结果通知与被处理对象通知
     */
    void syncReportNoticesOnHandle(ReportInfo reportInfo, String actionCode, Long adminUserId);

    /**
     * 账号风控封禁 / 解封时同步封禁通知
     */
    void syncUserBanNotice(Long targetUserId,
                           Integer banStatus,
                           LocalDateTime banUntil,
                           String banReason,
                           Long adminUserId);
}
