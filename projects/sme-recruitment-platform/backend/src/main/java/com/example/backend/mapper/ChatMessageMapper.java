package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.ChatMessage;
import com.example.backend.vo.ChatSessionRow;
import com.example.backend.vo.ChatUnreadCountRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天消息 Mapper
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话列表（按最后一条消息排序）
     */
    List<ChatSessionRow> selectSessionLastMessageList(@Param("userId") Long userId);

    /**
     * 查询未读数量（按对方用户分组）
     */
    List<ChatUnreadCountRow> selectUnreadCountList(@Param("userId") Long userId);

    /**
     * 分页查询会话消息
     */
    IPage<ChatMessage> selectMessagePage(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("peerId") Long peerId
    );

    /**
     * 查询同向最近一条“可能重复”的消息（用于短窗口幂等防重）
     */
    ChatMessage selectLatestSimilarMessage(
            @Param("fromUserId") Long fromUserId,
            @Param("toUserId") Long toUserId,
            @Param("content") String content,
            @Param("jobId") Long jobId,
            @Param("jobKey") String jobKey
    );

    /**
     * 标记已读
     */
    int markRead(@Param("userId") Long userId, @Param("peerId") Long peerId);
}
