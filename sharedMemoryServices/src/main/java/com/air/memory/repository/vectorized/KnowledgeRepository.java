package com.air.memory.repository.vectorized;

import com.air.memory.config.ChromaDbClientConfig;
import com.air.memory.entity.KnowledgeIndex;
import com.air.memory.entity.KnowledgeResult;
import com.air.memory.mapper.KnowledgeIndexMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRepository {

    private static final String GET_URI = "/api/v1/collections/{collection}/get";
    private static final String ADD_URI = "/api/v1/collections/{collection}/add";
    private static final String DELETE_URI = "/api/v1/collections/{collection}/delete";

    private final ChromaDbClientConfig chromaDbClientConfig;
    private final ChromaVectorStore chromaVectorStore;
    private final ChromaApi chromaApi;
    private final RestClient chromaDegradeLowRestClient;

    private final KnowledgeIndexMapper indexMapper;  // 文档索引 Mapper
    private final ObjectMapper objectMapper;  // 直接注入

    private final EmbeddingModel embeddingModel;   // OpenAI / DashScope

    /**
     * 同步保存知识（同时维护索引）
     */
    public void save(String content, String userId, String source) {
        Document doc = new Document(content, Map.of(
                "user_id", userId,
                "source", source,
                "type", "knowledge"
        ));
        chromaVectorStore.add(List.of(doc));
        // 写入索引表
        KnowledgeIndex index = KnowledgeIndex.builder()
                .docId(doc.getId())
                .userId(userId)
                .source(source)
                .createdAt(LocalDateTime.now())
                .lastAccess(LocalDateTime.now())
                .status("active")
                .build();
        indexMapper.insert(index);

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
        List<Document> results = chromaVectorStore.similaritySearch(searchRequest);

        // 更新被命中文档的访问时间
        List<String> hitIds = results.stream().map(Document::getId).toList();
        if (!hitIds.isEmpty()) {
            indexMapper.batchUpdateLastAccess(hitIds, LocalDateTime.now());
        }

        List<KnowledgeResult> knowledgeResults = new ArrayList<>();

        //Spring AI 1.1.2 中，VectorStore 的 similaritySearch 不支持直接返回相似度,todo 后续可升级为使用 Chroma 的原生 API 获取精确分数
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            double similarity = 1.0 - (i * 0.05);  // 估算相似度：第1名≈1.0，第2名≈0.95，依此类推
            knowledgeResults.add(new KnowledgeResult(
                    doc.getId(),
                    doc.getFormattedContent(),
                    similarity,
                    doc.getMetadata()
            ));
        }
        return knowledgeResults;
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
        chromaVectorStore.add(docs);

        // ✅ 批量写入索引表
        List<KnowledgeIndex> indices = docs.stream()
                .map(doc -> KnowledgeIndex.builder()
                        .docId(doc.getId())
                        .userId(doc.getMetadata().get("user_id").toString())
                        .source(doc.getMetadata().get("source").toString())
                        .createdAt(LocalDateTime.now())
                        .lastAccess(LocalDateTime.now())
                        .status("active")
                        .build())
                .toList();
        indexMapper.batchInsert(indices);  // 需要在 Mapper 中添加这个方法
    }

    /**
     * 检查是否存在高度相似的文档
     *
     * @param content   待检查的内容
     * @param threshold 相似度阈值 (0.0 - 1.0)，Chroma 默认按余弦相似度排序
     * @return true 表示存在相似文档
     */
    public boolean existsSimilar(String content, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(content)
                .topK(1)
                .similarityThreshold(threshold)  // Chroma 自动过滤低于阈值的文档
                .build();
        List<Document> results = chromaVectorStore.similaritySearch(request);
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
        List<Document> results = chromaVectorStore.similaritySearch(request);
        return results.isEmpty() ? null : results.get(0);
    }


    /**
     * 降级低频知识（利用 ChromaApi + 外部索引表）
     * 这里使用restClient进行操作
     */
    public int degradeLowFrequency(int months) {
        String baseCollectionName = chromaDbClientConfig.getKnowledgeBaseCollection();
        String archiveCollectionName = chromaDbClientConfig.getKnowledgeArchiveCollection();

        LocalDateTime threshold = LocalDateTime.now().minusMonths(months);
        // 1. 从索引表中查出低频文档 ID
        List<String> lowFreqIds = indexMapper.selectLowFrequencyIds(threshold);
        if (lowFreqIds.isEmpty()) return 0;

        int count = 0;
        for (String docId : lowFreqIds) {
            try {
                // 1. 获取文档（调用 Chroma GET /api/v1/collections/{collection}/get）
                Map<String, Object> requestBody = Map.of("ids", List.of(docId));
                String response = chromaDegradeLowRestClient.post()
                        .uri(GET_URI, baseCollectionName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(response);
                JsonNode idsNode = root.path("ids");
                if (idsNode.isEmpty()) {
                    indexMapper.updateStatus(docId, "orphaned");
                    continue;
                }

                // 提取文档内容（假设 documents 字段是文本列表）
                String documentText = root.path("documents").get(0).asText();
                JsonNode metadatasNode = root.path("metadatas").get(0);
                Map<String, Object> metadata = objectMapper.convertValue(metadatasNode, Map.class);

                //  添加到归档集合（调用 POST /api/v1/collections/{collection}/add）
                // 注意：归档集合可能尚不存在，可以先创建或忽略（Chroma 自动创建）
                Map<String, Object> addBody = Map.of(
                        "ids", List.of(docId),  // 可以保留原ID或加后缀
                        "documents", List.of(documentText),
                        "metadatas", List.of(metadata)
                );
                chromaDegradeLowRestClient.post()
                        .uri(ADD_URI, archiveCollectionName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(addBody)
                        .retrieve()
                        .toBodilessEntity();

                //  从主集合删除（调用 POST /api/v1/collections/{collection}/delete）
                Map<String, Object> deleteBody = Map.of("ids", List.of(docId));
                chromaDegradeLowRestClient.post()
                        .uri(DELETE_URI, baseCollectionName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(deleteBody)
                        .retrieve()
                        .toBodilessEntity();

                // 更新索引表状态
                indexMapper.updateStatus(docId, "archived");
                count++;
            } catch (Exception e) {
                log.error("降级文档失败: {}", docId, e);
            }
        }
        return count;
    }

}