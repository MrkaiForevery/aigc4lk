package com.air.memory.controller;

import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.feignClient.MemoryPreferenceFeign;
import com.air.memory.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/memory/preference")
public class PreferenceController implements MemoryPreferenceFeign {

    private final PreferenceService preferenceService;

    @GetMapping("/get/{userId}")
    @Override
    public PreferenceMemoryDTO getPreference(String userId) {
        log.info("开始执行getPreference任务-----------------");
        return preferenceService.getPreference(userId);
    }
}