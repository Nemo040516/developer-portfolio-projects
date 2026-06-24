package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MerchantInfoDTO {
    // 不需要传 ID，ID 由后端根据当前登录用户自动获取
    
    @NotBlank(message = "企业名称不能为空")
    private String companyName;

    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;

    @NotBlank(message = "联系人电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "联系人电话格式不正确")
    private String contactPhone;

    @NotBlank(message = "统一社会信用代码不能为空")
    private String creditCode;

    @NotBlank(message = "法人姓名不能为空")
    private String legalPerson;
    
    private String companyLogo; // Logo 图片地址
    private String industry;    // 行业
    private String scale;       // 规模
    private String financing;   // 融资阶段
    private String description; // 简介
    
    // 地址相关
    private String province;
    private String city;
    private String district;
    private String address;     // 详细门牌号
    
    @NotBlank(message = "请上传营业执照")
    private String licenseUrl;  // 营业执照

    // 资质材料(JSON数组字符串)
    private String qualificationUrls;
}
