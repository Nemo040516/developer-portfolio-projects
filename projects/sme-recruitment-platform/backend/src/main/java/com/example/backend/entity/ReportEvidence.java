package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报证据附件
 */
@Data
@TableName("report_evidence")
public class ReportEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联举报ID（report_info.id）
     */
    @TableField("report_id")
    private Long reportId;

    /**
     * 证据文件地址
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 文件类型：IMAGE / PDF / FILE
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 展示顺序（从1开始）
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 上传者ID（通常为举报人）
     */
    @TableField("uploader_id")
    private Long uploaderId;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
