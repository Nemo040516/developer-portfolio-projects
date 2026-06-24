package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.UserPrivacySetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPrivacySettingMapper extends BaseMapper<UserPrivacySetting> {
}
