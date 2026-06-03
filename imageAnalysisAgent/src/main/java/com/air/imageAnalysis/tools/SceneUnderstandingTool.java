package com.air.imageAnalysis.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.Base64;

@Component
public class SceneUnderstandingTool {

    private final ChatModel chatModel;

    public SceneUnderstandingTool(@Qualifier("sceneChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "分析图像的整体场景、情感、光照、质量等")
    public String analyzeScene(@ToolParam(description = "图像的 Base64 编码") String imageBase64) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            Resource imageResource = new ByteArrayResource(imageBytes);

            // 使用 builder 模式创建 Media，如果需要 id，可以设置，如果不需要，直接留空
            Media imageMedia = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_PNG)
                    .data(imageResource)
                    .build();

            // 构建多模态消息
            UserMessage userMessage = UserMessage.builder()
                    .text("请分析这张图片的场景，包括：1.整体场景描述；2.情感氛围；3.光照条件；4.图像质量。请用中文回答。")
                    .media(imageMedia) // 可以直接传入 Media 对象
                    .build();

            ChatResponse response = chatModel.call(new Prompt(userMessage));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            return "场景分析失败: " + e.getMessage();
        }
    }
}