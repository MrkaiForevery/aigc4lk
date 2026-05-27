package com.air.memory.mapper;

import com.air.memory.entity.BehaviorRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface BehaviorRecordMapper extends BaseMapper<BehaviorRecord> {
    @Select("SELECT * FROM memory_behavior WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<BehaviorRecord> selectRecent(@Param("userId") String userId, @Param("limit") int limit);
}