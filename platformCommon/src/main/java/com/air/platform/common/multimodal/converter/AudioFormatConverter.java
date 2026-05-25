package com.air.platform.common.multimodal.converter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AudioFormatConverter {
    
    public AudioData normalize(String audioInput) {
        AudioData audioData = new AudioData();
        
        if (audioInput.startsWith("http://") || audioInput.startsWith("https://")) {
            audioData.setUrl(audioInput);
            audioData.setType(AudioType.URL);
        } else {
            audioData.setBase64Data(audioInput);
            audioData.setType(AudioType.BASE64);
        }
        
        return audioData;
    }
    
    public byte[] toBytes(AudioData audioData) {
        if (audioData.getBase64Data() != null) {
            return java.util.Base64.getDecoder().decode(audioData.getBase64Data());
        }
        if (audioData.getUrl() != null) {
            return downloadAudio(audioData.getUrl());
        }
        throw new IllegalArgumentException("No valid audio data");
    }
    
    private byte[] downloadAudio(String url) {
        log.debug("Downloading audio from: {}", url);
        return new byte[0];
    }
    
    @Data
    public static class AudioData {
        private AudioType type;
        private String format;
        private Integer sampleRate;
        private String url;
        private String base64Data;
        private Long duration;
    }
    
    public enum AudioType {
        URL, BASE64, FILE
    }
}