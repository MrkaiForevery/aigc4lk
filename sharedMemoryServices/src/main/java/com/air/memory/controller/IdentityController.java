package com.air.memory.controller;

import cn.hutool.core.bean.BeanUtil;
import com.air.api.dto.IdentityMemoryDTO;
import com.air.api.feignClient.MemoryIdentityFeign;
import com.air.memory.service.IdentityMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/memory/identity")
@RequiredArgsConstructor
public class IdentityController implements MemoryIdentityFeign {

    private final IdentityMemoryService identityMemoryService;

    @GetMapping("/get/{userId}")
    public IdentityMemoryDTO get(@PathVariable String userId) {
        IdentityMemoryDTO memoryDTO = IdentityMemoryDTO.builder().build();
        BeanUtil.copyProperties(identityMemoryService.getIdentity(userId),memoryDTO);
        return memoryDTO;
    }

    @PostMapping
    public void save(@RequestBody IdentityMemoryDTO entity) {
        identityMemoryService.saveIdentity(entity);
    }

    @DeleteMapping("/{userId}")
    public void delete(@PathVariable String userId) {
        identityMemoryService.deleteIdentity(userId);
    }
}
