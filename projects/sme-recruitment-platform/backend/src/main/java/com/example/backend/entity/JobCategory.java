package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("job_category")
public class JobCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("category_name")
    private String categoryName;
    
    private Integer sort;
    
    @TableField("parent_id")
    private Long parentId;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<JobCategory> children;
}
