package com.air.api.feignClient;

import com.air.api.dto.conversation.ConversationHistoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.sql.SQLException;

@FeignClient(
        name = "aigc4lk-shared-memory-service",
        contextId = "memoryConversationHistoryFeign",
        path = "/memory/conversation/history"
)
public interface MemoryConversationHistoryFeign {

    @PostMapping("/saveOne")
    void saveOne(@RequestBody ConversationHistoryDTO conversationHistoryDTO) throws SQLException;
}
