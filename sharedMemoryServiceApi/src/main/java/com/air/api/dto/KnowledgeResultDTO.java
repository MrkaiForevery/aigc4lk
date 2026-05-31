package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public  class KnowledgeResultDTO {
    private  String id;
    private  String content;
    private double similarity;
    private  Map<String, Object> metadata;
}