package com.air.mcp.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface TableSchemaMapper {

    /**
     * 一次性获取指定前缀的所有表的所有列信息
     * 注意 PostgreSQL 的字符串拼接用 ||，这里使用 CONCAT 确保兼容性
     */
    @Select("SELECT table_name, column_name, data_type, character_maximum_length, is_nullable " +
            "FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name LIKE CONCAT(#{tablePrefix}, '%') " +
            "ORDER BY table_name, ordinal_position")
    List<Map<String, Object>> getAllTablesColumnsByTablePrefix(@Param("tablePrefix") String tablePrefix);
}