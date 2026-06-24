package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.InterviewSchedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InterviewScheduleMapper extends BaseMapper<InterviewSchedule> {

    @Select("SELECT MAX(round_no) FROM job_interview WHERE delivery_id = #{deliveryId}")
    Integer selectMaxRound(@Param("deliveryId") Long deliveryId);

    @Select("SELECT * FROM job_interview WHERE delivery_id = #{deliveryId} ORDER BY round_no DESC LIMIT 1")
    InterviewSchedule selectLatestByDelivery(@Param("deliveryId") Long deliveryId);

    @Select("SELECT * FROM job_interview WHERE delivery_id = #{deliveryId} ORDER BY round_no ASC")
    List<InterviewSchedule> selectByDelivery(@Param("deliveryId") Long deliveryId);

    // 统计当前轮次之前仍处于待确认的数量
    @Select("SELECT COUNT(*) FROM job_interview WHERE delivery_id = #{deliveryId} AND round_no < #{roundNo} AND status = 0")
    int countPendingBefore(@Param("deliveryId") Long deliveryId, @Param("roundNo") Integer roundNo);
}
