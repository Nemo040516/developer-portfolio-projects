package com.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.entity.JobCategory;

import java.util.List;

public interface JobCategoryService extends IService<JobCategory> {
    List<JobCategory> getAllCategories();
}
