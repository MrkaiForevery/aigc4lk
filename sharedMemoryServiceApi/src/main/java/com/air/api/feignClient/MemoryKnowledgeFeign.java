package com.air.api.feignClient;

import com.air.api.dto.IdentityMemoryDTO;
import com.air.api.dto.KnowledgeResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@FeignClient(
        name = "aigc4lk-shared-memory-service",
        contextId = "memoryKnowledgeFeign",
        path = "/memory/Knowledge"
)
public interface MemoryKnowledgeFeign {

    @PostMapping("/search")
    List<KnowledgeResultDTO> searchKnowledge(@RequestBody Map<String, ? extends Serializable> query);
}