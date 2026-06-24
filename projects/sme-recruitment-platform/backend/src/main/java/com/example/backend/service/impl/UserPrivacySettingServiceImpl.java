package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.UserPrivacySetting;
import com.example.backend.mapper.UserPrivacySettingMapper;
import com.example.backend.service.UserPrivacySettingService;
import org.springframework.stereotype.Service;

@Service
public class UserPrivacySettingServiceImpl extends ServiceImpl<UserPrivacySettingMapper, UserPrivacySetting> implements UserPrivacySettingService {
}
