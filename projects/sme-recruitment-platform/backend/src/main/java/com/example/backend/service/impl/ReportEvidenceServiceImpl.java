package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.ReportEvidence;
import com.example.backend.mapper.ReportEvidenceMapper;
import com.example.backend.service.ReportEvidenceService;
import org.springframework.stereotype.Service;

/**
 * 举报证据服务默认实现
 */
@Service
public class ReportEvidenceServiceImpl extends ServiceImpl<ReportEvidenceMapper, ReportEvidence> implements ReportEvidenceService {
}
