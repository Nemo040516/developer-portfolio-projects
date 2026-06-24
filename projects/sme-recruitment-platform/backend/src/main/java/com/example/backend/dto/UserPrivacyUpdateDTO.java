package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPrivacyUpdateDTO {
    @NotBlank(message = "联系方式可见范围不能为空")
    @Size(max = 20, message = "联系方式可见范围长度不能超过20")
    private String contactVisibility;
}
