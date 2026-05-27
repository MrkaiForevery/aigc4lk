package com.air.memory.repository.vectorized;

import com.air.memory.entity.KnowledgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
     * 异步保存知识 —— 使用 ioExecutor，避免阻塞主线程
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> saveAsync(String content, String userId, String source) {
        save(content, userId, source);
        return CompletableFuture.completedFuture(null);
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
     * 异步搜索知识 —— 使用 ioExecutor，Commander 主链路使用此方法
     */
    @Async("ioExecutor")
    public CompletableFuture<List<KnowledgeResult>> searchAsync(String query, int limit) {
        List<KnowledgeResult> results = search(query, limit);
        return CompletableFuture.completedFuture(results);
    }

    /**
     * 批量异步保存知识（优化网络开销）
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> batchSaveAsync(List<Map<String, String>> documents) {
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
        return CompletableFuture.completedFuture(null);
    }
}