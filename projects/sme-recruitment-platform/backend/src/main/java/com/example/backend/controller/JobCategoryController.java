package com.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.common.Result;
import com.example.backend.entity.JobCategory;
import com.example.backend.service.JobCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/category", "/categories"})
public class JobCategoryController {

    @Autowired
    private JobCategoryService jobCategoryService;

    // 原有的树形结构接口 (保留)
    @GetMapping({"/list", ""})
    public Result<List<JobCategory>> list() {
        return Result.success(jobCategoryService.getAllCategories());
    }

    // 新增：获取所有分类列表 (扁平结构，按 sort 排序)
    @GetMapping("/all")
    public Result<List<JobCategory>> getAll() {
        QueryWrapper<JobCategory> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort");
        return Result.success(jobCategoryService.list(wrapper));
    }
}
