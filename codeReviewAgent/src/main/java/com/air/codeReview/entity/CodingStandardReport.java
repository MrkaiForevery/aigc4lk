package com.air.codeReview.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public  class CodingStandardReport {
        public String standard;
        public double commentRatio;
        public List<String> violations;
        public int violationCount;
        public boolean passed;
    }