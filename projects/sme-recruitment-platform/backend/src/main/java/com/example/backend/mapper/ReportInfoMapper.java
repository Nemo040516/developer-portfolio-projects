package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.ReportInfo;
import com.example.backend.vo.AdminReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportInfoMapper extends BaseMapper<ReportInfo> {
    IPage<AdminReportVO> selectAdminReportList(Page<?> page,
                                               @Param("type") String type,
                                               @Param("status") Integer status);
}
