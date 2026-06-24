/*
 * 文件速览：
 * 1. 文件职责：处理商家资料的查询、保存更新、Logo 维护，并在资料整改场景下联动治理通知状态。
 * 2. 对外入口：MerchantInfoService，由商家资料控制器调用。
 * 3. 关键结构：saveOrUpdateMerchant、updateCompanyLogo、商家资料复审同步逻辑。
 * 4. 阅读建议：先看 saveOrUpdateMerchant 的审核状态处理，再看整改通知转待复核的联动。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.dto.MerchantInfoDTO;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.service.MerchantInfoService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MerchantInfoServiceImpl extends ServiceImpl<MerchantInfoMapper, MerchantInfo> implements MerchantInfoService {

    @Autowired
    private GovernanceNoticeService governanceNoticeService;

    @Override
    public MerchantInfo getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<MerchantInfo>()
                .eq(MerchantInfo::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateMerchant(Long userId, MerchantInfoDTO dto) {
        // 1. 先查询是否存在
        MerchantInfo merchantInfo = this.getByUserId(userId);
        Integer previousAuditStatus = merchantInfo == null ? null : merchantInfo.getAuditStatus();
        boolean needReaudit = false;
        LocalDateTime now = LocalDateTime.now();
        
        if (merchantInfo == null) {
            merchantInfo = new MerchantInfo();
            merchantInfo.setUserId(userId);
            merchantInfo.setAuditStatus(0); 
            merchantInfo.setAuditReason(null);
            merchantInfo.setAuditTime(null);
            merchantInfo.setPublishStatus(1);
        } else {
            // 已通过的商家，如关键字段发生变化，需要重新进入审核
            if (merchantInfo.getAuditStatus() != null && merchantInfo.getAuditStatus() == 1) {
                needReaudit = !java.util.Objects.equals(merchantInfo.getCompanyName(), dto.getCompanyName())
                        || !java.util.Objects.equals(merchantInfo.getContactName(), dto.getContactName())
                        || !java.util.Objects.equals(merchantInfo.getContactPhone(), dto.getContactPhone())
                        || !java.util.Objects.equals(merchantInfo.getCreditCode(), dto.getCreditCode())
                        || !java.util.Objects.equals(merchantInfo.getLegalPerson(), dto.getLegalPerson())
                        || !java.util.Objects.equals(merchantInfo.getLicenseUrl(), dto.getLicenseUrl())
                        || !java.util.Objects.equals(merchantInfo.getQualificationUrls(), dto.getQualificationUrls());
            }
        }
        
        // 2. 属性拷贝 (将 DTO 的值覆盖到 Entity)
        BeanUtils.copyProperties(dto, merchantInfo);
        merchantInfo.setUpdateTime(now);
        
        // 3. 审核状态处理
        // 未通过或未审核的商家，更新资料后继续进入待审核
        // 已通过的商家，默认保持通过状态（避免频繁中断业务）
        if (merchantInfo.getAuditStatus() == null || merchantInfo.getAuditStatus() != 1 || needReaudit) {
            merchantInfo.setAuditStatus(0);
            merchantInfo.setAuditReason(null);
            merchantInfo.setAuditTime(null);
        }

        // 4. 落库 (MyBatis Plus 的 saveOrUpdate 会根据是否有 ID 自动判断)
        this.saveOrUpdate(merchantInfo);

        // 5. 若当前属于“驳回后整改重新提交”，则同步推进治理事项进入待复核。
        if (previousAuditStatus != null && previousAuditStatus == 2 && merchantInfo.getId() != null) {
            governanceNoticeService.syncMerchantRectifyNoticeOnResubmit(
                    merchantInfo.getId(),
                    userId,
                    buildMerchantResubmitSummary(dto)
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCompanyLogo(Long userId, String logoUrl) {
        // 更新企业 Logo（若企业资料不存在则先创建）
        MerchantInfo merchantInfo = this.getByUserId(userId);
        if (merchantInfo == null) {
            merchantInfo = new MerchantInfo();
            merchantInfo.setUserId(userId);
            merchantInfo.setAuditStatus(0);
            merchantInfo.setAuditReason(null);
            merchantInfo.setAuditTime(null);
            merchantInfo.setPublishStatus(1);
        }
        merchantInfo.setCompanyLogo(logoUrl);
        merchantInfo.setUpdateTime(LocalDateTime.now());
        this.saveOrUpdate(merchantInfo);
    }

    /**
     * 生成企业资料重新提交时的简短摘要，便于在治理时间线中快速回看本次操作。
     */
    private String buildMerchantResubmitSummary(MerchantInfoDTO dto) {
        String companyName = dto == null ? null : dto.getCompanyName();
        if (companyName == null || companyName.trim().isEmpty()) {
            return "商家已更新企业资料并重新提交审核。";
        }
        return "商家已更新企业《" + companyName.trim() + "》资料并重新提交审核。";
    }
}
