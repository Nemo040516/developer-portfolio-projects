/*
 * 文件速览：
 * 1. 文件职责：统一聊天链路中的消息 VO 转换与发送者展示信息补齐。
 * 2. 对外入口：toMessageVO、fillSenderProfile、resolveDisplayName。
 * 3. 关键结构：商家公司名优先、求职者真实姓名优先、昵称/用户名兜底。
 * 4. 阅读建议：先看 fillSenderProfile，再看 resolveDisplayName。
 */
package com.example.backend.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.dto.ChatWsPushDTO;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.ChatMessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 聊天展示辅助组件。
 */
@Component
public class ChatPresentationSupport {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private ApplicantInfoMapper applicantInfoMapper;

    public ChatMessageVO toMessageVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        if (message == null) {
            return vo;
        }
        vo.setId(message.getId());
        vo.setFromUserId(message.getFromUserId());
        vo.setToUserId(message.getToUserId());
        vo.setContent(message.getContent());
        vo.setIsRead(message.getIsRead());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    public void fillSenderProfile(ChatWsPushDTO pushDTO, Long senderUserId) {
        if (pushDTO == null || senderUserId == null) {
            return;
        }
        SysUser sender = sysUserMapper.selectById(senderUserId);
        if (sender == null) {
            return;
        }
        pushDTO.setSenderAvatar(sender.getAvatar());
        pushDTO.setSenderName(resolveDisplayName(sender));
    }

    public String resolveDisplayName(SysUser user) {
        if (user == null) {
            return "用户";
        }
        String role = user.getRole();
        if (SecurityUtils.isMerchantRole(role)) {
            MerchantInfo merchantInfo = merchantInfoMapper.selectOne(new LambdaQueryWrapper<MerchantInfo>()
                    .eq(MerchantInfo::getUserId, user.getId())
                    .last("LIMIT 1"));
            if (merchantInfo != null && StringUtils.hasText(merchantInfo.getCompanyName())) {
                return merchantInfo.getCompanyName();
            }
        }
        if (SecurityUtils.isApplicantRole(role)) {
            ApplicantInfo applicantInfo = applicantInfoMapper.selectOne(new LambdaQueryWrapper<ApplicantInfo>()
                    .eq(ApplicantInfo::getUserId, user.getId())
                    .last("LIMIT 1"));
            if (applicantInfo != null && StringUtils.hasText(applicantInfo.getRealName())) {
                return applicantInfo.getRealName();
            }
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : "用户";
    }
}
