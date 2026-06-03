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
public  class CodeQualityReport {
        public String language;
        public int linesOfCode;
        public int cyclomaticComplexity;
        public double readabilityScore;
        public List<String> duplicationIssues;
        public double overallScore;
        // getters/setters 省略，使用 Lombok 或手动生成
    }