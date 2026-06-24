package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.vo.AdminMerchantAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantInfoMapper extends BaseMapper<MerchantInfo> {
    // 管理员：商家审核列表
    IPage<AdminMerchantAuditVO> selectAdminAuditList(Page<?> page,
                                                     @Param("keyword") String keyword,
                                                     @Param("status") Integer status);
}
