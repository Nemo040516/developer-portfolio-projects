package com.example.backend.vo;

import lombok.Data;

/**
 * 候选人简要信息
 */
@Data
public class ApplicantSimpleVO {
    private Long userId;     // 求职者用户ID
    private String nickname; // 昵称
    private String realName; // 真实姓名
    private Integer gender;  // 性别: 1-男, 2-女, 0-保密
    private Integer age;     // 年龄
    private String workYears;// 工作年限
    private String college;  // 毕业院校（标准字段）
    private String collage;  // 毕业院校
    private String degree;   // 学历
    private String avatar;   // 头像
    private String phone;    // 联系电话
    private String email;    // 邮箱
    private String currentIdentity; // 当前身份
    private String currentStatus;   // 求职状态
    private String expectCity;      // 期望城市
    private String expectJob;    // 期望职位
    private String expectSalary; // 期望薪资
    private String skills;       // 技能标签
    private String advantage;    // 个人优势

    // 结构化简历（JSON字符串）
    private String educationJson;
    private String experienceJson;
    private String projectJson;
    private String certificateJson;
    private String awardJson;
    private String languageJson;
}
