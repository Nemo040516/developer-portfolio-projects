package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试安排（支持多轮）
 */
@Data
@TableName("job_interview")
public class InterviewSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("delivery_id")
    private Long deliveryId; // 投递记录ID

    @TableField("round_no")
    private Integer roundNo; // 面试轮次

    @TableField("status")
    private Integer status; // 状态：0-待确认，1-已确认，2-已拒绝，3-已取消，4-已完成

    @TableField("schedule_time")
    private LocalDateTime scheduleTime; // 面试时间

    @TableField("location")
    private String location; // 面试地点

    @TableField("method")
    private String method; // 面试形式

    @TableField("remark")
    private String remark; // 面试备注

    @TableField("creator_id")
    private Long creatorId; // 创建人（商家ID）

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
