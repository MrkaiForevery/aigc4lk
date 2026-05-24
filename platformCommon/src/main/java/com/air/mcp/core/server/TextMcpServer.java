package com.air.mcp.core.server;

import com.air.mcp.annotations.McpTool;
import com.air.mcp.annotations.McpToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TextMcpServer {
    
    /**
     * 文件读取工具
     */
    @McpTool(name = "read_file", description = "Read file content from local filesystem")
    public FileReadResult readFile(
            @McpToolParam(description = "File path") String filePath,
            @McpToolParam(description = "Encoding", required = false) String encoding) {
        
        log.info("Reading file: {}", filePath);
        
        try {
            String content = doReadFile(filePath, encoding);
            return FileReadResult.builder()
                .success(true)
                .content(content)
                .filePath(filePath)
                .fileSize(content.length())
                .build();
        } catch (Exception e) {
            log.error("Failed to read file: {}", filePath, e);
            return FileReadResult.builder()
                .success(false)
                .error(e.getMessage())
                .filePath(filePath)
                .build();
        }
    }
    
    /**
     * 数据库查询工具
     */
    @McpTool(name = "query_database", description = "Execute database query")
    public DatabaseQueryResult queryDatabase(
            @McpToolParam(description = "SQL query") String sql,
            @McpToolParam(description = "Query parameters", required = false) List<Object> params,
            @McpToolParam(description = "Connection name", required = false) String connectionName) {
        
        log.info("Executing database query: {}", sql);
        
        // 实际实现：执行数据库查询
        return DatabaseQueryResult.builder()
            .success(true)
            .rows(List.of(Map.of("result", "query_result")))
            .rowCount(1)
            .build();
    }
    
    /**
     * Web搜索工具
     */
    @McpTool(name = "search_web", description = "Search the web")
    public WebSearchResult searchWeb(
            @McpToolParam(description = "Search query") String query,
            @McpToolParam(description = "Number of results", required = false) Integer limit) {
        
        log.info("Web search: {}", query);
        
        // 实际实现：调用搜索API
        return WebSearchResult.builder()
            .success(true)
            .query(query)
            .results(List.of())
            .totalCount(0)
            .build();
    }
    
    private String doReadFile(String filePath, String encoding) {
        // 实际实现：读取文件
        return "file_content";
    }
    
    // ==================== 结果类 ====================
    
    @lombok.Data
    @lombok.Builder
    public static class FileReadResult {
        private boolean success;
        private String content;
        private String filePath;
        private int fileSize;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DatabaseQueryResult {
        private boolean success;
        private List<Map<String, Object>> rows;
        private int rowCount;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class WebSearchResult {
        private boolean success;
        private String query;
        private List<SearchResultItem> results;
        private int totalCount;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class SearchResultItem {
            private String title;
            private String url;
            private String snippet;
        }
    }
}