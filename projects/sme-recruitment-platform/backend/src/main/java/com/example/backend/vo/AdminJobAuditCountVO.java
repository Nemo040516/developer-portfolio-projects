package com.example.backend.vo;

import lombok.Data;

/**
 * 职位审核统计（用于 Tab 数量）
 */
@Data
public class AdminJobAuditCountVO {
    private Integer total;
    private Integer pending;
    private Integer approved;
    private Integer rejected;
}
