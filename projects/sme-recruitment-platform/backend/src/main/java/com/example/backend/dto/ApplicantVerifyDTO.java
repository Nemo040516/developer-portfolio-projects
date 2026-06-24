package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplicantVerifyDTO {
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不能超过50")
    private String realName;

    @NotBlank(message = "证件类型不能为空")
    @Size(max = 20, message = "证件类型长度不能超过20")
    private String certType;

    @NotBlank(message = "证件号码不能为空")
    @Size(max = 50, message = "证件号码长度不能超过50")
    private String certNo;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
