package com.air.a2a.core.protocol;

import com.air.a2a.core.enums.A2AMessageType;
import lombok.Builder;
import lombok.Data;
import java.io.Serializable;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class A2AMessage implements Serializable {
    
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();
    
    private String senderAgentId;
    private String receiverAgentId;
    private A2AMessageType messageType;
    private Map<String, String> headers;
    private Object payload;
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    
    private String correlationId;
    
    @Builder.Default
    private int priority = 5;
    
    private Duration ttl;
    
    @Builder.Default
    private boolean requiresAck = true;
    
    @Builder.Default
    private int retryCount = 0;
    
    public boolean isExpired() {
        if (ttl == null) return false;
        return System.currentTimeMillis() - timestamp > ttl.toMillis();
    }
    
    public A2AMessage withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    public A2AMessage incrementRetry() {
        this.retryCount++;
        return this;
    }
}