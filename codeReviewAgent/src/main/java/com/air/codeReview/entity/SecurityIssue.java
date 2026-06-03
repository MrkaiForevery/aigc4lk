package com.air.codeReview.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public  class SecurityIssue {
        public String type;
        public String severity;
        public String description;
        public int lineNumber;

    }