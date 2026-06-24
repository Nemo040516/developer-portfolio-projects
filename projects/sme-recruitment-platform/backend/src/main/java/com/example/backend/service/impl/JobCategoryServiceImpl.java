package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.JobCategory;
import com.example.backend.mapper.JobCategoryMapper;
import com.example.backend.service.JobCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobCategoryServiceImpl extends ServiceImpl<JobCategoryMapper, JobCategory> implements JobCategoryService {

    @Override
    public List<JobCategory> getAllCategories() {
        // 1. 查询所有分类
        List<JobCategory> all = list(new LambdaQueryWrapper<JobCategory>()
                .orderByAsc(JobCategory::getSort));

        // 2. 按 parentId 分组
        Map<Long, List<JobCategory>> grouped = all.stream()
                .collect(Collectors.groupingBy(cat -> cat.getParentId() == null ? 0L : cat.getParentId()));

        // 3. 组装树形结构
        all.forEach(cat -> cat.setChildren(grouped.get(cat.getId())));

        // 4. 返回根节点 (parentId = 0)
        return grouped.getOrDefault(0L, new ArrayList<>());
    }
}
