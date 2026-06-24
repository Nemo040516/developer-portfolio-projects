package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private String role; // "APPLICANT" / "MERCHANT" / "ADMIN"
    // 角色排序由 MySQL 生成列维护，应用层只读，避免 updateById 误写导致 SQL 异常。
    @TableField(value = "role_sort", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer roleSort;
    private Integer status;
    private Integer banStatus;
    private String banReason;
    private LocalDateTime banUntil;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
