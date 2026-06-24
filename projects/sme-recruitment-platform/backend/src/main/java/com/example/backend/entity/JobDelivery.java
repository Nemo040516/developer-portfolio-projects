package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("job_apply")
public class JobDelivery {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;         // 职位ID
    private Long applicantId;   // 求职者ID (关联 sys_user)
    
    // 如果之后做文件上传，这里存地址；暂时可存空或根据需求扩展
    private String resumeUrl;   

    // 状态: 0-已投递, 1-被查看, 2-面试邀约, 3-不合适
    private Integer status;     

    private String feedback;    // 商家反馈意见

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
