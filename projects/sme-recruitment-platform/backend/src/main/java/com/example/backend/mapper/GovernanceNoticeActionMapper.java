/*
 * 文件速览：
 * 1. 文件职责：治理通知动作表 Mapper，承载 governance_notice_action 的基础 CRUD。
 * 2. 对外入口：供 GovernanceNoticeService 记录已读、整改提交、申诉、复核动作。
 * 3. 关键结构：当前先复用 BaseMapper，时间线排序查询留到后续补充。
 * 4. 阅读建议：配合 GovernanceNoticeAction 实体一起阅读。
 */
package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.GovernanceNoticeAction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GovernanceNoticeActionMapper extends BaseMapper<GovernanceNoticeAction> {
}
