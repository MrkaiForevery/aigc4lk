package com.air.memory.repository.vectorized;

import com.air.memory.entity.KnowledgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRepository {

    private final VectorStore vectorStore;        // Chroma
    private final EmbeddingModel embeddingModel;   // OpenAI / DashScope

    /**
     * 同步保存知识（保留，给不需要异步的场景使用）
     */
    public void save(String content, String userId, String source) {
        Document doc = new Document(content, Map.of(
                "user_id", userId,
                "source", source,
                "type", "knowledge"
        ));
        vectorStore.add(List.of(doc));
        log.debug("Knowledge saved: userId={}, source={}", userId, source);
    }

    /**
     * 同步搜索知识（保留，给不需要异步的场景使用）
     */
    public List<KnowledgeResult> search(String query, int limit) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .build();
        List<Document> results = vectorStore.similaritySearch(searchRequest);
        return results.stream()
                .map(doc -> new KnowledgeResult(
                        doc.getId(),
                        doc.getFormattedContent(),
                        doc.getMetadata()))
                .toList();
    }

    /**
     * 批量保存知识
     */
    public void batchSaveAsync(List<Map<String, String>> documents) {
        List<Document> docs = documents.stream()
                .map(doc -> new Document(
                        doc.get("content"),
                        Map.of(
                                "user_id", doc.getOrDefault("userId", "system"),
                                "source", doc.getOrDefault("source", "unknown"),
                                "type", "knowledge"
                        )))
                .toList();
        vectorStore.add(docs);
        log.debug("Batch knowledge saved: count={}", docs.size());
    }

    /**
     * 检查是否存在高度相似的文档
     * @param content 待检查的内容
     * @param threshold 相似度阈值 (0.0 - 1.0)，Chroma 默认按余弦相似度排序
     * @return true 表示存在相似文档
     */
    public boolean existsSimilar(String content, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(content)
                .topK(1)
                .similarityThreshold(threshold)  // Chroma 自动过滤低于阈值的文档
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return !results.isEmpty();
    }

    /**
     * 获取最相似的文档及其相似度（近似值，因为 Chroma 返回的 Document 不直接包含分数）
     * 我们可以通过二次调用 search 获取第一条，并认为它是相似的
     */
    public Document getMostSimilar(String content, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(content)
                .topK(1)
                .similarityThreshold(threshold)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.isEmpty() ? null : results.get(0);
    }


// KnowledgeRepository.java 中新增

    /**
     * TODO 待完善
     * 降级低频知识（示例实现，需根据实际 Chroma 操作调整）
     * @param months 超过此月数未访问的知识将被降级
     * @return 降级的文档数
     */
    public int degradeLowFrequency(int months) {
        // 方案1：如果你的文档 metadata 中存储了 last_access_time
        // 可以通过 Chroma 的 get 方法筛选出低频文档，然后移动到 archive 集合
        // 方案2：如果没有记录访问时间，可以跳过此功能或使用 Chroma 的 TTL 特性

        // 这里提供框架代码，具体实现取决于 Chroma 客户端的 API
        log.info("低频知识降级功能待实现（需配合 Chroma metadata 中的访问时间）");
        return 0;
    }
}