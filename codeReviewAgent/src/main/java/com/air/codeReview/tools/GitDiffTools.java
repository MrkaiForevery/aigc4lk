package com.air.codeReview.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;

@Component
public class GitDiffTools {

    @Tool(description = "获取Git仓库中未提交的差异代码")
    public String getUncommittedDiff(@ToolParam(description = "仓库路径") String repoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff");
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder diff = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    diff.append(line).append("\n");
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    return "Error: git diff failed with exit code " + exitCode;
                }
                return diff.toString();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "获取两个commit之间的差异")
    public String getDiffBetweenCommits(
            @ToolParam(description = "仓库路径") String repoPath,
            @ToolParam(description = "起始commit") String fromCommit,
            @ToolParam(description = "目标commit") String toCommit) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", fromCommit, toCommit);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder diff = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    diff.append(line).append("\n");
                }
                return diff.toString();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "读取指定文件内容")
    public String readFile(@ToolParam(description = "文件路径") String filePath) {
        try {
            return Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}