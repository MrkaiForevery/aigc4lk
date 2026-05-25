package com.air.commander.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelSelection {
    private String modelId;
    private String modelName;
    private String provider;
    private String selectionStrategy;
    private List<String> capabilities;
}

