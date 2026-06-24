/**
 * 文件速览：
 * 1. 文件职责：实现即时沟通模块的会话列表、消息读写、岗位上下文与在线推送。
 * 2. 关键升级：消息落库与岗位切换前都会校验真实投递关系，并保持 sessionId/peerId 双入口兼容。
 * 3. 关键结构：getSessionList、getMessagePage、saveMessage、updateSessionJob、resolveOwnedSession。
 * 4. 阅读建议：先看 saveMessage / updateSessionJob，再看底部会话解析与关系校验辅助方法。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.ChatSession;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.dto.ChatWsPushDTO;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.ChatMessageMapper;
import com.example.backend.mapper.ChatSessionMapper;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.mapper.JobInfoMapper;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.ChatService;
import com.example.backend.support.ChatPresentationSupport;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.ChatJobTitleRow;
import com.example.backend.vo.ChatMessageVO;
import com.example.backend.vo.ChatSessionRow;
import com.example.backend.vo.ChatSessionVO;
import com.example.backend.vo.ChatUnreadCountRow;
import com.example.backend.websocket.ChatSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 即时通讯业务实现
 */
@Service
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 同方向、同内容、同岗位上下文在短窗口内重复提交时，视为同一条消息，避免重复落库
    private static final long MESSAGE_DEDUP_WINDOW_SECONDS = 3L;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private ApplicantInfoMapper applicantInfoMapper;

    @Autowired
    private JobDeliveryMapper jobDeliveryMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private JobInfoMapper jobInfoMapper;

    @Autowired
    private ChatSessionRegistry sessionRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatPresentationSupport chatPresentationSupport;

    @Override
    public List<ChatSessionVO> getSessionList(Long userId) {
        List<ChatSessionRow> rows = baseMapper.selectSessionLastMessageList(userId);
        if (CollectionUtils.isEmpty(rows)) {
            return new ArrayList<>();
        }

        List<Long> peerIds = rows.stream()
                .map(ChatSessionRow::getPeerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 未读数量
        List<ChatUnreadCountRow> unreadRows = baseMapper.selectUnreadCountList(userId);
        Map<Long, Integer> unreadMap = CollectionUtils.isEmpty(unreadRows)
                ? new HashMap<>()
                : unreadRows.stream().collect(Collectors.toMap(
                        ChatUnreadCountRow::getPeerId,
                        row -> Optional.ofNullable(row.getUnreadCount()).orElse(0),
                        (a, b) -> a
                ));

        // 用户基础信息
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        // 商家信息
        Map<Long, MerchantInfo> merchantMap = fetchMerchantMap(peerIds);
        // 求职者信息
        Map<Long, ApplicantInfo> applicantMap = fetchApplicantMap(peerIds);

        SysUser currentUser = sysUserMapper.selectById(userId);
        boolean currentIsMerchant = SecurityUtils.isMerchantRole(currentUser != null ? currentUser.getRole() : null);
        boolean currentIsApplicant = SecurityUtils.isApplicantRole(currentUser != null ? currentUser.getRole() : null);
        Map<Long, String> jobTitleMap = resolveJobTitleMap(userId, peerIds, currentIsMerchant, currentIsApplicant);
        Map<Long, ChatSession> sessionMap = fetchChatSessionMap(userId, peerIds, currentIsMerchant, currentIsApplicant);

        List<ChatSessionVO> result = new ArrayList<>();
        for (ChatSessionRow row : rows) {
            Long peerId = row.getPeerId();
            SysUser peer = userMap.get(peerId);

            String role = peer != null ? peer.getRole() : null;
            boolean isMerchant = SecurityUtils.isMerchantRole(role);
            boolean isApplicant = SecurityUtils.isApplicantRole(role);

            String companyName = "";
            String peerName = "未知用户";
            String avatar = peer != null ? peer.getAvatar() : "";

            if (isMerchant) {
                MerchantInfo merchantInfo = merchantMap.get(peerId);
                companyName = merchantInfo != null ? merchantInfo.getCompanyName() : "";
                peerName = firstNonBlank(companyName,
                        peer != null ? peer.getNickname() : null,
                        peer != null ? peer.getUsername() : null,
                        "商家");
            } else if (isApplicant) {
                ApplicantInfo applicantInfo = applicantMap.get(peerId);
                peerName = firstNonBlank(applicantInfo != null ? applicantInfo.getRealName() : null,
                        peer != null ? peer.getNickname() : null,
                        peer != null ? peer.getUsername() : null,
                        "求职者");
            } else {
                peerName = firstNonBlank(peer != null ? peer.getNickname() : null,
                        peer != null ? peer.getUsername() : null,
                        "用户");
            }

            ChatSessionVO session = new ChatSessionVO();
            session.setId(peerId);
            session.setPeerId(peerId);
            session.setPeerName(peerName);
            session.setCompanyName(companyName);
            session.setPeerAvatar(avatar);
            ChatSession chatSession = sessionMap.get(peerId);
            if (chatSession != null) {
                session.setId(chatSession.getId());
                session.setSessionId(chatSession.getId());
                session.setJobId(chatSession.getJobId());
                session.setJobKey(chatSession.getJobKey());
                if (StringUtils.hasText(chatSession.getJobTitle())) {
                    session.setJobTitle(chatSession.getJobTitle());
                } else {
                    session.setJobTitle(jobTitleMap.get(peerId));
                }
            } else {
                session.setJobTitle(jobTitleMap.get(peerId));
            }
            session.setLastMessage(row.getLastMessage());
            session.setLastTime(row.getLastTime());
            session.setUnreadCount(unreadMap.getOrDefault(peerId, 0));
            result.add(session);
        }
        return result;
    }

    @Override
    public IPage<ChatMessageVO> getMessagePage(Page<?> page, Long userId, Long peerId) {
        IPage<ChatMessage> messagePage = baseMapper.selectMessagePage(page, userId, peerId);
        List<ChatMessageVO> records = messagePage.getRecords().stream()
                .map(chatPresentationSupport::toMessageVO)
                .collect(Collectors.toList());

        // 前端需要按时间正序展示，分页查询为倒序，需翻转
        Collections.reverse(records);

        Page<ChatMessageVO> voPage = new Page<>(messagePage.getCurrent(), messagePage.getSize(), messagePage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public IPage<ChatMessageVO> getMessagePage(Page<?> page, Long userId, Long peerId, Long sessionId) {
        Long resolvedPeerId = resolvePeerId(userId, peerId, sessionId);
        if (resolvedPeerId == null) {
            return new Page<>(page.getCurrent(), page.getSize(), 0);
        }
        return getMessagePage(page, userId, resolvedPeerId);
    }

    @Override
    public boolean markRead(Long userId, Long peerId) {
        return baseMapper.markRead(userId, peerId) >= 0;
    }

    @Override
    public boolean markRead(Long userId, Long peerId, Long sessionId) {
        Long resolvedPeerId = resolvePeerId(userId, peerId, sessionId);
        if (resolvedPeerId == null) {
            return false;
        }
        return markRead(userId, resolvedPeerId);
    }

    @Override
    public ChatMessage saveMessage(Long fromUserId, Long toUserId, String content) {
        if (fromUserId == null || toUserId == null) {
            return null;
        }
        String normalizedContent = normalizeContent(content);
        if (!StringUtils.hasText(normalizedContent)) {
            return null;
        }

        ChatSession session = resolveOrCreateSession(fromUserId, toUserId);
        if (session == null) {
            return null;
        }
        Long jobId = null;
        String jobKey = null;
        jobId = session.getJobId();
        jobKey = session.getJobKey();

        LocalDateTime now = LocalDateTime.now();
        ChatMessage latest = baseMapper.selectLatestSimilarMessage(fromUserId, toUserId, normalizedContent, jobId, jobKey);
        if (isDuplicateInWindow(latest, now)) {
            return latest;
        }

        ChatMessage message = new ChatMessage();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setContent(normalizedContent);
        message.setIsRead(0);
        message.setJobId(jobId);
        message.setJobKey(jobKey);
        message.setCreateTime(now);
        this.save(message);
        return message;
    }

    @Override
    public ChatMessage sendMessageWithPush(Long fromUserId, Long toUserId, String content) {
        ChatMessage message = saveMessage(fromUserId, toUserId, content);
        if (message == null) {
            return null;
        }
        // REST 兜底：尝试推送给在线用户
        try {
            ChatWsPushDTO pushDTO = buildPushDTO(fromUserId, toUserId, message.getContent());
            String payload = objectMapper.writeValueAsString(pushDTO);
            sessionRegistry.sendToUser(toUserId, payload);
        } catch (Exception ignored) {
            // 推送失败不影响消息落库
        }
        return message;
    }

    @Override
    public ChatSession updateSessionJob(Long userId, Long peerId, Long jobId, String jobKey) {
        // 更新会话岗位上下文（用于岗位切换/附件申请）
        if (userId == null || peerId == null || jobId == null) {
            return null;
        }
        PairIds pair = resolvePair(userId, peerId);
        if (pair == null) {
            return null;
        }

        JobInfo jobInfo = jobInfoMapper.selectById(jobId);
        if (jobInfo == null) {
            return null;
        }
        if (!Objects.equals(jobInfo.getMerchantId(), pair.merchantId)) {
            return null;
        }

        ChatSession session = getOrCreateSession(pair.applicantId, pair.merchantId);
        session.setJobId(jobId);
        session.setJobTitle(jobInfo.getTitle());
        session.setJobKey(StringUtils.hasText(jobKey) ? jobKey : buildJobKey(jobId));
        session.setUpdateTime(LocalDateTime.now());
        if (session.getId() == null) {
            session.setCreateTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            chatSessionMapper.updateById(session);
        }
        return session;
    }

    @Override
    public ChatSession updateSessionJob(Long userId, Long peerId, Long sessionId, Long jobId, String jobKey) {
        Long resolvedPeerId = resolvePeerId(userId, peerId, sessionId);
        if (resolvedPeerId == null) {
            return null;
        }
        return updateSessionJob(userId, resolvedPeerId, jobId, jobKey);
    }

    private Map<Long, ChatSession> fetchChatSessionMap(Long userId,
                                                       List<Long> peerIds,
                                                       boolean currentIsMerchant,
                                                       boolean currentIsApplicant) {
        // 批量读取会话岗位信息，优先用于会话列表展示
        if (CollectionUtils.isEmpty(peerIds)) {
            return new HashMap<>();
        }
        LambdaQueryWrapper<ChatSession> query = new LambdaQueryWrapper<>();
        if (currentIsMerchant) {
            query.eq(ChatSession::getMerchantId, userId)
                    .in(ChatSession::getApplicantId, peerIds);
        } else if (currentIsApplicant) {
            query.eq(ChatSession::getApplicantId, userId)
                    .in(ChatSession::getMerchantId, peerIds);
        } else {
            return new HashMap<>();
        }
        List<ChatSession> list = chatSessionMapper.selectList(query);
        if (CollectionUtils.isEmpty(list)) {
            return new HashMap<>();
        }
        return list.stream().collect(Collectors.toMap(
                currentIsMerchant ? ChatSession::getApplicantId : ChatSession::getMerchantId,
                item -> item,
                (a, b) -> a
        ));
    }

    private ChatSession resolveOrCreateSession(Long fromUserId, Long toUserId) {
        PairIds pair = resolvePair(fromUserId, toUserId);
        if (pair == null) {
            return null;
        }
        return getOrCreateSession(pair.applicantId, pair.merchantId);
    }

    private Long resolvePeerId(Long userId, Long peerId, Long sessionId) {
        if (peerId != null) {
            if (sessionId == null) {
                return peerId;
            }
            ChatSession session = resolveOwnedSession(userId, sessionId);
            if (session == null) {
                return null;
            }
            Long resolvedPeerId = resolvePeerIdFromSession(userId, session);
            return Objects.equals(peerId, resolvedPeerId) ? resolvedPeerId : null;
        }
        if (sessionId == null) {
            return null;
        }
        ChatSession session = resolveOwnedSession(userId, sessionId);
        return resolvePeerIdFromSession(userId, session);
    }

    private ChatSession resolveOwnedSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return null;
        }
        if (Objects.equals(userId, session.getApplicantId()) || Objects.equals(userId, session.getMerchantId())) {
            return session;
        }
        return null;
    }

    private Long resolvePeerIdFromSession(Long userId, ChatSession session) {
        if (userId == null || session == null) {
            return null;
        }
        if (Objects.equals(userId, session.getApplicantId())) {
            return session.getMerchantId();
        }
        if (Objects.equals(userId, session.getMerchantId())) {
            return session.getApplicantId();
        }
        return null;
    }

    private ChatSession getOrCreateSession(Long applicantId, Long merchantId) {
        ChatSession existing = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getApplicantId, applicantId)
                .eq(ChatSession::getMerchantId, merchantId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        ChatSession session = new ChatSession();
        session.setApplicantId(applicantId);
        session.setMerchantId(merchantId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    private PairIds resolvePair(Long userId, Long peerId) {
        SysUser user = sysUserMapper.selectById(userId);
        SysUser peer = sysUserMapper.selectById(peerId);
        if (user == null || peer == null) {
            return null;
        }
        boolean userIsMerchant = SecurityUtils.isMerchantRole(user.getRole());
        boolean userIsApplicant = SecurityUtils.isApplicantRole(user.getRole());
        boolean peerIsMerchant = SecurityUtils.isMerchantRole(peer.getRole());
        boolean peerIsApplicant = SecurityUtils.isApplicantRole(peer.getRole());
        if (userIsMerchant && peerIsApplicant) {
            return hasDeliveryRelation(userId, peerId) ? new PairIds(peerId, userId) : null;
        }
        if (userIsApplicant && peerIsMerchant) {
            return hasDeliveryRelation(peerId, userId) ? new PairIds(userId, peerId) : null;
        }
        return null;
    }

    /**
     * 即时沟通必须建立在真实投递关系上，避免跨角色任意探测与骚扰。
     */
    private boolean hasDeliveryRelation(Long merchantId, Long applicantId) {
        return merchantId != null
                && applicantId != null
                && jobDeliveryMapper.countMerchantApplicantRelation(merchantId, applicantId) > 0;
    }

    private String buildJobKey(Long jobId) {
        return jobId == null ? "" : "JOB:" + jobId;
    }

    private Map<Long, MerchantInfo> fetchMerchantMap(List<Long> peerIds) {
        if (CollectionUtils.isEmpty(peerIds)) {
            return new HashMap<>();
        }
        List<MerchantInfo> list = merchantInfoMapper.selectList(new LambdaQueryWrapper<MerchantInfo>()
                .in(MerchantInfo::getUserId, peerIds));
        return list.stream().collect(Collectors.toMap(MerchantInfo::getUserId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, ApplicantInfo> fetchApplicantMap(List<Long> peerIds) {
        if (CollectionUtils.isEmpty(peerIds)) {
            return new HashMap<>();
        }
        List<ApplicantInfo> list = applicantInfoMapper.selectList(new LambdaQueryWrapper<ApplicantInfo>()
                .in(ApplicantInfo::getUserId, peerIds));
        return list.stream().collect(Collectors.toMap(ApplicantInfo::getUserId, Function.identity(), (a, b) -> a));
    }

    /**
     * 关联会话职位名称：商家侧按求职者最新投递职位，求职者侧按商家最新职位
     */
    private Map<Long, String> resolveJobTitleMap(Long userId,
                                                 List<Long> peerIds,
                                                 boolean currentIsMerchant,
                                                 boolean currentIsApplicant) {
        if (CollectionUtils.isEmpty(peerIds)) {
            return new HashMap<>();
        }
        if (currentIsMerchant) {
            List<ChatJobTitleRow> rows = jobDeliveryMapper.selectLatestJobTitleByApplicants(userId, peerIds);
            return rows.stream().collect(Collectors.toMap(
                    ChatJobTitleRow::getApplicantId,
                    ChatJobTitleRow::getJobTitle,
                    (a, b) -> a
            ));
        }
        if (currentIsApplicant) {
            List<ChatJobTitleRow> rows = jobDeliveryMapper.selectLatestJobTitleByMerchants(userId, peerIds);
            return rows.stream().collect(Collectors.toMap(
                    ChatJobTitleRow::getMerchantId,
                    ChatJobTitleRow::getJobTitle,
                    (a, b) -> a
            ));
        }
        return new HashMap<>();
    }

    /**
     * 构建推送消息体（用于 REST 兜底）
     */
    private ChatWsPushDTO buildPushDTO(Long fromUserId, Long toUserId, String content) {
        ChatWsPushDTO pushDTO = new ChatWsPushDTO();
        pushDTO.setFromUserId(fromUserId);
        pushDTO.setToUserId(toUserId);
        pushDTO.setContent(content);
        pushDTO.setCreateTime(LocalDateTime.now().format(TIME_FORMATTER));
        chatPresentationSupport.fillSenderProfile(pushDTO, fromUserId);
        return pushDTO;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 统一消息文本归一化：去掉首尾空白，避免“同内容不同空格”造成重复。
     */
    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.trim();
    }

    /**
     * 短窗口幂等判断：窗口内相同消息仅保留一条。
     */
    private boolean isDuplicateInWindow(ChatMessage latest, LocalDateTime now) {
        if (latest == null || latest.getCreateTime() == null || now == null) {
            return false;
        }
        long seconds = Duration.between(latest.getCreateTime(), now).getSeconds();
        return seconds >= 0 && seconds <= MESSAGE_DEDUP_WINDOW_SECONDS;
    }

    private static class PairIds {
        private final Long applicantId;
        private final Long merchantId;

        private PairIds(Long applicantId, Long merchantId) {
            this.applicantId = applicantId;
            this.merchantId = merchantId;
        }
    }
}
