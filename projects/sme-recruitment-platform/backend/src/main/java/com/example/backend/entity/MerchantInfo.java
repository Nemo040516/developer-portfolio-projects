package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("merchant_profile")
public class MerchantInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("company_name")
    private String companyName;

    @TableField("company_logo")
    private String companyLogo;

    private String industry;
    private String scale;
    private String financing;
    private String description;
    private String province;
    private String city;
    private String district;
    private String address;

    // 企业联系人
    @TableField("contact_name")
    private String contactName;

    // 联系人电话
    @TableField("contact_phone")
    private String contactPhone;

    // 统一社会信用代码
    @TableField("credit_code")
    private String creditCode;

    // 法人姓名
    @TableField("legal_person")
    private String legalPerson;
    
    private BigDecimal longitude;
    private BigDecimal latitude;

    @TableField("license_url")
    private String licenseUrl;

    // 资质材料(JSON数组字符串)
    @TableField("qualification_urls")
    private String qualificationUrls;

    @TableField("audit_status")
    private Integer auditStatus;

    // 审核驳回原因
    @TableField("audit_reason")
    private String auditReason;

    // 审核时间
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /**
     * 发布状态：0-限制发布，1-正常，2-封禁
     */
    @TableField("publish_status")
    private Integer publishStatus;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
