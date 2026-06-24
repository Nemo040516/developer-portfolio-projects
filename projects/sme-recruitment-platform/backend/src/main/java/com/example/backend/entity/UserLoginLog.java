package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log_login")
public class UserLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("ip")
    private String ip;

    @TableField("device")
    private String device;

    @TableField("user_agent")
    private String userAgent;

    @TableField("login_time")
    private LocalDateTime loginTime;

    @TableField("create_time")
    private LocalDateTime createTime;
}
