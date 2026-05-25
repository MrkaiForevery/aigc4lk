package com.air.platform.common.multimodal.vo;

import com.air.platform.common.enums.ModalityType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MultimodalRequest {
    private ModalityType modality;
    private String text;
    private String audio;
    private String image;
    private String video;
    private Map<String, Object> options;
    
    public boolean hasAudio() {
        return audio != null && !audio.isEmpty();
    }
    
    public boolean hasVideo() {
        return video != null && !video.isEmpty();
    }
    
    public boolean hasImage() {
        return image != null && !image.isEmpty();
    }
    
    public ModalityType getModality() {
        if (hasVideo()) return ModalityType.VIDEO;
        if (hasAudio()) return ModalityType.SPEECH;
        if (hasImage()) return ModalityType.VISION;
        return ModalityType.TEXT;
    }
}