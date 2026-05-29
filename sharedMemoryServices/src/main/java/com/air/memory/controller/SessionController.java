package com.air.memory.controller;

import com.air.api.feignClient.MemorySessionFeign;
import com.air.memory.service.MemoryManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/memory/session")
public class SessionController implements MemorySessionFeign {

    private final MemoryManagerService memoryManagerService;

    @PostMapping("/{sessionId}/summarize")
    @Override
    public String summarizeSession(String sessionId, String userId) {
        return memoryManagerService.summarizeSession(sessionId, userId);
    }
}