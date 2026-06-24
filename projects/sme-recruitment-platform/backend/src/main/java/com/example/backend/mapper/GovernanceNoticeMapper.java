/*
 * 文件速览：
 * 1. 文件职责：治理通知主表 Mapper，承载 governance_notice 的基础 CRUD。
 * 2. 对外入口：供 GovernanceNoticeService 后续实现列表、详情与状态流转。
 * 3. 关键结构：当前先复用 BaseMapper，复杂列表查询留到 Phase 1 下一轮补 XML。
 * 4. 阅读建议：先看实体字段，再在 ServiceImpl 中补业务查询。
 */
package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.GovernanceNotice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GovernanceNoticeMapper extends BaseMapper<GovernanceNotice> {
}
