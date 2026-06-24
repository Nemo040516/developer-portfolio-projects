package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.JobViewLog;
import com.example.backend.mapper.JobViewLogMapper;
import com.example.backend.service.JobViewLogService;
import org.springframework.stereotype.Service;

@Service
public class JobViewLogServiceImpl extends ServiceImpl<JobViewLogMapper, JobViewLog> implements JobViewLogService {
}
