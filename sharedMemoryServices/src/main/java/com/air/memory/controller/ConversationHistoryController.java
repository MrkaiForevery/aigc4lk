package com.air.memory.controller;

import com.air.api.dto.conversation.ConversationHistoryDTO;
import com.air.api.feignClient.MemoryConversationHistoryFeign;
import com.air.memory.service.ConversationHistoryManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@RestController
@RequestMapping("/memory/conversation/history")
@RequiredArgsConstructor
public class ConversationHistoryController implements MemoryConversationHistoryFeign {

    private final ConversationHistoryManagerService conversationHistoryManagerService;

    @Override
    public void saveOne(ConversationHistoryDTO conversationHistoryDTO) throws SQLException {
        conversationHistoryManagerService.doSaveOneConversationHistory(conversationHistoryDTO);
    }
}
