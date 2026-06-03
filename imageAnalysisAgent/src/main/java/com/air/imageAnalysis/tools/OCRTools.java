package com.air.imageAnalysis.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.Base64;

@Component
public class OCRTools {

    private final ChatModel chatModel;

    public OCRTools(@Qualifier("ocrChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "识别图像中的文字内容，返回提取的文本")
    public String recognizeText(@ToolParam(description = "图像的 Base64 编码") String imageBase64) {
        try {
            // 解码 Base64
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            Resource imageResource = new ByteArrayResource(imageBytes);

            // 使用 builder 模式创建 Media，如果需要 id，可以设置，如果不需要，直接留空
            Media imageMedia = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_PNG)
                    .data(imageResource)
                    .build();

            // 构建多模态消息
            UserMessage userMessage = UserMessage.builder()
                    .text("请识别这张图片中的所有文字内容，只返回文字本身，不要额外解释。")
                    .media(imageMedia) // 可以直接传入 Media 对象
                    .build();

            ChatResponse response = chatModel.call(new Prompt(userMessage));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            return "OCR识别失败: " + e.getMessage();
        }
    }
}