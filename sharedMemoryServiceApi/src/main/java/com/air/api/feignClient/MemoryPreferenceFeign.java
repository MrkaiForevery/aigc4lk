package com.air.api.feignClient;

import com.air.api.dto.IdentityMemoryDTO;
import com.air.api.dto.PreferenceMemoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "aigc4lk-shared-memory-service",
        contextId = "memoryPreferenceFeign",
        path = "/memory/preference"
)
public interface MemoryPreferenceFeign {

    @GetMapping("/{userId}")
    PreferenceMemoryDTO getPreference(@PathVariable String userId);
}