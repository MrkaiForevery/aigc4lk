package com.air.commander.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Data
@Getter
@Setter
public class CommanderModelDefinition {
    private String modelId;
    private String type;          // DASHSCOPE / OPENAI_COMPATIBLE
    private String provider;
    private String modelName;
    private List<String> capabilities = new ArrayList<>();
    private Integer weight;
    private boolean enabled;
}