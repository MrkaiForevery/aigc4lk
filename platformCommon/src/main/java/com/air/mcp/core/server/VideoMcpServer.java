package com.air.mcp.core.server;

import com.air.mcp.annotations.McpTool;
import com.air.mcp.annotations.McpToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
public class VideoMcpServer {
    
    /**
     * 视频分析工具
     */
    @McpTool(name = "analyze_video", description = "Analyze video content")
    public VideoAnalysisResult analyzeVideo(
            @McpToolParam(description = "Video URL or path") String videoInput,
            @McpToolParam(description = "Analysis type", required = false) String analysisType) {
        
        log.info("Analyzing video: {}", truncate(videoInput, 50));
        
        return VideoAnalysisResult.builder()
            .success(true)
            .summary("Video summary")
            .duration(120.0)
            .sceneCount(5)
            .scenes(List.of(
                VideoAnalysisResult.SceneInfo.builder().timestamp(0.0).description("Opening scene").build(),
                VideoAnalysisResult.SceneInfo.builder().timestamp(30.0).description("Main content").build(),
                VideoAnalysisResult.SceneInfo.builder().timestamp(90.0).description("Conclusion").build()
            ))
            .build();
    }
    
    /**
     * 视频摘要工具
     */
    @McpTool(name = "summarize_video", description = "Generate video summary")
    public VideoSummaryResult summarizeVideo(
            @McpToolParam(description = "Video URL or path") String videoInput,
            @McpToolParam(description = "Summary length", required = false) String length) {
        
        log.info("Generating video summary: {}", truncate(videoInput, 50));
        
        return VideoSummaryResult.builder()
            .success(true)
            .summary("Concise video summary")
            .keyMoments(List.of(
                VideoSummaryResult.KeyMoment.builder().timestamp(10.0).description("Key moment 1").build(),
                VideoSummaryResult.KeyMoment.builder().timestamp(45.0).description("Key moment 2").build()
            ))
            .thumbnailBase64("thumbnail_base64")
            .duration(120.0)
            .build();
    }
    
    /**
     * 动作识别工具
     */
    @McpTool(name = "recognize_actions", description = "Recognize actions in video")
    public ActionRecognitionResult recognizeActions(
            @McpToolParam(description = "Video URL or path") String videoInput,
            @McpToolParam(description = "Target actions", required = false) List<String> targetActions) {
        
        log.info("Recognizing actions in video: {}", truncate(videoInput, 50));
        
        List<ActionRecognitionResult.ActionSegment> actions = List.of(
            ActionRecognitionResult.ActionSegment.builder()
                .actionType("walking")
                .startTime(5.0)
                .endTime(15.0)
                .confidence(0.92)
                .build(),
            ActionRecognitionResult.ActionSegment.builder()
                .actionType("talking")
                .startTime(20.0)
                .endTime(45.0)
                .confidence(0.88)
                .build()
        );
        
        return ActionRecognitionResult.builder()
            .success(true)
            .actions(actions)
            .duration(120.0)
            .build();
    }
    
    /**
     * 文本转视频工具
     */
    @McpTool(name = "text_to_video", description = "Generate video from text description")
    public VideoGenerationResult textToVideo(
            @McpToolParam(description = "Text prompt") String prompt,
            @McpToolParam(description = "Duration in seconds", required = false) Integer duration,
            @McpToolParam(description = "Resolution", required = false) String resolution) {
        
        log.info("Generating video from prompt: {}", truncate(prompt, 100));
        
        return VideoGenerationResult.builder()
            .success(true)
            .videoBase64("generated_video_base64")
            .duration(duration != null ? duration : 5)
            .resolution(resolution != null ? resolution : "720p")
            .prompt(prompt)
            .build();
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    
    // ==================== 结果类 ====================
    
    @lombok.Data
    @lombok.Builder
    public static class VideoAnalysisResult {
        private boolean success;
        private String summary;
        private double duration;
        private int sceneCount;
        private List<SceneInfo> scenes;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class SceneInfo {
            private double timestamp;
            private String description;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VideoSummaryResult {
        private boolean success;
        private String summary;
        private List<KeyMoment> keyMoments;
        private String thumbnailBase64;
        private double duration;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class KeyMoment {
            private double timestamp;
            private String description;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ActionRecognitionResult {
        private boolean success;
        private List<ActionSegment> actions;
        private double duration;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class ActionSegment {
            private String actionType;
            private double startTime;
            private double endTime;
            private double confidence;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VideoGenerationResult {
        private boolean success;
        private String videoBase64;
        private int duration;
        private String resolution;
        private String prompt;
        private String error;
    }
}