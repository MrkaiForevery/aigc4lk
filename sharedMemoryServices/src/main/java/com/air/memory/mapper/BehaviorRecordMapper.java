package com.air.memory.mapper;

import com.air.memory.entity.BehaviorRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BehaviorRecordMapper extends BaseMapper<BehaviorRecord> {

    @Select("SELECT * FROM memory_behavior WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<BehaviorRecord> selectRecent(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 删除指定时间之前的记录
     */
    @Delete("DELETE FROM memory_behavior WHERE created_at < #{threshold}")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);

    /**
     * 将旧数据归档到历史表（需要先创建 archive 表）
     */
    @Insert("INSERT INTO memory_behavior_archive SELECT * FROM memory_behavior WHERE created_at < #{threshold}")
    int archiveToHistory(@Param("threshold") LocalDateTime threshold);
}