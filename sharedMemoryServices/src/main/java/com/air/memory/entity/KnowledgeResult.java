package com.air.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public  class KnowledgeResult {
    private  String id;
    private  String content;
    private  Map<String, Object> metadata;
}