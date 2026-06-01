package com.air.memory.controller;

import com.air.api.dto.ProfileMemoryDTO;
import com.air.api.feignClient.MemoryProfileFeign;
import com.air.memory.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/memory/profile")
public class ProfileController implements MemoryProfileFeign {

    private final ProfileService profileService;

    @GetMapping("/get/{userId}")
    @Override
    public ProfileMemoryDTO getProfile(String userId) {
        return profileService.getProfile(userId);
    }
}