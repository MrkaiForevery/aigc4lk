package com.air.memory.service;

import com.air.api.dto.IdentityMemoryDTO;
import com.air.memory.entity.IdentityMemory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityMemoryService {

    private final StructuredMemoryRepository structuredMemoryRepository;


    public IdentityMemory getIdentity(String userId) {
       return structuredMemoryRepository.getIdentity(userId);
    }

    public void saveIdentity(IdentityMemoryDTO entity) {
        structuredMemoryRepository.saveIdentityAsync(entity);
    }

    public void deleteIdentity(String userId) {
        structuredMemoryRepository.deleteIdentityAsync(userId);
    }
}
