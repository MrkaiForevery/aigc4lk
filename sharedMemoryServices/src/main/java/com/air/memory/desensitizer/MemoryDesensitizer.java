package com.air.memory.desensitizer;

import com.air.memory.config.DesensitizeEnableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具
 * 支持：手机号、邮箱、身份证、银行卡、IP地址
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDesensitizer {

    /**脱敏开关配置注入**/
    private final DesensitizeEnableConfig desensitizeEnableConfig;

    //todo 后期这些配置放到Nacos上面读取
    // 手机号（国内）
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    // 邮箱
    private static final Pattern EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}");
    // 身份证（18位）
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    // 银行卡（16-19位数字）
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");
    // IP地址
    private static final Pattern IP_ADDR = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    /**
     * 全面脱敏：应用所有规则
     */
    public String desensitize(String content) {
        if (content == null || content.isEmpty() || !desensitizeEnableConfig.isEnabled()) return content;

        String result = content;
        if (desensitizeEnableConfig.isPhone()) {
            result = desensitizePhone(result);
        }

        if (desensitizeEnableConfig.isEmail()) {
            result = desensitizeEmail(result);
        }

        if (desensitizeEnableConfig.isIdCard()) {
            result = desensitizeIdCard(result);
        }

        if (desensitizeEnableConfig.isBankCard()) {
            result = desensitizeBankCard(result);
        }

        if (desensitizeEnableConfig.isIp()) {
            result = desensitizeIp(result);
        }

        return result;
    }

    /**
     * 手机号脱敏：保留前3后4，中间4位替换为 ****
     */
    public String desensitizePhone(String content) {
        if (content == null) return null;
        return PHONE.matcher(content).replaceAll(match -> {
            String phone = match.group();
            if (phone.length() == 11) {
                return phone.substring(0, 3) + "****" + phone.substring(7);
            }
            return "****";
        });
    }

    /**
     * 邮箱脱敏：用户名部分替换为 ***
     */
    public String desensitizeEmail(String content) {
        if (content == null) return null;
        return EMAIL.matcher(content).replaceAll(match -> {
            String email = match.group();
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return "***" + email.substring(atIndex);
            }
            return "***";
        });
    }

    /**
     * 身份证脱敏：保留前6后4，中间8位替换为 *
     */
    public String desensitizeIdCard(String content) {
        if (content == null) return null;
        return ID_CARD.matcher(content).replaceAll(match -> {
            String idCard = match.group();
            if (idCard.length() == 18) {
                return idCard.substring(0, 6) + "********" + idCard.substring(14);
            }
            return "****************";
        });
    }

    /**
     * 银行卡脱敏：保留前4后4，中间替换为 *
     */
    public String desensitizeBankCard(String content) {
        if (content == null) return null;
        return BANK_CARD.matcher(content).replaceAll(match -> {
            String card = match.group();
            if (card.length() >= 8) {
                int maskLen = card.length() - 8;
                return card.substring(0, 4) + "*".repeat(maskLen) + card.substring(card.length() - 4);
            }
            return "****";
        });
    }

    /**
     * IP地址脱敏：后两段替换为 *
     */
    public String desensitizeIp(String content) {
        if (content == null) return null;
        return IP_ADDR.matcher(content).replaceAll(match -> {
            String ip = match.group();
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
            return "*.*.*.*";
        });
    }
}