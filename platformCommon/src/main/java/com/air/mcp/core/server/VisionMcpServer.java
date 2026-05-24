package com.air.mcp.core.server;

import com.air.mcp.annotations.McpTool;
import com.air.mcp.annotations.McpToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class VisionMcpServer {
    
    /**
     * 图像分析工具
     */
    @McpTool(name = "analyze_image", description = "Analyze and describe image content")
    public ImageAnalysisResult analyzeImage(
            @McpToolParam(description = "Image URL or Base64") String imageInput,
            @McpToolParam(description = "Analysis question", required = false) String question) {
        
        log.info("Analyzing image: {}", truncate(imageInput, 50));
        
        return ImageAnalysisResult.builder()
            .success(true)
            .description("Image description")
            .tags(List.of("tag1", "tag2"))
            .confidence(0.95)
            .build();
    }
    
    /**
     * OCR文字识别工具
     */
    @McpTool(name = "extract_text_ocr", description = "Extract text from image using OCR")
    public OCRResult extractText(
            @McpToolParam(description = "Image URL or Base64") String imageInput,
            @McpToolParam(description = "Language", required = false) String language) {
        
        log.info("Extracting text from image: {}", truncate(imageInput, 50));
        
        return OCRResult.builder()
            .success(true)
            .text("Extracted text content")
            .language(language != null ? language : "zh-CN")
            .confidence(0.92)
            .regions(List.of())
            .build();
    }
    
    /**
     * 目标检测工具
     */
    @McpTool(name = "detect_objects", description = "Detect objects in image")
    public ObjectDetectionResult detectObjects(
            @McpToolParam(description = "Image URL or Base64") String imageInput,
            @McpToolParam(description = "Target classes filter", required = false) List<String> targetClasses) {
        
        log.info("Detecting objects in image: {}", truncate(imageInput, 50));
        
        List<ObjectDetectionResult.DetectedObject> objects = List.of(
            ObjectDetectionResult.DetectedObject.builder()
                .className("person")
                .confidence(0.95)
                .bbox(BoundingBox.builder().x(100).y(150).width(80).height(200).build())
                .build()
        );
        
        return ObjectDetectionResult.builder()
            .success(true)
            .objects(objects)
            .objectCount(objects.size())
            .build();
    }
    
    /**
     * 人脸检测工具
     */
    @McpTool(name = "detect_faces", description = "Detect faces in image")
    public FaceDetectionResult detectFaces(
            @McpToolParam(description = "Image URL or Base64") String imageInput,
            @McpToolParam(description = "Extract attributes", required = false) Boolean extractAttributes) {
        
        log.info("Detecting faces in image: {}", truncate(imageInput, 50));
        
        return FaceDetectionResult.builder()
            .success(true)
            .faceCount(1)
            .faces(List.of())
            .build();
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    
    // ==================== 结果类 ====================
    
    @lombok.Data
    @lombok.Builder
    public static class ImageAnalysisResult {
        private boolean success;
        private String description;
        private List<String> tags;
        private double confidence;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class OCRResult {
        private boolean success;
        private String text;
        private String language;
        private double confidence;
        private List<OCRRegion> regions;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class OCRRegion {
            private String text;
            private BoundingBox bbox;
            private double confidence;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ObjectDetectionResult {
        private boolean success;
        private List<DetectedObject> objects;
        private int objectCount;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class DetectedObject {
            private String className;
            private double confidence;
            private BoundingBox bbox;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class FaceDetectionResult {
        private boolean success;
        private int faceCount;
        private List<DetectedFace> faces;
        private String error;
        
        @lombok.Data
        @lombok.Builder
        public static class DetectedFace {
            private BoundingBox bbox;
            private FaceAttributes attributes;
            private double confidence;
            
            @lombok.Data
            @lombok.Builder
            public static class FaceAttributes {
                private String gender;
                private Integer age;
                private String expression;
                private Double smileConfidence;
            }
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BoundingBox {
        private int x;
        private int y;
        private int width;
        private int height;
    }
}