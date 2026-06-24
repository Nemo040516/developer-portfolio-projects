package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件简历返回对象
 */
@Data
public class ResumeAttachmentVO {
    private String fileName;       // 文件名
    private String fileUrl;        // 文件访问地址
    private LocalDateTime updateTime; // 更新时间（可选）
}
