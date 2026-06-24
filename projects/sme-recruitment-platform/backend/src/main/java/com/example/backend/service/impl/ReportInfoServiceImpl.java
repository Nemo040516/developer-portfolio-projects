package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.ReportInfo;
import com.example.backend.mapper.ReportInfoMapper;
import com.example.backend.service.ReportInfoService;
import org.springframework.stereotype.Service;

@Service
public class ReportInfoServiceImpl extends ServiceImpl<ReportInfoMapper, ReportInfo> implements ReportInfoService {
    // 举报服务默认实现（如需扩展业务逻辑可在此补充）
}
