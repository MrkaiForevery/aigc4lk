package com.air.memory.controller;

import com.air.api.dto.KnowledgeResultDTO;
import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.feignClient.MemoryKnowledgeFeign;
import com.air.api.feignClient.MemoryPreferenceFeign;
import com.air.memory.service.KnowledgeService;
import com.air.memory.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/memory/Knowledge")
public class KnowledgeController implements MemoryKnowledgeFeign {

    private final KnowledgeService knowledgeService;

    @PostMapping("/search")
    @Override
    public List<KnowledgeResultDTO> searchKnowledge(Map<String, ? extends Serializable> query) {
       return knowledgeService.search(query);
    }
}