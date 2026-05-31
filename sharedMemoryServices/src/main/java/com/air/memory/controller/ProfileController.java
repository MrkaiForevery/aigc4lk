package com.air.memory.controller;

import com.air.api.dto.ProfileMemoryDTO;
import com.air.api.feignClient.MemoryProfileFeign;
import com.air.api.feignClient.MemorySessionFeign;
import com.air.memory.entity.ProfileMemory;
import com.air.memory.service.MemoryManagerService;
import com.air.memory.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/memory/profile")
public class ProfileController implements MemoryProfileFeign {

    private final ProfileService profileService;

    @GetMapping("/{userId}")
    @Override
    public ProfileMemoryDTO getProfile(String userId) {
        return profileService.getProfile(userId);
    }
}