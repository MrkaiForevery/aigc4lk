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
public  class SecurityReport {
        public List<SecurityIssue> issues;
        public int totalIssues;
        public int criticalCount;
        public int highCount;
    }