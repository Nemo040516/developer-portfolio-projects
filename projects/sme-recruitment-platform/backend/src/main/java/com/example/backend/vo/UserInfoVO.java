package com.example.backend.vo;

import lombok.Data;

@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    
    /**
     * 认证状态
     * 对于 MERCHANT：对应 MerchantInfo 的 auditStatus (0:待审核, 1:通过, 2:拒绝, 3:未提交)
     * 对于 APPLICANT/ADMIN：默认 1 (已认证)
     */
    private Integer auditStatus;
}
