package com.air.api.feignClient;

import com.air.api.dto.IdentityMemoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "aigc4lk-shared-memory-service",
        contextId = "memoryIdentityFeign",
        path = "/memory/identity"
)
public interface MemoryIdentityFeign {
    @GetMapping("/get/{userId}")
    IdentityMemoryDTO get(@PathVariable String userId);

    @PostMapping
    void save(@RequestBody IdentityMemoryDTO identity);

    @DeleteMapping("/{userId}")
    void delete(@PathVariable String userId);
}