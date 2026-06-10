package com.air.commander.credential;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 凭证服务，用于中断统一反馈 todo 先模拟，后续单独做成一个服务
 */
@Component
public class CredentialService {

    public Map<String, String> approve(String userId, List<String> scopes) {

        return Map.of("token", "mock-token");
    }
}