/*
 * 文件速览：
 * 1. 文件职责：定义商家候选人库分页与详情查询接口。
 * 2. 关键要求：商家端返回前需按求职者隐私规则做脱敏。
 * 3. 主要调用方：TalentPoolController。
 * 4. 阅读建议：先看列表，再看详情。
 */
package com.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.vo.TalentCandidateVO;

/**
 * 商家候选人库服务
 */
public interface TalentPoolService {
    /**
     * 获取候选人库分页列表
     *
     * @param page      分页参数
     * @param keyword   关键词（姓名/技能/期望职位）
     * @param expectJob 期望职位
     * @param city      期望城市
     * @return 候选人分页列表
     */
    IPage<TalentCandidateVO> getTalentPage(Page<?> page, Long merchantId, String keyword, String expectJob, String city);

    /**
     * 获取候选人详情（在线简历）
     *
     * @param userId 求职者用户ID
     * @return 候选人详情
     */
    TalentCandidateVO getTalentDetail(Long merchantId, Long userId);
}
