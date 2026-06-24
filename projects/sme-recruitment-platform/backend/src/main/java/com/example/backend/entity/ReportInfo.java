package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报信息实体
 */
@Data
@TableName("report_info")
public class ReportInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 举报类型：JOB / MERCHANT / USER
     */
    private String type;

    /**
     * 被举报对象ID（职位ID或商家ID）
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 举报人ID（sys_user.id）
     */
    @TableField("reporter_id")
    private Long reporterId;

    /**
     * 举报原因
     */
    private String reason;

    /**
     * 处理状态：0-待处理，1-已处理，2-已驳回
     */
    private Integer status;

    /**
     * 处理结果说明
     */
    private String result;

    /**
     * 举报证据（文件地址列表，逗号分隔）
     */
    private String evidence;

    /**
     * 处理动作代码（如 JOB_OFFLINE / USER_BAN / REJECT）
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 处理人ID（sys_user.id）
     */
    @TableField("handled_by")
    private Long handledBy;

    /**
     * 处理时间
     */
    @TableField("handled_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime handledTime;

    /**
     * 被举报对象快照（JSON字符串，保存举报提交时的对象摘要）
     */
    @TableField("target_snapshot")
    private String targetSnapshot;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
