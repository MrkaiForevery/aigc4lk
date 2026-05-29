package com.air.api.feignClient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "aigc4lk-shared-memory-service",
        contextId = "memorySessionFeign",
        path = "/memory/session"
)
public interface MemorySessionFeign {

    @PostMapping("/{sessionId}/summarize")
    String summarizeSession(@PathVariable String sessionId, @RequestParam String userId);
}
