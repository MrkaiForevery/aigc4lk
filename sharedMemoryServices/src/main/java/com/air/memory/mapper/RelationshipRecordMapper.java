package com.air.memory.mapper;

import com.air.memory.entity.RelationshipRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RelationshipRecordMapper extends BaseMapper<RelationshipRecord> {
    List<RelationshipRecord> findByUserId(@Param("userId") String userId);
    List<RelationshipRecord> findByRelatedUser(@Param("relatedUser") String relatedUser);
}