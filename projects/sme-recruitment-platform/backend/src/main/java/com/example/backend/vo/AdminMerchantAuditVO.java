package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员商家审核列表 VO
 */
@Data
public class AdminMerchantAuditVO {
    private Long id;
    private String companyName;
    private String contact;
    private String phone;
    private String address;
    private String licenseUrl;
    private String legalPerson;
    private String creditCode;
    private String qualificationUrls;
    private Integer auditStatus;
    private String reason;
    private Integer publishStatus;
    private Integer reportCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime submittedAt;
}
