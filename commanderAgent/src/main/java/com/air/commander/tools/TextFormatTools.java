package com.air.commander.tools;

public class TextFormatTools {

    // 清洗 markdown 包裹: ```json ... ```
    public static String removeMDHeadTailAnnotation(String input) {
        return input.replaceAll("^```(?:json)?\\s*", "")   // 去掉开头的 ```json
                .replaceAll("\\s*```$", "")            // 去掉结尾的 ```
                .trim();
    }
}
