/*
 * 文件速览：
 * 1. 文件职责：实现平台治理通知模块的最小后端闭环，覆盖列表、创建、详情、已读、提交动作、管理员复核与多业务联动。
 * 2. 对外入口：GovernanceNoticeService，由后续管理员端与用户端治理通知控制器调用。
 * 3. 关键结构：治理事项分页查询（含阶段筛选）、动作时间线、状态流转、职位/商家整改、举报结果、用户警告/封禁联动。
 * 4. 阅读建议：先看 getMyNoticePage 的阶段筛选，再看 create/review/markRead/submitAction 和 sync*Notice。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.common.GovernanceNoticeConstants;
import com.example.backend.dto.AdminGovernanceNoticeCreateDTO;
import com.example.backend.dto.AdminGovernanceNoticeReviewDTO;
import com.example.backend.dto.GovernanceNoticeActionDTO;
import com.example.backend.exception.ApiException;
import com.example.backend.entity.GovernanceNotice;
import com.example.backend.entity.GovernanceNoticeAction;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.ReportInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.mapper.JobInfoMapper;
import com.example.backend.mapper.GovernanceNoticeMapper;
import com.example.backend.mapper.GovernanceNoticeActionMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.vo.AdminGovernanceNoticeVO;
import com.example.backend.vo.GovernanceNoticeActionVO;
import com.example.backend.vo.GovernanceNoticeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GovernanceNoticeServiceImpl extends ServiceImpl<GovernanceNoticeMapper, GovernanceNotice>
        implements GovernanceNoticeService {

    @Autowired
    private GovernanceNoticeActionMapper governanceNoticeActionMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JobInfoMapper jobInfoMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Override
    public IPage<AdminGovernanceNoticeVO> getAdminNoticePage(Page<?> page,
                                                             String targetRole,
                                                             String noticeType,
                                                             String status,
                                                             String sourceModule,
                                                             Boolean overdueOnly) {
        LambdaQueryWrapper<GovernanceNotice> query = buildAdminQuery(targetRole, noticeType, status, sourceModule, overdueOnly);
        IPage<GovernanceNotice> rawPage = this.page(createNoticeQueryPage(page), query);
        List<GovernanceNotice> records = rawPage.getRecords();

        Map<Long, String> userNameMap = buildUserNameMap(collectUserIds(records));
        Map<Long, String> jobTitleMap = buildJobTitleMap(collectJobIds(records));
        return mapNoticePage(rawPage, item -> toAdminVO(item, userNameMap, jobTitleMap, null));
    }

    @Override
    public AdminGovernanceNoticeVO getAdminNoticeDetail(Long noticeId) {
        GovernanceNotice notice = getNoticeOrThrow(noticeId);
        Map<Long, String> userNameMap = buildUserNameMap(collectUserIds(List.of(notice)));
        Map<Long, String> jobTitleMap = buildJobTitleMap(collectJobIds(List.of(notice)));
        List<GovernanceNoticeActionVO> actions = buildActionTimeline(noticeId);
        return toAdminVO(notice, userNameMap, jobTitleMap, actions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotice(AdminGovernanceNoticeCreateDTO dto, Long adminUserId) {
        if (adminUserId == null) {
            throw new ApiException(401, "管理员身份无效");
        }

        GovernanceNotice notice = new GovernanceNotice();
        LocalDateTime now = LocalDateTime.now();
        notice.setNoticeNo(generateNoticeNo());
        notice.setTargetRole(dto.getTargetRole());
        notice.setTargetUserId(dto.getTargetUserId());
        notice.setNoticeType(dto.getNoticeType());
        notice.setSeverity(dto.getSeverity());
        notice.setSourceModule(dto.getSourceModule());
        notice.setSourceId(dto.getSourceId());
        notice.setRelatedJobId(dto.getRelatedJobId());
        notice.setRelatedMerchantId(resolveRelatedMerchantId(dto));
        notice.setTitle(dto.getTitle());
        notice.setSummary(buildSummary(dto.getSummary(), dto.getDetail()));
        notice.setDetail(dto.getDetail());
        notice.setRequiredAction(dto.getRequiredAction());
        notice.setDueTime(dto.getDueTime());
        notice.setStatus(GovernanceNoticeConstants.Status.PENDING_READ);
        notice.setNeedAck(defaultFlag(dto.getNeedAck(), 1));
        notice.setNeedReply(defaultFlag(dto.getNeedReply(), 0));
        notice.setCreatedBy(adminUserId);
        notice.setLatestActionTime(now);
        notice.setIsDeleted(0);
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        this.save(notice);

        auditLogService.record(
                "GOVERNANCE",
                "CREATE",
                notice.getId(),
                "创建治理事项 noticeType=" + notice.getNoticeType()
                        + "，targetUserId=" + notice.getTargetUserId()
                        + "，source=" + notice.getSourceModule() + ":" + notice.getSourceId()
        );
        return notice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewNotice(Long noticeId, AdminGovernanceNoticeReviewDTO dto, Long adminUserId) {
        GovernanceNotice notice = getNoticeOrThrow(noticeId);
        LocalDateTime now = LocalDateTime.now();
        String reviewStatus = normalizeValue(dto.getReviewStatus());

        if (GovernanceNoticeConstants.ActionType.APPROVE.equals(reviewStatus)) {
            notice.setStatus(GovernanceNoticeConstants.Status.FINISHED);
            notice.setClosedTime(now);
        } else if (GovernanceNoticeConstants.ActionType.REJECT.equals(reviewStatus)) {
            notice.setStatus(GovernanceNoticeConstants.Status.REJECTED);
            notice.setClosedTime(null);
        } else if (GovernanceNoticeConstants.ActionType.CLOSE.equals(reviewStatus)) {
            notice.setStatus(GovernanceNoticeConstants.Status.CLOSED);
            notice.setClosedTime(now);
        } else {
            throw new ApiException(400, "复核状态不合法");
        }

        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
        this.updateById(notice);

        saveAction(noticeId,
                "ADMIN",
                adminUserId,
                reviewStatus,
                dto.getReviewComment(),
                null,
                null);

        auditLogService.record(
                "GOVERNANCE",
                "REVIEW",
                noticeId,
                "治理事项复核 action=" + reviewStatus + "，comment=" + safeText(dto.getReviewComment())
        );
    }

    @Override
    public IPage<GovernanceNoticeVO> getMyNoticePage(Page<?> page, Long userId, String status, String noticeType, String stage) {
        LambdaQueryWrapper<GovernanceNotice> query = new LambdaQueryWrapper<GovernanceNotice>()
                .eq(GovernanceNotice::getTargetUserId, userId)
                .eq(GovernanceNotice::getIsDeleted, 0)
                .orderByDesc(GovernanceNotice::getLatestActionTime)
                .orderByDesc(GovernanceNotice::getCreateTime);

        if (StringUtils.hasText(status)) {
            query.eq(GovernanceNotice::getStatus, normalizeValue(status));
        } else if (StringUtils.hasText(stage)) {
            applyUserStageFilter(query, stage);
        }
        if (StringUtils.hasText(noticeType)) {
            query.eq(GovernanceNotice::getNoticeType, normalizeValue(noticeType));
        }

        IPage<GovernanceNotice> rawPage = this.page(createNoticeQueryPage(page), query);
        Map<Long, String> jobTitleMap = buildJobTitleMap(collectJobIds(rawPage.getRecords()));
        return mapNoticePage(rawPage, item -> toUserVO(item, jobTitleMap, null));
    }

    /**
     * 用户侧阶段筛选会把多个底层状态折叠成一个工作流视图，避免前端自己拼接状态集合。
     */
    private void applyUserStageFilter(LambdaQueryWrapper<GovernanceNotice> query, String stage) {
        String normalizedStage = normalizeValue(stage);
        if ("READ".equals(normalizedStage)) {
            query.eq(GovernanceNotice::getStatus, GovernanceNoticeConstants.Status.PENDING_READ);
            return;
        }
        if ("ACTION".equals(normalizedStage)) {
            query.in(
                    GovernanceNotice::getStatus,
                    GovernanceNoticeConstants.Status.PENDING_ACTION,
                    GovernanceNoticeConstants.Status.REJECTED,
                    GovernanceNoticeConstants.Status.EXPIRED
            );
            return;
        }
        if ("REVIEW".equals(normalizedStage)) {
            query.eq(GovernanceNotice::getStatus, GovernanceNoticeConstants.Status.PENDING_REVIEW);
        }
    }

    @Override
    public GovernanceNoticeVO getMyNoticeDetail(Long noticeId, Long userId) {
        GovernanceNotice notice = getNoticeOrThrow(noticeId);
        assertOwnership(notice, userId);
        Map<Long, String> jobTitleMap = buildJobTitleMap(collectJobIds(List.of(notice)));
        List<GovernanceNoticeActionVO> actions = buildActionTimeline(noticeId);
        return toUserVO(notice, jobTitleMap, actions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long noticeId, Long userId, String userRole) {
        GovernanceNotice notice = getNoticeOrThrow(noticeId);
        assertOwnership(notice, userId);
        if (notice.getReadTime() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        notice.setReadTime(now);
        if (GovernanceNoticeConstants.Status.PENDING_READ.equals(notice.getStatus())) {
            if (Integer.valueOf(1).equals(notice.getNeedReply())) {
                notice.setStatus(GovernanceNoticeConstants.Status.PENDING_ACTION);
                notice.setClosedTime(null);
            } else {
                notice.setStatus(GovernanceNoticeConstants.Status.FINISHED);
                notice.setClosedTime(now);
            }
        }
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
        this.updateById(notice);

        saveAction(noticeId, normalizeValue(userRole), userId, GovernanceNoticeConstants.ActionType.READ, "用户已读", null, null);
        auditLogService.record("GOVERNANCE", "READ", noticeId, "用户已读治理事项");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAction(Long noticeId, Long userId, String userRole, GovernanceNoticeActionDTO dto, boolean restrictedMode) {
        GovernanceNotice notice = getNoticeOrThrow(noticeId);
        assertOwnership(notice, userId);
        String actionType = normalizeValue(dto.getActionType());
        LocalDateTime now = LocalDateTime.now();

        if (!StringUtils.hasText(actionType)) {
            throw new ApiException(400, "动作类型不能为空");
        }
        if (restrictedMode) {
            validateRestrictedAction(notice, actionType);
        }

        if (notice.getReadTime() == null) {
            notice.setReadTime(now);
        }

        if (GovernanceNoticeConstants.ActionType.SUBMIT_FIX.equals(actionType)
                || GovernanceNoticeConstants.ActionType.APPEAL.equals(actionType)) {
            notice.setStatus(GovernanceNoticeConstants.Status.PENDING_REVIEW);
        } else if (GovernanceNoticeConstants.ActionType.REPLY.equals(actionType)
                && GovernanceNoticeConstants.Status.PENDING_READ.equals(notice.getStatus())) {
            notice.setStatus(GovernanceNoticeConstants.Status.PENDING_ACTION);
        }

        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
        this.updateById(notice);

        saveAction(noticeId, normalizeValue(userRole), userId, actionType, dto.getContent(), dto.getAttachmentJson(), null);
        auditLogService.record(
                "GOVERNANCE",
                "ACTION",
                noticeId,
                "用户提交治理动作 action=" + actionType + "，content=" + safeText(dto.getContent())
        );
    }

    /**
     * 受限模式下只允许对封禁通知发起申诉，其余写操作继续禁止。
     */
    private void validateRestrictedAction(GovernanceNotice notice, String actionType) {
        if (!GovernanceNoticeConstants.ActionType.APPEAL.equals(actionType)) {
            throw new ApiException(403, "当前账号受限，仅允许提交封禁申诉");
        }
        if (!GovernanceNoticeConstants.NoticeType.BAN_NOTICE.equals(notice.getNoticeType())) {
            throw new ApiException(403, "当前账号受限，仅允许对封禁通知发起申诉");
        }
        if (!canAppeal(notice)) {
            throw new ApiException(400, "当前封禁通知暂不可申诉");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncJobRectifyNoticeOnReject(Long jobId, Long merchantUserId, String rejectReason, Long adminUserId) {
        JobInfo jobInfo = getJobOrThrow(jobId);
        Long targetMerchantId = merchantUserId != null ? merchantUserId : jobInfo.getMerchantId();
        LocalDateTime now = LocalDateTime.now();
        GovernanceNotice notice = findLatestJobRectifyNotice(jobId, targetMerchantId);

        if (notice == null) {
            notice = new GovernanceNotice();
            notice.setNoticeNo(generateNoticeNo());
            notice.setTargetRole(GovernanceNoticeConstants.TargetRole.MERCHANT);
            notice.setTargetUserId(targetMerchantId);
            notice.setNoticeType(GovernanceNoticeConstants.NoticeType.JOB_RECTIFY);
            notice.setSeverity(GovernanceNoticeConstants.Severity.WARNING);
            notice.setSourceModule(GovernanceNoticeConstants.SourceModule.JOB_AUDIT);
            notice.setSourceId(jobId);
            notice.setRelatedJobId(jobId);
            notice.setRelatedMerchantId(jobInfo.getMerchantId());
            notice.setNeedAck(1);
            notice.setNeedReply(1);
            notice.setCreatedBy(adminUserId);
            notice.setIsDeleted(0);
            notice.setCreateTime(now);
        }

        notice.setTitle(buildJobRectifyTitle(jobInfo));
        notice.setSummary(buildJobRectifySummary(rejectReason));
        notice.setDetail(buildJobRectifyDetail(jobInfo, rejectReason));
        notice.setRequiredAction("请根据驳回原因修改职位信息，并在完成后提交复审。");
        notice.setDueTime(now.plusDays(3));
        notice.setStatus(GovernanceNoticeConstants.Status.PENDING_READ);
        notice.setReadTime(null);
        notice.setClosedTime(null);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);

        if (notice.getId() == null) {
            this.save(notice);
        } else {
            this.updateById(notice);
        }

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.REJECT,
                StringUtils.hasText(rejectReason) ? rejectReason.trim() : "职位审核未通过，请按要求修改后重新提交。",
                null,
                buildSimpleExtraJson("jobId", jobId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "JOB_RECTIFY_REJECT",
                notice.getId(),
                "职位驳回联动整改通知 jobId=" + jobId + "，merchantId=" + targetMerchantId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncJobRectifyNoticeOnApprove(Long jobId, Long merchantUserId, Long adminUserId) {
        GovernanceNotice notice = findLatestJobRectifyNotice(jobId, merchantUserId);
        if (notice == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        notice.setStatus(GovernanceNoticeConstants.Status.FINISHED);
        notice.setClosedTime(now);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
        this.updateById(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.APPROVE,
                "职位已审核通过，整改事项完成。",
                null,
                buildSimpleExtraJson("jobId", jobId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "JOB_RECTIFY_APPROVE",
                notice.getId(),
                "职位通过审核，整改事项完成 jobId=" + jobId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncJobRectifyNoticeOnResubmit(Long jobId, Long merchantUserId, String submitSummary) {
        GovernanceNotice notice = findLatestJobRectifyNotice(jobId, merchantUserId);
        if (notice == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (notice.getReadTime() == null) {
            notice.setReadTime(now);
        }
        notice.setStatus(GovernanceNoticeConstants.Status.PENDING_REVIEW);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
        this.updateById(notice);

        saveAction(
                notice.getId(),
                "MERCHANT",
                merchantUserId,
                GovernanceNoticeConstants.ActionType.SUBMIT_FIX,
                StringUtils.hasText(submitSummary) ? submitSummary.trim() : "商家已修改职位并提交复审。",
                null,
                buildSimpleExtraJson("jobId", jobId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "JOB_RECTIFY_RESUBMIT",
                notice.getId(),
                "商家提交职位复审，整改事项转待复核 jobId=" + jobId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncMerchantRectifyNoticeOnReject(Long merchantProfileId,
                                                  Long merchantUserId,
                                                  String rejectReason,
                                                  Long adminUserId) {
        MerchantInfo merchantInfo = getMerchantOrThrow(merchantProfileId);
        Long targetMerchantUserId = merchantUserId != null ? merchantUserId : merchantInfo.getUserId();
        LocalDateTime now = LocalDateTime.now();
        GovernanceNotice notice = findLatestMerchantRectifyNotice(merchantProfileId, targetMerchantUserId);

        if (notice == null) {
            notice = createBaseNotice(
                    GovernanceNoticeConstants.TargetRole.MERCHANT,
                    targetMerchantUserId,
                    GovernanceNoticeConstants.NoticeType.MERCHANT_RECTIFY,
                    GovernanceNoticeConstants.Severity.WARNING,
                    GovernanceNoticeConstants.SourceModule.MERCHANT_AUDIT,
                    merchantProfileId,
                    null,
                    targetMerchantUserId,
                    adminUserId,
                    now
            );
            notice.setNeedAck(1);
            notice.setNeedReply(1);
        }

        notice.setTitle(buildMerchantRectifyTitle(merchantInfo));
        notice.setSummary(buildMerchantRectifySummary(rejectReason));
        notice.setDetail(buildMerchantRectifyDetail(merchantInfo, rejectReason));
        notice.setRequiredAction("请根据驳回原因补充或修正企业资料，并重新提交审核。");
        notice.setDueTime(now.plusDays(3));
        refreshPendingNotice(notice, now);
        saveOrUpdateNotice(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.REJECT,
                StringUtils.hasText(rejectReason) ? rejectReason.trim() : "企业资料审核未通过，请按要求修改后重新提交。",
                null,
                buildSimpleExtraJson("merchantProfileId", merchantProfileId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "MERCHANT_RECTIFY_REJECT",
                notice.getId(),
                "商家资料驳回联动整改通知 merchantProfileId=" + merchantProfileId + "，merchantUserId=" + targetMerchantUserId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncMerchantRectifyNoticeOnApprove(Long merchantProfileId, Long merchantUserId, Long adminUserId) {
        GovernanceNotice notice = findLatestMerchantRectifyNotice(merchantProfileId, merchantUserId);
        if (notice == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        finishNotice(notice, now);
        this.updateById(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.APPROVE,
                "企业资料已审核通过，整改事项完成。",
                null,
                buildSimpleExtraJson("merchantProfileId", merchantProfileId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "MERCHANT_RECTIFY_APPROVE",
                notice.getId(),
                "商家资料审核通过，整改事项完成 merchantProfileId=" + merchantProfileId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncMerchantRectifyNoticeOnResubmit(Long merchantProfileId, Long merchantUserId, String submitSummary) {
        GovernanceNotice notice = findLatestMerchantRectifyNotice(merchantProfileId, merchantUserId);
        if (notice == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (notice.getReadTime() == null) {
            notice.setReadTime(now);
        }
        notice.setStatus(GovernanceNoticeConstants.Status.PENDING_REVIEW);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
        this.updateById(notice);

        saveAction(
                notice.getId(),
                "MERCHANT",
                merchantUserId,
                GovernanceNoticeConstants.ActionType.SUBMIT_FIX,
                StringUtils.hasText(submitSummary) ? submitSummary.trim() : "商家已更新企业资料并重新提交审核。",
                null,
                buildSimpleExtraJson("merchantProfileId", merchantProfileId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "MERCHANT_RECTIFY_RESUBMIT",
                notice.getId(),
                "商家提交企业资料复审，整改事项转待复核 merchantProfileId=" + merchantProfileId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncReportNoticesOnHandle(ReportInfo reportInfo, String actionCode, Long adminUserId) {
        if (reportInfo == null || reportInfo.getId() == null || reportInfo.getStatus() == null) {
            return;
        }
        syncReporterReportResultNotice(reportInfo, actionCode, adminUserId);
        if (Integer.valueOf(1).equals(reportInfo.getStatus())) {
            syncTargetNoticeByReportAction(reportInfo, actionCode, adminUserId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUserBanNotice(Long targetUserId,
                                  Integer banStatus,
                                  LocalDateTime banUntil,
                                  String banReason,
                                  Long adminUserId) {
        if (targetUserId == null || banStatus == null) {
            return;
        }

        GovernanceNotice notice = findLatestRiskBanNotice(targetUserId);
        LocalDateTime now = LocalDateTime.now();

        if (banStatus == 0) {
            if (notice == null) {
                return;
            }
            closeNotice(notice, now);
            this.updateById(notice);
            saveAction(
                    notice.getId(),
                    "ADMIN",
                    adminUserId,
                    GovernanceNoticeConstants.ActionType.CLOSE,
                    "账号风控限制已解除。",
                    null,
                    buildSimpleExtraJson("userId", targetUserId)
            );
            auditLogService.record(
                    "GOVERNANCE",
                    "BAN_NOTICE_CLOSE",
                    notice.getId(),
                    "账号风控解除，关闭封禁通知 userId=" + targetUserId
            );
            return;
        }

        SysUser user = getUserOrThrow(targetUserId);
        if (notice == null) {
            notice = createBaseNotice(
                    resolveTargetRole(user),
                    targetUserId,
                    GovernanceNoticeConstants.NoticeType.BAN_NOTICE,
                    GovernanceNoticeConstants.Severity.HIGH,
                    GovernanceNoticeConstants.SourceModule.RISK_CONTROL,
                    targetUserId,
                    null,
                    null,
                    adminUserId,
                    now
            );
            notice.setNeedAck(1);
            notice.setNeedReply(1);
        }

        notice.setSeverity(banStatus == 2
                ? GovernanceNoticeConstants.Severity.HIGH
                : GovernanceNoticeConstants.Severity.WARNING);
        notice.setTitle(buildRiskBanTitle(user, banStatus));
        notice.setSummary(buildRiskBanSummary(banStatus, banReason, banUntil));
        notice.setDetail(buildRiskBanDetail(user, banStatus, banReason, banUntil));
        notice.setRequiredAction("如对本次处理有异议，可在平台提醒中提交申诉说明。");
        notice.setDueTime(now.plusDays(7));
        refreshPendingNotice(notice, now);
        saveOrUpdateNotice(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.REJECT,
                buildRiskBanActionContent(banStatus, banReason, banUntil),
                null,
                buildSimpleExtraJson("userId", targetUserId)
        );

        auditLogService.record(
                "GOVERNANCE",
                "BAN_NOTICE_SYNC",
                notice.getId(),
                "账号风控联动封禁通知 userId=" + targetUserId + "，banStatus=" + banStatus
        );
    }

    private LambdaQueryWrapper<GovernanceNotice> buildAdminQuery(String targetRole,
                                                                 String noticeType,
                                                                 String status,
                                                                 String sourceModule,
                                                                 Boolean overdueOnly) {
        LambdaQueryWrapper<GovernanceNotice> query = new LambdaQueryWrapper<GovernanceNotice>()
                .eq(GovernanceNotice::getIsDeleted, 0)
                .orderByDesc(GovernanceNotice::getLatestActionTime)
                .orderByDesc(GovernanceNotice::getCreateTime);

        if (StringUtils.hasText(targetRole)) {
            query.eq(GovernanceNotice::getTargetRole, normalizeValue(targetRole));
        }
        if (StringUtils.hasText(noticeType)) {
            query.eq(GovernanceNotice::getNoticeType, normalizeValue(noticeType));
        }
        if (StringUtils.hasText(status)) {
            query.eq(GovernanceNotice::getStatus, normalizeValue(status));
        }
        if (StringUtils.hasText(sourceModule)) {
            query.eq(GovernanceNotice::getSourceModule, normalizeValue(sourceModule));
        }
        if (Boolean.TRUE.equals(overdueOnly)) {
            query.isNotNull(GovernanceNotice::getDueTime)
                    .lt(GovernanceNotice::getDueTime, LocalDateTime.now())
                    .notIn(GovernanceNotice::getStatus,
                            GovernanceNoticeConstants.Status.FINISHED,
                            GovernanceNoticeConstants.Status.CLOSED,
                            GovernanceNoticeConstants.Status.EXPIRED);
        }
        return query;
    }

    private void syncReporterReportResultNotice(ReportInfo reportInfo, String actionCode, Long adminUserId) {
        if (reportInfo.getReporterId() == null) {
            return;
        }

        SysUser reporter = sysUserMapper.selectById(reportInfo.getReporterId());
        if (reporter == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        GovernanceNotice notice = findLatestNoticeBySource(
                reportInfo.getReporterId(),
                GovernanceNoticeConstants.NoticeType.REPORT_RESULT,
                GovernanceNoticeConstants.SourceModule.REPORT,
                reportInfo.getId()
        );

        if (notice == null) {
            notice = createBaseNotice(
                    resolveTargetRole(reporter),
                    reportInfo.getReporterId(),
                    GovernanceNoticeConstants.NoticeType.REPORT_RESULT,
                    GovernanceNoticeConstants.Severity.INFO,
                    GovernanceNoticeConstants.SourceModule.REPORT,
                    reportInfo.getId(),
                    resolveRelatedJobId(reportInfo),
                    resolveRelatedMerchantUserId(reportInfo),
                    adminUserId,
                    now
            );
            notice.setNeedAck(1);
            notice.setNeedReply(0);
        }

        notice.setSeverity(GovernanceNoticeConstants.Severity.INFO);
        notice.setRelatedJobId(resolveRelatedJobId(reportInfo));
        notice.setRelatedMerchantId(resolveRelatedMerchantUserId(reportInfo));
        notice.setTitle(buildReporterReportResultTitle(reportInfo));
        notice.setSummary(buildReporterReportResultSummary(reportInfo, actionCode));
        notice.setDetail(buildReporterReportResultDetail(reportInfo, actionCode));
        notice.setRequiredAction(buildReporterReportResultRequiredAction(reportInfo));
        notice.setDueTime(null);
        refreshPendingNotice(notice, now);
        saveOrUpdateNotice(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                Integer.valueOf(1).equals(reportInfo.getStatus())
                        ? GovernanceNoticeConstants.ActionType.APPROVE
                        : GovernanceNoticeConstants.ActionType.REJECT,
                buildReporterReportResultSummary(reportInfo, actionCode),
                null,
                buildReportExtraJson(reportInfo, actionCode)
        );

        auditLogService.record(
                "GOVERNANCE",
                "REPORT_RESULT_SYNC",
                notice.getId(),
                "举报结果通知已同步给举报人 reportId=" + reportInfo.getId() + "，reporterId=" + reportInfo.getReporterId()
        );
    }

    private void syncTargetNoticeByReportAction(ReportInfo reportInfo, String actionCode, Long adminUserId) {
        if (!StringUtils.hasText(actionCode)) {
            return;
        }
        String reportType = normalizeValue(reportInfo.getType());
        if ("JOB".equals(reportType)) {
            syncJobReportTargetNotice(reportInfo, actionCode, adminUserId);
            return;
        }
        if ("MERCHANT".equals(reportType)) {
            syncMerchantReportTargetNotice(reportInfo, actionCode, adminUserId);
            return;
        }
        if ("USER".equals(reportType)) {
            syncUserReportTargetNotice(reportInfo, actionCode, adminUserId);
        }
    }

    private void syncJobReportTargetNotice(ReportInfo reportInfo, String actionCode, Long adminUserId) {
        JobInfo jobInfo = jobInfoMapper.selectById(reportInfo.getTargetId());
        if (jobInfo == null || jobInfo.getMerchantId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        GovernanceNotice notice = findLatestNoticeBySource(
                jobInfo.getMerchantId(),
                GovernanceNoticeConstants.NoticeType.JOB_RECTIFY,
                GovernanceNoticeConstants.SourceModule.REPORT,
                reportInfo.getId()
        );

        if (notice == null) {
            notice = createBaseNotice(
                    GovernanceNoticeConstants.TargetRole.MERCHANT,
                    jobInfo.getMerchantId(),
                    GovernanceNoticeConstants.NoticeType.JOB_RECTIFY,
                    resolveJobReportSeverity(actionCode),
                    GovernanceNoticeConstants.SourceModule.REPORT,
                    reportInfo.getId(),
                    jobInfo.getId(),
                    jobInfo.getMerchantId(),
                    adminUserId,
                    now
            );
            notice.setNeedAck(1);
            notice.setNeedReply(1);
        }

        notice.setSeverity(resolveJobReportSeverity(actionCode));
        notice.setRelatedJobId(jobInfo.getId());
        notice.setRelatedMerchantId(jobInfo.getMerchantId());
        notice.setTitle(buildJobReportNoticeTitle(jobInfo, actionCode));
        notice.setSummary(buildJobReportNoticeSummary(reportInfo, actionCode));
        notice.setDetail(buildJobReportNoticeDetail(jobInfo, reportInfo, actionCode));
        notice.setRequiredAction(buildJobReportRequiredAction(actionCode));
        notice.setDueTime(now.plusDays(3));
        refreshPendingNotice(notice, now);
        saveOrUpdateNotice(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.REJECT,
                buildJobReportNoticeSummary(reportInfo, actionCode),
                null,
                buildReportExtraJson(reportInfo, actionCode)
        );

        auditLogService.record(
                "GOVERNANCE",
                "REPORT_JOB_TARGET_SYNC",
                notice.getId(),
                "举报处理结果已同步给商家职位整改通知 reportId=" + reportInfo.getId() + "，jobId=" + jobInfo.getId()
        );
    }

    private void syncMerchantReportTargetNotice(ReportInfo reportInfo, String actionCode, Long adminUserId) {
        MerchantInfo merchantInfo = findMerchantByTargetId(reportInfo.getTargetId());
        if (merchantInfo == null || merchantInfo.getUserId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        GovernanceNotice notice = findLatestNoticeBySource(
                merchantInfo.getUserId(),
                GovernanceNoticeConstants.NoticeType.MERCHANT_RECTIFY,
                GovernanceNoticeConstants.SourceModule.REPORT,
                reportInfo.getId()
        );

        if (notice == null) {
            notice = createBaseNotice(
                    GovernanceNoticeConstants.TargetRole.MERCHANT,
                    merchantInfo.getUserId(),
                    GovernanceNoticeConstants.NoticeType.MERCHANT_RECTIFY,
                    resolveMerchantReportSeverity(actionCode),
                    GovernanceNoticeConstants.SourceModule.REPORT,
                    reportInfo.getId(),
                    null,
                    merchantInfo.getUserId(),
                    adminUserId,
                    now
            );
            notice.setNeedAck(1);
            notice.setNeedReply(1);
        }

        notice.setSeverity(resolveMerchantReportSeverity(actionCode));
        notice.setRelatedMerchantId(merchantInfo.getUserId());
        notice.setTitle(buildMerchantReportNoticeTitle(merchantInfo, actionCode));
        notice.setSummary(buildMerchantReportNoticeSummary(reportInfo, actionCode));
        notice.setDetail(buildMerchantReportNoticeDetail(merchantInfo, reportInfo, actionCode));
        notice.setRequiredAction(buildMerchantReportRequiredAction(actionCode));
        notice.setDueTime(now.plusDays(5));
        refreshPendingNotice(notice, now);
        saveOrUpdateNotice(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.REJECT,
                buildMerchantReportNoticeSummary(reportInfo, actionCode),
                null,
                buildReportExtraJson(reportInfo, actionCode)
        );

        auditLogService.record(
                "GOVERNANCE",
                "REPORT_MERCHANT_TARGET_SYNC",
                notice.getId(),
                "举报处理结果已同步给商家资料整改通知 reportId=" + reportInfo.getId() + "，merchantUserId=" + merchantInfo.getUserId()
        );
    }

    private void syncUserReportTargetNotice(ReportInfo reportInfo, String actionCode, Long adminUserId) {
        SysUser targetUser = sysUserMapper.selectById(reportInfo.getTargetId());
        if (targetUser == null) {
            return;
        }

        String noticeType = "USER_WARN".equals(actionCode)
                ? GovernanceNoticeConstants.NoticeType.USER_WARNING
                : GovernanceNoticeConstants.NoticeType.BAN_NOTICE;
        LocalDateTime now = LocalDateTime.now();
        GovernanceNotice notice = findLatestNoticeBySource(
                targetUser.getId(),
                noticeType,
                GovernanceNoticeConstants.SourceModule.REPORT,
                reportInfo.getId()
        );

        if (notice == null) {
            notice = createBaseNotice(
                    resolveTargetRole(targetUser),
                    targetUser.getId(),
                    noticeType,
                    resolveUserReportSeverity(actionCode),
                    GovernanceNoticeConstants.SourceModule.REPORT,
                    reportInfo.getId(),
                    null,
                    "MERCHANT".equalsIgnoreCase(targetUser.getRole()) ? targetUser.getId() : null,
                    adminUserId,
                    now
            );
            notice.setNeedAck(1);
            notice.setNeedReply(1);
        }

        notice.setSeverity(resolveUserReportSeverity(actionCode));
        notice.setTitle(buildUserReportNoticeTitle(targetUser, actionCode));
        notice.setSummary(buildUserReportNoticeSummary(reportInfo, actionCode));
        notice.setDetail(buildUserReportNoticeDetail(targetUser, reportInfo, actionCode));
        notice.setRequiredAction(buildUserReportRequiredAction(actionCode));
        notice.setDueTime(now.plusDays(7));
        refreshPendingNotice(notice, now);
        saveOrUpdateNotice(notice);

        saveAction(
                notice.getId(),
                "ADMIN",
                adminUserId,
                GovernanceNoticeConstants.ActionType.REJECT,
                buildUserReportNoticeSummary(reportInfo, actionCode),
                null,
                buildReportExtraJson(reportInfo, actionCode)
        );

        auditLogService.record(
                "GOVERNANCE",
                "REPORT_USER_TARGET_SYNC",
                notice.getId(),
                "举报处理结果已同步给被处理用户 reportId=" + reportInfo.getId() + "，targetUserId=" + targetUser.getId()
        );
    }

    private GovernanceNotice createBaseNotice(String targetRole,
                                              Long targetUserId,
                                              String noticeType,
                                              String severity,
                                              String sourceModule,
                                              Long sourceId,
                                              Long relatedJobId,
                                              Long relatedMerchantId,
                                              Long adminUserId,
                                              LocalDateTime now) {
        GovernanceNotice notice = new GovernanceNotice();
        notice.setNoticeNo(generateNoticeNo());
        notice.setTargetRole(targetRole);
        notice.setTargetUserId(targetUserId);
        notice.setNoticeType(noticeType);
        notice.setSeverity(severity);
        notice.setSourceModule(sourceModule);
        notice.setSourceId(sourceId);
        notice.setRelatedJobId(relatedJobId);
        notice.setRelatedMerchantId(relatedMerchantId);
        notice.setCreatedBy(adminUserId);
        notice.setLatestActionTime(now);
        notice.setIsDeleted(0);
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        return notice;
    }

    private void refreshPendingNotice(GovernanceNotice notice, LocalDateTime now) {
        notice.setStatus(GovernanceNoticeConstants.Status.PENDING_READ);
        notice.setReadTime(null);
        notice.setClosedTime(null);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
    }

    private void finishNotice(GovernanceNotice notice, LocalDateTime now) {
        notice.setStatus(GovernanceNoticeConstants.Status.FINISHED);
        notice.setClosedTime(now);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
    }

    private void closeNotice(GovernanceNotice notice, LocalDateTime now) {
        notice.setStatus(GovernanceNoticeConstants.Status.CLOSED);
        notice.setClosedTime(now);
        notice.setLatestActionTime(now);
        notice.setUpdateTime(now);
    }

    private void saveOrUpdateNotice(GovernanceNotice notice) {
        if (notice.getId() == null) {
            this.save(notice);
        } else {
            this.updateById(notice);
        }
    }

    private GovernanceNotice findLatestNoticeBySource(Long targetUserId,
                                                      String noticeType,
                                                      String sourceModule,
                                                      Long sourceId) {
        LambdaQueryWrapper<GovernanceNotice> query = new LambdaQueryWrapper<GovernanceNotice>()
                .eq(GovernanceNotice::getTargetUserId, targetUserId)
                .eq(GovernanceNotice::getNoticeType, noticeType)
                .eq(GovernanceNotice::getSourceModule, sourceModule)
                .eq(GovernanceNotice::getSourceId, sourceId)
                .eq(GovernanceNotice::getIsDeleted, 0)
                .orderByDesc(GovernanceNotice::getUpdateTime)
                .last("LIMIT 1");
        return this.getOne(query, false);
    }

    private MerchantInfo getMerchantOrThrow(Long merchantProfileId) {
        MerchantInfo merchantInfo = merchantInfoMapper.selectById(merchantProfileId);
        if (merchantInfo == null) {
            throw new ApiException(404, "关联商家不存在");
        }
        return merchantInfo;
    }

    private MerchantInfo findMerchantByTargetId(Long targetId) {
        if (targetId == null) {
            return null;
        }
        MerchantInfo merchantInfo = merchantInfoMapper.selectById(targetId);
        if (merchantInfo != null) {
            return merchantInfo;
        }
        return merchantInfoMapper.selectOne(new LambdaQueryWrapper<MerchantInfo>()
                .eq(MerchantInfo::getUserId, targetId)
                .last("LIMIT 1"));
    }

    private SysUser getUserOrThrow(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(404, "关联用户不存在");
        }
        return user;
    }

    private String resolveTargetRole(SysUser user) {
        if (user != null && "MERCHANT".equalsIgnoreCase(user.getRole())) {
            return GovernanceNoticeConstants.TargetRole.MERCHANT;
        }
        return GovernanceNoticeConstants.TargetRole.APPLICANT;
    }

    private Long resolveRelatedJobId(ReportInfo reportInfo) {
        if (reportInfo == null || reportInfo.getTargetId() == null) {
            return null;
        }
        return "JOB".equalsIgnoreCase(reportInfo.getType()) ? reportInfo.getTargetId() : null;
    }

    private Long resolveRelatedMerchantUserId(ReportInfo reportInfo) {
        if (reportInfo == null || reportInfo.getTargetId() == null) {
            return null;
        }
        if ("JOB".equalsIgnoreCase(reportInfo.getType())) {
            JobInfo jobInfo = jobInfoMapper.selectById(reportInfo.getTargetId());
            return jobInfo != null ? jobInfo.getMerchantId() : null;
        }
        if ("MERCHANT".equalsIgnoreCase(reportInfo.getType())) {
            MerchantInfo merchantInfo = findMerchantByTargetId(reportInfo.getTargetId());
            return merchantInfo != null ? merchantInfo.getUserId() : null;
        }
        return null;
    }

    private GovernanceNotice getNoticeOrThrow(Long noticeId) {
        GovernanceNotice notice = this.getById(noticeId);
        if (notice == null || Integer.valueOf(1).equals(notice.getIsDeleted())) {
            throw new ApiException(404, "治理事项不存在");
        }
        return notice;
    }

    private void assertOwnership(GovernanceNotice notice, Long userId) {
        if (notice == null || userId == null || !Objects.equals(notice.getTargetUserId(), userId)) {
            throw new ApiException(403, "无权访问该治理事项");
        }
    }

    private Integer defaultFlag(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String buildSummary(String summary, String detail) {
        if (StringUtils.hasText(summary)) {
            return summary.trim();
        }
        String safeDetail = safeText(detail);
        if (safeDetail.length() <= 120) {
            return safeDetail;
        }
        return safeDetail.substring(0, 120) + "...";
    }

    private Long resolveRelatedMerchantId(AdminGovernanceNoticeCreateDTO dto) {
        if (dto.getRelatedMerchantId() != null) {
            MerchantInfo merchantInfo = getMerchantOrThrow(dto.getRelatedMerchantId());
            if (merchantInfo.getUserId() == null) {
                throw new ApiException(400, "关联商家缺少用户账号绑定");
            }
            return merchantInfo.getUserId();
        }
        if (dto.getRelatedJobId() == null) {
            return null;
        }
        JobInfo jobInfo = jobInfoMapper.selectById(dto.getRelatedJobId());
        return jobInfo != null ? jobInfo.getMerchantId() : null;
    }

    private String generateNoticeNo() {
        return "GN"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(100, 1000));
    }

    private Set<Long> collectUserIds(Collection<GovernanceNotice> notices) {
        Set<Long> ids = new HashSet<>();
        for (GovernanceNotice notice : notices) {
            if (notice.getTargetUserId() != null) {
                ids.add(notice.getTargetUserId());
            }
            if (notice.getCreatedBy() != null) {
                ids.add(notice.getCreatedBy());
            }
        }
        return ids;
    }

    private Set<Long> collectJobIds(Collection<GovernanceNotice> notices) {
        Set<Long> ids = new HashSet<>();
        for (GovernanceNotice notice : notices) {
            if (notice.getRelatedJobId() != null) {
                ids.add(notice.getRelatedJobId());
            }
        }
        return ids;
    }

    private Map<Long, String> buildUserNameMap(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return result;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        for (SysUser user : users) {
            result.put(user.getId(), resolveUserName(user));
        }
        return result;
    }

    private Map<Long, String> buildJobTitleMap(Collection<Long> jobIds) {
        Map<Long, String> result = new HashMap<>();
        if (CollectionUtils.isEmpty(jobIds)) {
            return result;
        }
        List<JobInfo> jobs = jobInfoMapper.selectBatchIds(jobIds);
        for (JobInfo job : jobs) {
            result.put(job.getId(), job.getTitle());
        }
        return result;
    }

    /**
     * 统一创建治理事项查询分页对象，避免重复手写 current/size 拷贝。
     */
    private Page<GovernanceNotice> createNoticeQueryPage(Page<?> page) {
        return new Page<>(page.getCurrent(), page.getSize());
    }

    /**
     * 统一保留分页元数据并转换记录，避免不同查询重复创建结果分页壳。
     */
    private <T> Page<T> mapNoticePage(IPage<GovernanceNotice> rawPage, Function<GovernanceNotice, T> mapper) {
        Page<T> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream()
                .map(mapper)
                .collect(Collectors.toList()));
        return result;
    }

    private String resolveUserName(SysUser user) {
        if (user == null) {
            return "未知用户";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return "未知用户";
    }

    private AdminGovernanceNoticeVO toAdminVO(GovernanceNotice notice,
                                              Map<Long, String> userNameMap,
                                              Map<Long, String> jobTitleMap,
                                              List<GovernanceNoticeActionVO> actions) {
        AdminGovernanceNoticeVO vo = new AdminGovernanceNoticeVO();
        vo.setId(notice.getId());
        vo.setNoticeNo(notice.getNoticeNo());
        vo.setTargetRole(notice.getTargetRole());
        vo.setTargetUserId(notice.getTargetUserId());
        vo.setTargetUserName(userNameMap.getOrDefault(notice.getTargetUserId(), "未知用户"));
        vo.setNoticeType(notice.getNoticeType());
        vo.setSeverity(notice.getSeverity());
        vo.setSourceModule(notice.getSourceModule());
        vo.setSourceId(notice.getSourceId());
        vo.setRelatedJobId(notice.getRelatedJobId());
        vo.setRelatedJobTitle(jobTitleMap.getOrDefault(notice.getRelatedJobId(), ""));
        vo.setRelatedMerchantId(notice.getRelatedMerchantId());
        vo.setTitle(notice.getTitle());
        vo.setSummary(notice.getSummary());
        vo.setDetail(notice.getDetail());
        vo.setRequiredAction(notice.getRequiredAction());
        vo.setStatus(notice.getStatus());
        vo.setNeedAck(notice.getNeedAck());
        vo.setNeedReply(notice.getNeedReply());
        vo.setCreatedBy(notice.getCreatedBy());
        vo.setCreatedByName(userNameMap.getOrDefault(notice.getCreatedBy(), "未知管理员"));
        vo.setDueTime(notice.getDueTime());
        vo.setReadTime(notice.getReadTime());
        vo.setClosedTime(notice.getClosedTime());
        vo.setLatestActionTime(notice.getLatestActionTime());
        vo.setCreateTime(notice.getCreateTime());
        vo.setOverdue(isOverdue(notice));
        vo.setActions(actions);
        return vo;
    }

    private GovernanceNoticeVO toUserVO(GovernanceNotice notice,
                                        Map<Long, String> jobTitleMap,
                                        List<GovernanceNoticeActionVO> actions) {
        GovernanceNoticeVO vo = new GovernanceNoticeVO();
        vo.setId(notice.getId());
        vo.setNoticeNo(notice.getNoticeNo());
        vo.setNoticeType(notice.getNoticeType());
        vo.setSeverity(notice.getSeverity());
        vo.setSourceModule(notice.getSourceModule());
        vo.setSourceId(notice.getSourceId());
        vo.setRelatedJobId(notice.getRelatedJobId());
        vo.setRelatedJobTitle(jobTitleMap.getOrDefault(notice.getRelatedJobId(), ""));
        vo.setTitle(notice.getTitle());
        vo.setSummary(notice.getSummary());
        vo.setDetail(notice.getDetail());
        vo.setRequiredAction(notice.getRequiredAction());
        vo.setStatus(notice.getStatus());
        vo.setNeedAck(notice.getNeedAck());
        vo.setNeedReply(notice.getNeedReply());
        vo.setCanAcknowledge(notice.getReadTime() == null && Integer.valueOf(1).equals(notice.getNeedAck()));
        vo.setCanReply(canReply(notice));
        vo.setCanAppeal(canAppeal(notice));
        vo.setActions(actions);
        vo.setDueTime(notice.getDueTime());
        vo.setReadTime(notice.getReadTime());
        vo.setLatestActionTime(notice.getLatestActionTime());
        vo.setCreateTime(notice.getCreateTime());
        return vo;
    }

    private List<GovernanceNoticeActionVO> buildActionTimeline(Long noticeId) {
        List<GovernanceNoticeAction> actions = governanceNoticeActionMapper.selectList(
                new LambdaQueryWrapper<GovernanceNoticeAction>()
                        .eq(GovernanceNoticeAction::getNoticeId, noticeId)
                        .orderByAsc(GovernanceNoticeAction::getCreateTime)
                        .orderByAsc(GovernanceNoticeAction::getId)
        );
        if (CollectionUtils.isEmpty(actions)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = actions.stream()
                .map(GovernanceNoticeAction::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = buildUserNameMap(userIds);

        return actions.stream()
                .map(item -> toActionVO(item, userNameMap))
                .collect(Collectors.toList());
    }

    private GovernanceNoticeActionVO toActionVO(GovernanceNoticeAction action, Map<Long, String> userNameMap) {
        GovernanceNoticeActionVO vo = new GovernanceNoticeActionVO();
        vo.setId(action.getId());
        vo.setNoticeId(action.getNoticeId());
        vo.setActorRole(action.getActorRole());
        vo.setActorUserId(action.getActorUserId());
        vo.setActorName(userNameMap.getOrDefault(action.getActorUserId(), "未知用户"));
        vo.setActionType(action.getActionType());
        vo.setContent(action.getContent());
        vo.setAttachmentJson(action.getAttachmentJson());
        vo.setExtraJson(action.getExtraJson());
        vo.setCreateTime(action.getCreateTime());
        return vo;
    }

    private boolean canReply(GovernanceNotice notice) {
        if (!Integer.valueOf(1).equals(notice.getNeedReply())) {
            return false;
        }
        return GovernanceNoticeConstants.Status.PENDING_ACTION.equals(notice.getStatus())
                || GovernanceNoticeConstants.Status.REJECTED.equals(notice.getStatus());
    }

    private boolean canAppeal(GovernanceNotice notice) {
        if (!Integer.valueOf(1).equals(notice.getNeedReply())) {
            return false;
        }
        if (GovernanceNoticeConstants.Status.FINISHED.equals(notice.getStatus())
                || GovernanceNoticeConstants.Status.CLOSED.equals(notice.getStatus())
                || GovernanceNoticeConstants.Status.EXPIRED.equals(notice.getStatus())) {
            return false;
        }
        return GovernanceNoticeConstants.NoticeType.USER_WARNING.equals(notice.getNoticeType())
                || GovernanceNoticeConstants.NoticeType.BAN_NOTICE.equals(notice.getNoticeType());
    }

    private boolean isOverdue(GovernanceNotice notice) {
        if (notice.getDueTime() == null) {
            return false;
        }
        if (GovernanceNoticeConstants.Status.FINISHED.equals(notice.getStatus())
                || GovernanceNoticeConstants.Status.CLOSED.equals(notice.getStatus())
                || GovernanceNoticeConstants.Status.EXPIRED.equals(notice.getStatus())) {
            return false;
        }
        return notice.getDueTime().isBefore(LocalDateTime.now());
    }

    private void saveAction(Long noticeId,
                            String actorRole,
                            Long actorUserId,
                            String actionType,
                            String content,
                            String attachmentJson,
                            String extraJson) {
        GovernanceNoticeAction action = new GovernanceNoticeAction();
        action.setNoticeId(noticeId);
        action.setActorRole(normalizeValue(actorRole));
        action.setActorUserId(actorUserId);
        action.setActionType(normalizeValue(actionType));
        action.setContent(content);
        action.setAttachmentJson(attachmentJson);
        action.setExtraJson(extraJson);
        action.setCreateTime(LocalDateTime.now());
        governanceNoticeActionMapper.insert(action);
    }

    private String normalizeValue(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private GovernanceNotice findLatestMerchantRectifyNotice(Long merchantProfileId, Long merchantUserId) {
        LambdaQueryWrapper<GovernanceNotice> query = new LambdaQueryWrapper<GovernanceNotice>()
                .eq(GovernanceNotice::getNoticeType, GovernanceNoticeConstants.NoticeType.MERCHANT_RECTIFY)
                .eq(GovernanceNotice::getSourceModule, GovernanceNoticeConstants.SourceModule.MERCHANT_AUDIT)
                .eq(GovernanceNotice::getSourceId, merchantProfileId)
                .eq(GovernanceNotice::getIsDeleted, 0)
                .notIn(GovernanceNotice::getStatus,
                        GovernanceNoticeConstants.Status.FINISHED,
                        GovernanceNoticeConstants.Status.CLOSED)
                .orderByDesc(GovernanceNotice::getUpdateTime)
                .last("LIMIT 1");
        if (merchantUserId != null) {
            query.eq(GovernanceNotice::getTargetUserId, merchantUserId);
        }
        return this.getOne(query, false);
    }

    private GovernanceNotice findLatestRiskBanNotice(Long targetUserId) {
        return this.getOne(new LambdaQueryWrapper<GovernanceNotice>()
                .eq(GovernanceNotice::getTargetUserId, targetUserId)
                .eq(GovernanceNotice::getNoticeType, GovernanceNoticeConstants.NoticeType.BAN_NOTICE)
                .eq(GovernanceNotice::getSourceModule, GovernanceNoticeConstants.SourceModule.RISK_CONTROL)
                .eq(GovernanceNotice::getIsDeleted, 0)
                .notIn(GovernanceNotice::getStatus,
                        GovernanceNoticeConstants.Status.FINISHED,
                        GovernanceNoticeConstants.Status.CLOSED)
                .orderByDesc(GovernanceNotice::getUpdateTime)
                .last("LIMIT 1"), false);
    }

    private JobInfo getJobOrThrow(Long jobId) {
        JobInfo jobInfo = jobInfoMapper.selectById(jobId);
        if (jobInfo == null) {
            throw new ApiException(404, "关联职位不存在");
        }
        return jobInfo;
    }

    private GovernanceNotice findLatestJobRectifyNotice(Long jobId, Long merchantUserId) {
        return this.getOne(new LambdaQueryWrapper<GovernanceNotice>()
                .eq(GovernanceNotice::getTargetUserId, merchantUserId)
                .eq(GovernanceNotice::getNoticeType, GovernanceNoticeConstants.NoticeType.JOB_RECTIFY)
                .eq(GovernanceNotice::getSourceModule, GovernanceNoticeConstants.SourceModule.JOB_AUDIT)
                .eq(GovernanceNotice::getRelatedJobId, jobId)
                .eq(GovernanceNotice::getIsDeleted, 0)
                .notIn(GovernanceNotice::getStatus,
                        GovernanceNoticeConstants.Status.FINISHED,
                        GovernanceNoticeConstants.Status.CLOSED)
                .orderByDesc(GovernanceNotice::getUpdateTime)
                .last("LIMIT 1"), false);
    }

    private String buildJobRectifyTitle(JobInfo jobInfo) {
        String title = jobInfo != null ? safeText(jobInfo.getTitle()) : "";
        if (!StringUtils.hasText(title)) {
            return "职位需修改后重新提交";
        }
        return "职位《" + title + "》需修改后重新提交";
    }

    private String buildJobRectifySummary(String rejectReason) {
        String safeReason = safeText(rejectReason);
        if (!StringUtils.hasText(safeReason)) {
            return "职位审核未通过，请根据平台要求修改后重新提交。";
        }
        if (safeReason.length() <= 120) {
            return "驳回原因：" + safeReason;
        }
        return "驳回原因：" + safeReason.substring(0, 120) + "...";
    }

    private String buildJobRectifyDetail(JobInfo jobInfo, String rejectReason) {
        StringBuilder detail = new StringBuilder();
        detail.append("该职位未通过管理员审核。");
        if (jobInfo != null && StringUtils.hasText(jobInfo.getTitle())) {
            detail.append("关联职位：").append(jobInfo.getTitle()).append("。");
        }
        if (StringUtils.hasText(rejectReason)) {
            detail.append("驳回原因：").append(rejectReason.trim()).append("。");
        } else {
            detail.append("请检查职位信息完整性、真实性与规范性，并修改后重新提交。");
        }
        detail.append("完成修改后，请在职位管理中提交复审。");
        return detail.toString();
    }

    private String buildMerchantRectifyTitle(MerchantInfo merchantInfo) {
        String companyName = merchantInfo != null ? safeText(merchantInfo.getCompanyName()) : "";
        if (!StringUtils.hasText(companyName)) {
            return "企业资料需修改后重新提交";
        }
        return "企业《" + companyName + "》资料需修改后重新提交";
    }

    private String buildMerchantRectifySummary(String rejectReason) {
        String safeReason = safeText(rejectReason);
        if (!StringUtils.hasText(safeReason)) {
            return "企业资料审核未通过，请根据平台要求补充或修正后重新提交。";
        }
        if (safeReason.length() <= 120) {
            return "驳回原因：" + safeReason;
        }
        return "驳回原因：" + safeReason.substring(0, 120) + "...";
    }

    private String buildMerchantRectifyDetail(MerchantInfo merchantInfo, String rejectReason) {
        StringBuilder detail = new StringBuilder();
        detail.append("企业资料未通过管理员审核。");
        if (merchantInfo != null && StringUtils.hasText(merchantInfo.getCompanyName())) {
            detail.append("关联企业：").append(merchantInfo.getCompanyName()).append("。");
        }
        if (StringUtils.hasText(rejectReason)) {
            detail.append("驳回原因：").append(rejectReason.trim()).append("。");
        } else {
            detail.append("请检查企业资质、联系人信息与营业信息的真实性、完整性，并修改后重新提交。");
        }
        detail.append("完成修改后，请在企业信息管理中重新提交审核。");
        return detail.toString();
    }

    private String resolveJobReportSeverity(String actionCode) {
        return "JOB_WARN".equals(actionCode)
                ? GovernanceNoticeConstants.Severity.WARNING
                : GovernanceNoticeConstants.Severity.HIGH;
    }

    private String resolveMerchantReportSeverity(String actionCode) {
        return "MERCHANT_WARN".equals(actionCode)
                ? GovernanceNoticeConstants.Severity.WARNING
                : GovernanceNoticeConstants.Severity.HIGH;
    }

    private String resolveUserReportSeverity(String actionCode) {
        return "USER_WARN".equals(actionCode)
                ? GovernanceNoticeConstants.Severity.WARNING
                : GovernanceNoticeConstants.Severity.HIGH;
    }

    private String buildReporterReportResultTitle(ReportInfo reportInfo) {
        if (Integer.valueOf(1).equals(reportInfo.getStatus())) {
            return "你提交的举报已处理";
        }
        return "你提交的举报未通过";
    }

    private String buildReporterReportResultSummary(ReportInfo reportInfo, String actionCode) {
        if (Integer.valueOf(1).equals(reportInfo.getStatus())) {
            String actionText = describeReportAction(actionCode);
            if (StringUtils.hasText(actionText)) {
                return "平台已完成核查，处理结果：" + actionText + "。";
            }
            return "平台已完成核查并处理你提交的举报。";
        }
        return "平台暂未采纳本次举报，如有更多证据可重新提交。";
    }

    private String buildReporterReportResultDetail(ReportInfo reportInfo, String actionCode) {
        StringBuilder detail = new StringBuilder();
        detail.append("举报类型：").append(describeReportType(reportInfo.getType())).append("。");
        if (StringUtils.hasText(reportInfo.getReason())) {
            detail.append("举报原因：").append(reportInfo.getReason().trim()).append("。");
        }
        if (Integer.valueOf(1).equals(reportInfo.getStatus())) {
            detail.append("平台已完成核查");
            if (StringUtils.hasText(actionCode)) {
                detail.append("，处理动作：").append(describeReportAction(actionCode));
            }
            detail.append("。");
        } else {
            detail.append("平台暂未采纳本次举报。");
        }
        if (StringUtils.hasText(reportInfo.getResult())) {
            detail.append("处理说明：").append(reportInfo.getResult().trim()).append("。");
        }
        return detail.toString();
    }

    private String buildReporterReportResultRequiredAction(ReportInfo reportInfo) {
        if (Integer.valueOf(1).equals(reportInfo.getStatus())) {
            return "当前举报已处理完成，如后续发现新的违规证据，可再次提交举报。";
        }
        return "如有更充分的证据或补充说明，可重新提交举报。";
    }

    private String buildJobReportNoticeTitle(JobInfo jobInfo, String actionCode) {
        String jobTitle = jobInfo != null ? safeText(jobInfo.getTitle()) : "";
        String subject = StringUtils.hasText(jobTitle) ? "职位《" + jobTitle + "》" : "该职位";
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "JOB_WARN" -> subject + "收到平台警告";
            case "JOB_OFFLINE_LIMIT_MERCHANT" -> subject + "已下架，并限制企业发布";
            case "JOB_OFFLINE_BAN_MERCHANT" -> subject + "已下架，并暂停企业发布";
            default -> subject + "已被平台下架";
        };
    }

    private String buildJobReportNoticeSummary(ReportInfo reportInfo, String actionCode) {
        if (StringUtils.hasText(reportInfo.getResult())) {
            return buildSummary(reportInfo.getResult(), reportInfo.getResult());
        }
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "JOB_WARN" -> "平台核查后已对该职位发出警告，请尽快自查并修正。";
            case "JOB_OFFLINE_LIMIT_MERCHANT" -> "平台核查后已下架职位，并限制当前企业发布。";
            case "JOB_OFFLINE_BAN_MERCHANT" -> "平台核查后已下架职位，并暂停当前企业发布权限。";
            default -> "平台核查后已下架该职位，请根据要求整改。";
        };
    }

    private String buildJobReportNoticeDetail(JobInfo jobInfo, ReportInfo reportInfo, String actionCode) {
        StringBuilder detail = new StringBuilder();
        detail.append("平台核查举报后，确认该职位存在违规风险。");
        if (jobInfo != null && StringUtils.hasText(jobInfo.getTitle())) {
            detail.append("关联职位：").append(jobInfo.getTitle()).append("。");
        }
        detail.append("处理结果：").append(describeReportAction(actionCode)).append("。");
        if (StringUtils.hasText(reportInfo.getResult())) {
            detail.append("处理说明：").append(reportInfo.getResult().trim()).append("。");
        }
        detail.append("请尽快根据平台要求完成整改。");
        return detail.toString();
    }

    private String buildJobReportRequiredAction(String actionCode) {
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "JOB_WARN" -> "请立即自查并修正职位内容，必要时可提交补充说明。";
            case "JOB_OFFLINE_LIMIT_MERCHANT", "JOB_OFFLINE_BAN_MERCHANT" ->
                    "请先整改职位与企业资料，如需恢复发布权限可提交说明或申诉。";
            default -> "请修改职位信息后重新发布，如有异议可提交说明。";
        };
    }

    private String buildMerchantReportNoticeTitle(MerchantInfo merchantInfo, String actionCode) {
        String companyName = merchantInfo != null ? safeText(merchantInfo.getCompanyName()) : "";
        String subject = StringUtils.hasText(companyName) ? "企业《" + companyName + "》" : "当前企业";
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "MERCHANT_WARN" -> subject + "收到平台警告";
            case "MERCHANT_BAN" -> subject + "已被暂停发布资格";
            default -> subject + "已被限制发布";
        };
    }

    private String buildMerchantReportNoticeSummary(ReportInfo reportInfo, String actionCode) {
        if (StringUtils.hasText(reportInfo.getResult())) {
            return buildSummary(reportInfo.getResult(), reportInfo.getResult());
        }
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "MERCHANT_WARN" -> "平台核查后已对企业发出警告，请尽快自查整改。";
            case "MERCHANT_BAN" -> "平台核查后已暂停企业发布资格，请尽快处理。";
            default -> "平台核查后已限制企业发布，请尽快整改资料与发布行为。";
        };
    }

    private String buildMerchantReportNoticeDetail(MerchantInfo merchantInfo, ReportInfo reportInfo, String actionCode) {
        StringBuilder detail = new StringBuilder();
        detail.append("平台核查举报后，确认企业存在违规风险。");
        if (merchantInfo != null && StringUtils.hasText(merchantInfo.getCompanyName())) {
            detail.append("关联企业：").append(merchantInfo.getCompanyName()).append("。");
        }
        detail.append("处理结果：").append(describeReportAction(actionCode)).append("。");
        if (StringUtils.hasText(reportInfo.getResult())) {
            detail.append("处理说明：").append(reportInfo.getResult().trim()).append("。");
        }
        detail.append("请尽快完成企业资料与发布行为整改。");
        return detail.toString();
    }

    private String buildMerchantReportRequiredAction(String actionCode) {
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "MERCHANT_WARN" -> "请立即自查企业资料与招聘行为，并提交整改说明。";
            case "MERCHANT_BAN" -> "请先提交申诉或补充说明，待平台复核后再处理恢复资格。";
            default -> "请修正企业资料与发布行为，并提交整改说明等待复核。";
        };
    }

    private String buildUserReportNoticeTitle(SysUser user, String actionCode) {
        String prefix = "MERCHANT".equalsIgnoreCase(user.getRole()) ? "商家账号" : "账号";
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "USER_WARN" -> prefix + "收到平台警告";
            case "USER_DISABLE" -> prefix + "已被停用";
            case "USER_BLACKLIST" -> prefix + "已被列入黑名单";
            default -> prefix + "已被封禁";
        };
    }

    private String buildUserReportNoticeSummary(ReportInfo reportInfo, String actionCode) {
        if (StringUtils.hasText(reportInfo.getResult())) {
            return buildSummary(reportInfo.getResult(), reportInfo.getResult());
        }
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "USER_WARN" -> "平台核查后已发出警告，请立即停止相关违规行为。";
            case "USER_DISABLE" -> "平台核查后已停用该账号。";
            case "USER_BLACKLIST" -> "平台核查后已将该账号列入黑名单。";
            default -> "平台核查后已封禁该账号。";
        };
    }

    private String buildUserReportNoticeDetail(SysUser user, ReportInfo reportInfo, String actionCode) {
        StringBuilder detail = new StringBuilder();
        detail.append("平台核查举报后，确认当前账号存在违规风险。");
        if (StringUtils.hasText(user.getUsername())) {
            detail.append("账号：").append(user.getUsername().trim()).append("。");
        }
        detail.append("处理结果：").append(describeReportAction(actionCode)).append("。");
        if (StringUtils.hasText(reportInfo.getResult())) {
            detail.append("处理说明：").append(reportInfo.getResult().trim()).append("。");
        }
        detail.append("如对本次处理有异议，可提交申诉说明。");
        return detail.toString();
    }

    private String buildUserReportRequiredAction(String actionCode) {
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "USER_WARN" -> "请立即停止相关行为，并按要求进行自查整改。";
            default -> "如对本次处理有异议，可在平台提醒中提交申诉说明。";
        };
    }

    private String buildRiskBanTitle(SysUser user, Integer banStatus) {
        String prefix = user != null && "MERCHANT".equalsIgnoreCase(user.getRole()) ? "商家账号" : "账号";
        return switch (banStatus) {
            case 2 -> prefix + "已被列入黑名单";
            case 1 -> prefix + "已被封禁";
            default -> prefix + "状态已更新";
        };
    }

    private String buildRiskBanSummary(Integer banStatus, String banReason, LocalDateTime banUntil) {
        if (StringUtils.hasText(banReason)) {
            return buildSummary(banReason, banReason);
        }
        if (banStatus != null && banStatus == 2) {
            return "平台已将当前账号列入黑名单。";
        }
        if (banUntil != null) {
            return "平台已限制当前账号使用，限制截止时间：" + banUntil;
        }
        return "平台已限制当前账号使用权限。";
    }

    private String buildRiskBanDetail(SysUser user, Integer banStatus, String banReason, LocalDateTime banUntil) {
        StringBuilder detail = new StringBuilder();
        detail.append("平台风控已更新当前账号状态。");
        if (user != null && StringUtils.hasText(user.getUsername())) {
            detail.append("账号：").append(user.getUsername().trim()).append("。");
        }
        detail.append("处理结果：").append(describeBanStatus(banStatus)).append("。");
        if (StringUtils.hasText(banReason)) {
            detail.append("处理原因：").append(banReason.trim()).append("。");
        }
        if (banUntil != null) {
            detail.append("限制截止：").append(banUntil).append("。");
        }
        detail.append("如有异议，可提交申诉说明。");
        return detail.toString();
    }

    private String buildRiskBanActionContent(Integer banStatus, String banReason, LocalDateTime banUntil) {
        StringBuilder content = new StringBuilder(describeBanStatus(banStatus));
        if (StringUtils.hasText(banReason)) {
            content.append("，原因：").append(banReason.trim());
        }
        if (banUntil != null) {
            content.append("，截止：").append(banUntil);
        }
        return content.toString();
    }

    private String buildReportExtraJson(ReportInfo reportInfo, String actionCode) {
        if (reportInfo == null || reportInfo.getId() == null) {
            return null;
        }
        return "{\"reportId\":" + reportInfo.getId()
                + ",\"reportType\":\"" + safeText(reportInfo.getType()).toUpperCase() + "\""
                + ",\"actionCode\":\"" + safeText(actionCode).toUpperCase() + "\"}";
    }

    private String describeReportType(String reportType) {
        String upper = normalizeValue(reportType);
        return switch (upper) {
            case "JOB" -> "职位举报";
            case "MERCHANT" -> "商家举报";
            case "USER" -> "账号举报";
            default -> "举报";
        };
    }

    private String describeReportAction(String actionCode) {
        String upper = normalizeValue(actionCode);
        return switch (upper) {
            case "JOB_WARN" -> "职位警告";
            case "JOB_OFFLINE" -> "下架职位";
            case "JOB_OFFLINE_LIMIT_MERCHANT" -> "下架职位并限制企业发布";
            case "JOB_OFFLINE_BAN_MERCHANT" -> "下架职位并暂停企业发布";
            case "MERCHANT_WARN" -> "企业警告";
            case "MERCHANT_LIMIT" -> "限制企业发布";
            case "MERCHANT_BAN" -> "暂停企业发布";
            case "USER_WARN" -> "账号警告";
            case "USER_DISABLE" -> "停用账号";
            case "USER_BAN" -> "封禁账号";
            case "USER_BLACKLIST" -> "列入黑名单";
            case "REJECT" -> "驳回举报";
            default -> "";
        };
    }

    private String describeBanStatus(Integer banStatus) {
        if (banStatus == null) {
            return "状态未知";
        }
        return switch (banStatus) {
            case 2 -> "列入黑名单";
            case 1 -> "封禁账号";
            case 0 -> "解除限制";
            default -> "状态未知";
        };
    }

    private String buildSimpleExtraJson(String key, Long value) {
        if (value == null || !StringUtils.hasText(key)) {
            return null;
        }
        return "{\"" + key.trim() + "\":" + value + "}";
    }
}
