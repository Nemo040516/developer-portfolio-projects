package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位浏览记录
 */
@Data
@TableName("job_view_log")
public class JobViewLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("job_id")
    private Long jobId;

    @TableField("merchant_id")
    private Long merchantId;

    @TableField("viewer_id")
    private Long viewerId;

    @TableField("view_time")
    private LocalDateTime viewTime;
}
