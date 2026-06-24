package com.example.backend.vo;

import lombok.Data;

@Data
public class UserVerifyStatusVO {
    private String status;
    private String submittedAt;
    private String auditReason;
    private String auditTime;
    private Long auditUserId;
    private String auditUserName;
}
