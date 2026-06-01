package com.air.api.feignClient;

import com.air.api.dto.ProfileMemoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "aigc4lk-shared-memory-service",
        contextId = "memoryProfileFeign",
        path = "/memory/profile"
)
public interface MemoryProfileFeign {
    @GetMapping("/get/{userId}")
    ProfileMemoryDTO getProfile(@PathVariable String userId);
}