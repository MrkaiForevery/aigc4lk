package com.air.mcp.core.server;

import com.air.mcp.annotations.McpTool;
import com.air.mcp.annotations.McpToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.List;

@Slf4j
@Component
public class SpeechMcpServer {
    
    /**
     * 语音识别工具
     */
    @McpTool(name = "recognize_speech", description = "Convert speech to text")
    public ASRResult recognizeSpeech(
            @McpToolParam(description = "Audio URL or Base64") String audioInput,
            @McpToolParam(description = "Source language", required = false) String language,
            @McpToolParam(description = "Enable punctuation", required = false) Boolean enablePunctuation) {
        
        log.info("Recognizing speech from audio: {}", truncate(audioInput, 50));
        
        return ASRResult.builder()
            .success(true)
            .text("Recognized speech text")
            .language(language != null ? language : "zh-CN")
            .confidence(0.94)
            .words(List.of(
                ASRResult.WordInfo.builder().text("Recognized").startTime(0.0).endTime(0.5).confidence(0.98).build(),
                ASRResult.WordInfo.builder().text("speech").startTime(0.5).endTime(0.9).confidence(0.95).build(),
                ASRResult.WordInfo.builder().text("text").startTime(0.9).endTime(1.2).confidence(0.92).build()
            ))
            .duration(1.2)
            .build();
    }
    
    /**
     * 流式语音识别工具
     */
    @McpTool(name = "recognize_speech_streaming", description = "Real-time streaming speech recognition")
    public Flux<ASRStreamChunk> recognizeSpeechStreaming(
            @McpToolParam(description = "Audio stream") Flux<byte[]> audioStream,
            @McpToolParam(description = "Source language", required = false) String language) {
        
        log.info("Starting streaming speech recognition");
        
        return audioStream
            .buffer(10)
            .map(chunk -> ASRStreamChunk.builder()
                .text("partial text")
                .isFinal(false)
                .confidence(0.85)
                .build());
    }
    
    /**
     * 语音合成工具
     */
    @McpTool(name = "synthesize_speech", description = "Convert text to speech")
    public TTSResult synthesizeSpeech(
            @McpToolParam(description = "Text to synthesize") String text,
            @McpToolParam(description = "Voice ID", required = false) String voiceId,
            @McpToolParam(description = "Speech speed", required = false) Double speed,
            @McpToolParam(description = "Output format", required = false) String format) {
        
        log.info("Synthesizing speech for text: {}", truncate(text, 100));
        
        return TTSResult.builder()
            .success(true)
            .audioBase64("base64_encoded_audio")
            .voiceId(voiceId != null ? voiceId : "zh_female_1")
            .duration(calculateDuration(text))
            .format(format != null ? format : "mp3")
            .text(text)
            .build();
    }
    
    /**
     * 语言识别工具
     */
    @McpTool(name = "detect_language", description = "Detect language from audio")
    public LanguageDetectionResult detectLanguage(
            @McpToolParam(description = "Audio URL or Base64") String audioInput) {
        
        log.info("Detecting language from audio: {}", truncate(audioInput, 50));
        
        return LanguageDetectionResult.builder()
            .success(true)
            .language("zh-CN")
            .confidence(0.98)
            .alternatives(List.of())
            .build();
    }
    
    private double calculateDuration(String text) {
        // 粗略估算：中文约3字/秒，英文约2.5词/秒
        return text.length() / 3.0;
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    
    // ==================== 结果类 ====================
    
    @lombok.Data
    @lombok.Builder
    public static class ASRResult {
        private boolean success;
        private String text;
        private String language;
        private double confidence;
        private List<WordInfo> words;
        private double duration;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class WordInfo {
            private String text;
            private double startTime;
            private double endTime;
            private double confidence;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ASRStreamChunk {
        private String text;
        private boolean isFinal;
        private double confidence;
        private double timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TTSResult {
        private boolean success;
        private String audioBase64;
        private String voiceId;
        private double duration;
        private String format;
        private String text;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class LanguageDetectionResult {
        private boolean success;
        private String language;
        private double confidence;
        private List<LanguageAlternative> alternatives;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class LanguageAlternative {
            private String language;
            private double confidence;
        }
    }
}