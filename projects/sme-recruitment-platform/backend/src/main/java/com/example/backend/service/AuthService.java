package com.example.backend.service;

import com.example.backend.common.Result;
import com.example.backend.entity.SysUser;

import java.util.Map;

public interface AuthService {
    Result<SysUser> register(SysUser sysUser);
    Result<Map<String, Object>> login(SysUser sysUser);
}
