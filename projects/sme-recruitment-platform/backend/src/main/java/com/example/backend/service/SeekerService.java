package com.example.backend.service;

import com.example.backend.vo.SeekerDashboardVO;

public interface SeekerService {
    SeekerDashboardVO getDashboardStats(Long userId);
    java.util.Map<String, Object> getInsightStats(Long userId);
}
