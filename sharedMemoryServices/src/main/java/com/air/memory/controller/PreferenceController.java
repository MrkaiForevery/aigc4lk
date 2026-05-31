package com.air.memory.controller;

import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.feignClient.MemoryPreferenceFeign;
import com.air.memory.service.PreferenceService;
import com.air.memory.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/memory/preference")
public class PreferenceController implements MemoryPreferenceFeign {

    private final PreferenceService preferenceService;

    @GetMapping("/{userId}")
    @Override
    public PreferenceMemoryDTO getPreference(String userId) {
        return preferenceService.getPreference(userId);
    }
}