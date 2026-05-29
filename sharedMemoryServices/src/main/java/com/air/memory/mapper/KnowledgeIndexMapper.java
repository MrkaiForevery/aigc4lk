package com.air.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.air.memory.entity.KnowledgeIndex;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeIndexMapper extends BaseMapper<KnowledgeIndex> {

    /**
     * 查询低频文档 ID（超过指定时间未访问，且状态为 active）
     * @param threshold 最后访问时间阈值
     * @return 文档 ID 列表
     */
    @Select("SELECT doc_id FROM knowledge_index WHERE status = 'active' AND last_access < #{threshold}")
    List<String> selectLowFrequencyIds(@Param("threshold") LocalDateTime threshold);

    /**
     * 更新文档状态
     * @param docId 文档 ID
     * @param status 新状态（如 'archived'）
     */
    @Update("UPDATE knowledge_index SET status = #{status} WHERE doc_id = #{docId}")
    int updateStatus(@Param("docId") String docId, @Param("status") String status);

    /**
     * 批量更新文档最后访问时间（当搜索结果命中时）
     * @param docIds 文档 ID 列表
     * @param now 当前时间
     */
    @Update("<script>" +
            "UPDATE knowledge_index SET last_access = #{now} WHERE doc_id IN " +
            "<foreach collection='docIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchUpdateLastAccess(@Param("docIds") List<String> docIds, @Param("now") LocalDateTime now);

    /**
     * 批量插入索引记录
     */
    @Insert("<script>" +
            "INSERT INTO knowledge_index (doc_id, user_id, source, created_at, last_access, status) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.docId}, #{item.userId}, #{item.source}, #{item.createdAt}, #{item.lastAccess}, #{item.status})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<KnowledgeIndex> indices);
}