package com.kakuiwong.gaoyangspringai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: gaoyang
 * @Description: LiveTalking 数字人服务调用工具
 */
@Service
public class LiveTalkingUtil {

    @Value("${livetalking.base-url:http://localhost:8010}")
    private String livetalkingBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 文本驱动数字人说话
     *
     * @param text      要转换的文本内容
     * @param sessionId 会话ID（必填，通过 WebRTC 连接建立后获取）
     * @param type      类型：echo（直接复读）或 chat（LLM对话），默认 echo
     * @return LiveTalking 服务响应结果
     */
    public String humanSpeak(String text, String sessionId, String type) {
        String url = livetalkingBaseUrl + "/human";

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        if (sessionId != null && !sessionId.isEmpty()) {
            requestBody.put("sessionid", sessionId);
        }
        if (type != null && !type.isEmpty()) {
            requestBody.put("type", type);
        } else {
            requestBody.put("type", "echo");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new RuntimeException("LiveTalking 服务返回异常, status=" + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("调用 LiveTalking 服务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查数字人是否正在说话
     *
     * @param sessionId 会话ID
     * @return true 表示正在说话，false 表示空闲
     */
    public boolean isSpeaking(String sessionId) {
        String url = livetalkingBaseUrl + "/is_speaking?sessionid=" + sessionId;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                return jsonNode.has("data") && jsonNode.get("data").asBoolean();
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("查询说话状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取会话信息（用于验证 sessionid 是否有效）
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    public String getSessionInfo(String sessionId) {
        String url = livetalkingBaseUrl + "/human?sessionid=" + sessionId;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("LiveTalking 服务返回异常, status=" + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("获取会话信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 建立 WebRTC 连接（转发 offer 到 LiveTalking）
     *
     * @param sdp  SDP offer 内容
     * @param type 消息类型（通常为 "offer"）
     * @return 包含 SDP answer 和 sessionId 的响应
     */
    public String createOffer(String sdp, String type) {
        String url = livetalkingBaseUrl + "/offer";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sdp", sdp);
        requestBody.put("type", type);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new RuntimeException("LiveTalking 服务返回异常, status=" + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("建立 WebRTC 连接失败: " + e.getMessage(), e);
        }
    }
}
