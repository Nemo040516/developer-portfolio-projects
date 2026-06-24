package com.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.dto.MerchantInfoDTO;
import com.example.backend.entity.MerchantInfo;

public interface MerchantInfoService extends IService<MerchantInfo> {
    MerchantInfo getByUserId(Long userId);
    void saveOrUpdateMerchant(Long userId, MerchantInfoDTO dto);
    void updateCompanyLogo(Long userId, String logoUrl);
}
