package com.air.platform.common.a2a.protocol;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class A2AResponse {
    
    private String messageId;
    private String correlationId;
    private ResponseStatus status;
    private Object payload;
    private String errorCode;
    private String errorMessage;
    private long timestamp;
    
    public static A2AResponse success(String correlationId, Object payload) {
        return A2AResponse.builder()
            .correlationId(correlationId)
            .status(ResponseStatus.SUCCESS)
            .payload(payload)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static A2AResponse failure(String correlationId, String errorCode, String errorMessage) {
        return A2AResponse.builder()
            .correlationId(correlationId)
            .status(ResponseStatus.FAILURE)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static A2AResponse timeout(String correlationId) {
        return failure(correlationId, "A2A-002", "A2A communication timeout");
    }
    
    public enum ResponseStatus {
        SUCCESS,
        FAILURE,
        TIMEOUT,
        RETRY
    }
}