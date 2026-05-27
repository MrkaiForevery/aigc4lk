package com.air.memory.mapper;

import com.air.memory.entity.DecisionRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

// DecisionRecordMapper.java
@Mapper
public interface DecisionRecordMapper extends BaseMapper<DecisionRecord> {
    @Select("SELECT intent_scenario, COUNT(*) FROM memory_decision GROUP BY intent_scenario")
    List<Map<String, Object>> countByScenario();
}