package com.air.codeReview.tools;

import com.air.codeReview.entity.CodeQualityReport;
import com.air.codeReview.entity.CodingStandardReport;
import com.air.codeReview.entity.SecurityIssue;
import com.air.codeReview.entity.SecurityReport;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class CodeAnalysisAbilityTools {

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\+.*?\\\"|\\\"\\s*\\+\\s*\\w+\\s*\\+\\s*\\\")",
        Pattern.DOTALL
    );
    private static final Pattern HARDCODED_PASSWORD = Pattern.compile(
        "(?i)(password|pwd|secret|key)\\s*=\\s*\"[^\"]+\"",
        Pattern.DOTALL
    );

    @Tool(description = "分析代码质量，返回复杂度、重复率、可读性评分")
    public CodeQualityReport analyzeCode(
            @ToolParam(description = "待审查的源代码") String code,
            @ToolParam(description = "编程语言 (java, python, javascript 等)") String language) {
        
        CodeQualityReport report = new CodeQualityReport();
        report.setLanguage(language);
        report.setLinesOfCode(code.split("\n").length);
        
        // 简单复杂度评估：根据条件分支、循环等估算
        int complexity = countComplexity(code);
        report.setCyclomaticComplexity(complexity);
        
        // 可读性评分（基于注释率、命名规范等简易判断）
        double readability = calculateReadability(code);
        report.setReadabilityScore(readability);
        
        // 检查是否存在重复代码模式（简化版）
        List<String> duplicates = findDuplicates(code);
        report.setDuplicationIssues(duplicates);
        
        // 总体质量评分（0-100）
        report.setOverallScore(computeOverallScore(complexity, readability, duplicates.size()));
        
        return report;
    }

    @Tool(description = "检测代码中的安全漏洞")
    public SecurityReport checkSecurity(
            @ToolParam(description = "待审查的源代码") String code,
            @ToolParam(description = "编程语言") String language) {
        
        SecurityReport report = new SecurityReport();
        List<SecurityIssue> issues = new ArrayList<>();
        
        // SQL 注入检测
        if (SQL_INJECTION_PATTERN.matcher(code).find()) {
            issues.add(new SecurityIssue("SQL_INJECTION", "HIGH", 
                "检测到可能存在SQL注入风险的字符串拼接", getLineNumber(code, SQL_INJECTION_PATTERN)));
        }
        
        // 硬编码密码检测
        if (HARDCODED_PASSWORD.matcher(code).find()) {
            issues.add(new SecurityIssue("HARDCODED_CREDENTIALS", "CRITICAL", 
                "检测到硬编码密码/密钥", getLineNumber(code, HARDCODED_PASSWORD)));
        }
        
        // XSS 检测（简单模式）
        if (code.contains("innerHTML") || code.contains("document.write")) {
            issues.add(new SecurityIssue("XSS_RISK", "MEDIUM", 
                "检测到潜在的XSS风险（innerHTML/document.write）", -1));
        }
        
        report.setIssues(issues);
        report.setTotalIssues(issues.size());
        report.setCriticalCount((int) issues.stream().filter(i -> "CRITICAL".equals(i.severity)).count());
        report.setHighCount((int) issues.stream().filter(i -> "HIGH".equals(i.severity)).count());
        
        return report;
    }

    @Tool(description = "检查编码规范符合度")
    public CodingStandardReport checkCodingStandard(
            @ToolParam(description = "待审查的源代码") String code,
            @ToolParam(description = "规范标准，如 alibaba、google") String standard) {
        
        CodingStandardReport report = new CodingStandardReport();
        report.setStandard(standard);
        
        List<String> violations = new ArrayList<>();
        
        // 命名规范检查（驼峰）
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 检测类名首字母大写
            if (line.matches(".*class\\s+[a-z].*")) {
                violations.add("第" + (i+1) + "行: 类名应该以大写字母开头");
            }
            // 检测方法名首字母小写
            if (line.matches(".*\\s+[A-Z][a-zA-Z0-9]*\\(.*\\).*\\{")) {
                violations.add("第" + (i+1) + "行: 方法名应该以小写字母开头");
            }
        }
        
        // 注释率检查
        int totalLines = lines.length;
        long commentLines = Arrays.stream(lines).filter(l -> l.trim().startsWith("//") || l.trim().startsWith("/*")).count();
        double commentRatio = totalLines > 0 ? (double) commentLines / totalLines : 0;
        
        report.setCommentRatio(commentRatio);
        report.setViolations(violations);
        report.setViolationCount(violations.size());
        report.setPassed(violations.isEmpty() && commentRatio >= 0.1);
        
        return report;
    }

    // 内部辅助方法
    private int countComplexity(String code) {
        int complexity = 1;
        complexity += countOccurrences(code, "if") * 1;
        complexity += countOccurrences(code, "else") * 1;
        complexity += countOccurrences(code, "for") * 1;
        complexity += countOccurrences(code, "while") * 1;
        complexity += countOccurrences(code, "case") * 1;
        complexity += countOccurrences(code, "&&") * 1;
        complexity += countOccurrences(code, "\\|\\|") * 1;
        return complexity;
    }
    
    private int countOccurrences(String text, String word) {
        Pattern p = Pattern.compile("\\b" + word + "\\b");
        int count = 0;
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) count++;
        return count;
    }
    
    private double calculateReadability(String code) {
        int lines = code.split("\n").length;
        long commentLines = Arrays.stream(code.split("\n")).filter(l -> l.trim().startsWith("//") || l.trim().startsWith("/*")).count();
        double commentRatio = lines > 0 ? (double) commentLines / lines : 0;
        // 基本分50 + 注释分30 + 行长度分20
        double score = 50 + Math.min(30, commentRatio * 100 * 0.3);
        // 检查平均行长
        double avgLineLength = Arrays.stream(code.split("\n")).mapToInt(String::length).average().orElse(0);
        if (avgLineLength < 80) score += 20;
        else if (avgLineLength < 120) score += 10;
        return Math.min(100, score);
    }
    
    private List<String> findDuplicates(String code) {
        // 简化实现：寻找重复的5行代码块
        List<String> duplicates = new ArrayList<>();
        String[] lines = code.split("\n");
        Map<String, Integer> lineFreq = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 10) {
                lineFreq.put(trimmed, lineFreq.getOrDefault(trimmed, 0) + 1);
            }
        }
        for (Map.Entry<String, Integer> entry : lineFreq.entrySet()) {
            if (entry.getValue() > 2) {
                duplicates.add("重复代码行: " + entry.getKey().substring(0, Math.min(50, entry.getKey().length())));
            }
        }
        return duplicates;
    }
    
    private double computeOverallScore(int complexity, double readability, int duplicateCount) {
        double score = 100;
        if (complexity > 30) score -= 30;
        else if (complexity > 20) score -= 15;
        else if (complexity > 10) score -= 5;
        score = score * (readability / 100);
        score -= duplicateCount * 5;
        return Math.max(0, Math.min(100, score));
    }
    
    private int getLineNumber(String code, Pattern pattern) {
        java.util.regex.Matcher m = pattern.matcher(code);
        if (m.find()) {
            int pos = m.start();
            String[] lines = code.substring(0, pos).split("\n");
            return lines.length;
        }
        return -1;
    }
}