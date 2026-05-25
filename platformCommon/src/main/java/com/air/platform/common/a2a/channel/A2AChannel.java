package com.air.platform.common.a2a.channel;

import com.air.platform.common.a2a.enums.A2AMessageType;
import com.air.platform.common.a2a.protocol.A2AMessage;
import com.air.platform.common.a2a.protocol.A2AResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Component
public class A2AChannel {
    
    private final Map<String, CompletableFuture<A2AResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<A2AMessageType, Function<A2AMessage, A2AResponse>> messageHandlers = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("A2A Channel initialized");
    }
    
    /**
     * 注册消息处理器
     */
    public void registerHandler(A2AMessageType type, Function<A2AMessage, A2AResponse> handler) {
        messageHandlers.put(type, handler);
        log.debug("Registered handler for message type: {}", type);
    }
    
    /**
     * 发送消息
     */
    public CompletableFuture<A2AResponse> send(A2AMessage message) {
        log.debug("Sending A2A message: type={}, from={}, to={}", 
            message.getMessageType(), message.getSenderAgentId(), message.getReceiverAgentId());
        
        CompletableFuture<A2AResponse> future = new CompletableFuture<>();
        pendingRequests.put(message.getMessageId(), future);
        
        // 实际实现：通过Nacos A2A客户端发送
        try {
            A2AResponse response = doSend(message);
            future.complete(response);
        } catch (Exception e) {
            future.completeExceptionally(e);
        } finally {
            pendingRequests.remove(message.getMessageId());
        }
        
        return future;
    }
    
    /**
     * 接收消息
     */
    public A2AResponse receive(A2AMessage message) {
        log.debug("Receiving A2A message: type={}, from={}", 
            message.getMessageType(), message.getSenderAgentId());
        
        Function<A2AMessage, A2AResponse> handler = messageHandlers.get(message.getMessageType());
        if (handler == null) {
            log.warn("No handler registered for message type: {}", message.getMessageType());
            return A2AResponse.failure(message.getMessageId(), "A2A-004", "No handler registered");
        }
        
        try {
            return handler.apply(message);
        } catch (Exception e) {
            log.error("Error handling A2A message", e);
            return A2AResponse.failure(message.getMessageId(), "A2A-005", e.getMessage());
        }
    }
    
    /**
     * 请求帮助（委托任务给其他Agent）
     */
    public A2AResponse requestHelp(String targetAgentId, Object task) {
        A2AMessage request = A2AMessage.builder()
            .messageType(A2AMessageType.TASK_DELEGATION)
            .receiverAgentId(targetAgentId)
            .payload(task)
            .build();
        
        CompletableFuture<A2AResponse> future = send(request);
        try {
            return future.get();
        } catch (Exception e) {
            log.error("Failed to request help from: {}", targetAgentId, e);
            return A2AResponse.failure(request.getMessageId(), "A2A-002", e.getMessage());
        }
    }
    
    /**
     * 广播消息
     */
    public void broadcast(A2AMessage message) {
        message.setMessageType(A2AMessageType.BROADCAST);
        message.setReceiverAgentId(null);
        
        log.info("Broadcasting A2A message: type={}, from={}", 
            message.getMessageType(), message.getSenderAgentId());
        
        // 实际实现：向所有订阅的Agent发送
        doBroadcast(message);
    }
    
    private A2AResponse doSend(A2AMessage message) {
        // 实际实现：通过Nacos A2A客户端发送
        return A2AResponse.success(message.getMessageId(), "sent");
    }
    
    private void doBroadcast(A2AMessage message) {
        // 实际实现：向所有订阅的Agent广播
    }
}