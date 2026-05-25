package com.air.platform.common.multimodal.converter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ImageFormatConverter {
    
    private static final Pattern BASE64_PATTERN = 
        Pattern.compile("^data:image/(\\w+);base64,(.+)$");
    
    public ImageData normalize(String imageInput) {
        ImageData imageData = new ImageData();
        
        if (imageInput.startsWith("http://") || imageInput.startsWith("https://")) {
            imageData.setUrl(imageInput);
            imageData.setType(ImageType.URL);
        } else if (imageInput.contains("base64")) {
            var matcher = BASE64_PATTERN.matcher(imageInput);
            if (matcher.find()) {
                imageData.setFormat(matcher.group(1));
                imageData.setBase64Data(matcher.group(2));
                imageData.setType(ImageType.BASE64);
            } else {
                imageData.setBase64Data(imageInput);
                imageData.setType(ImageType.BASE64);
            }
        } else {
            imageData.setBase64Data(imageInput);
            imageData.setType(ImageType.BASE64);
        }
        
        return imageData;
    }
    
    public String toBase64(ImageData imageData) {
        if (imageData.getBase64Data() != null) {
            return imageData.getBase64Data();
        }
        if (imageData.getUrl() != null) {
            // 从URL下载并转换
            return downloadAndConvert(imageData.getUrl());
        }
        throw new IllegalArgumentException("No valid image data");
    }
    
    private String downloadAndConvert(String url) {
        // 实现URL下载和Base64转换
        log.debug("Downloading image from: {}", url);
        return "";
    }
    
    @Data
    public static class ImageData {
        private ImageType type;
        private String format;
        private String url;
        private String base64Data;
        private Dimensions dimensions;
        
        public String getBase64Data() {
            return base64Data;
        }
    }
    
    public enum ImageType {
        URL, BASE64, FILE
    }
    
    @Data
    public static class Dimensions {
        private int width;
        private int height;
    }
}