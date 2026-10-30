package com.kakuiwong.gaoyangspringai.controller.digitalHuman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakuiwong.gaoyangspringai.util.LiveTalkingUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: gaoyang
 * @Description: 数字人控制器，调用 LiveTalking 服务实现文本驱动数字人
 */
@RestController
@RequestMapping("/digital-human")
public class DigitalHumanController {

    @Autowired
    private LiveTalkingUtil liveTalkingUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 建立 WebRTC 连接，获取 sessionId
     * 前端需要先调用此接口建立连接，获取 sessionId 后才能调用 speak 接口
     *
     * @param offerRequest 包含 SDP offer 的请求体 {"sdp": "...", "type": "offer"}
     * @return 包含 SDP answer 和 sessionId 的响应
     */
    @PostMapping("/offer")
    public Map<String, Object> offer(@RequestBody Map<String, String> offerRequest) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sdp = offerRequest.get("sdp");
            String type = offerRequest.get("type");

            if (sdp == null || sdp.isEmpty()) {
                result.put("success", false);
                result.put("message", "SDP 不能为空");
                return result;
            }

            if (type == null || type.isEmpty()) {
                type = "offer";
            }

            String response = liveTalkingUtil.createOffer(sdp, type);
            
            // 将 LiveTalking 返回的 response 解析为 JSON，提取 sessionId
            JsonNode node = objectMapper.readTree(response);
            Map<String, Object> parsedData = new HashMap<>();
            parsedData.put("sdp", node.has("sdp") ? node.get("sdp").asText() : "");
            parsedData.put("type", node.has("type") ? node.get("type").asText() : "answer");
            parsedData.put("sessionId", node.has("sessionid") ? node.get("sessionid").asText() : "");
            
            result.put("data", parsedData);
        } catch (Exception e) {
            result.put("message", "建立 WebRTC 连接失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 文本驱动数字人说话
     * 注意：需要先调用 /offer 接口建立 WebRTC 连接获取 sessionId
     *
     * @param text      要转换的文本内容（必填）
     * @param sessionId 会话ID（必填，通过 /offer 接口获取）
     * @param type      类型：echo（直接复读）或 chat（LLM对话），默认 echo
     * @return LiveTalking 服务响应结果
     */
    @PostMapping("/speak")
    public Map<String, Object> speak(
            @RequestParam("text") String text,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "type", required = false, defaultValue = "echo") String type) {

        Map<String, Object> result = new HashMap<>();
        try {
            // 验证 sessionId 是否为空
            if (sessionId == null || sessionId.isEmpty()) {
                result.put("success", false);
                result.put("message", "sessionId 不能为空，请先调用 /offer 接口建立连接");
                return result;
            }

            String response = liveTalkingUtil.humanSpeak(text, sessionId, type);
            result.put("success", true);
            result.put("data", response);
            result.put("message", "文本已发送到数字人");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "调用数字人服务失败: " + e.getMessage());
            result.put("hint", "请检查：1) LiveTalking 服务是否已启动 2) sessionId 是否有效");
        }
        return result;
    }

    /**
     * 检查数字人是否正在说话
     *
     * @param sessionId 会话ID
     * @return 说话状态
     */
    @GetMapping("/is-speaking")
    public Map<String, Object> isSpeaking(@RequestParam("sessionId") String sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean speaking = liveTalkingUtil.isSpeaking(sessionId);
            result.put("success", true);
            result.put("sessionId", sessionId);
            result.put("isSpeaking", speaking);
            result.put("message", speaking ? "数字人正在说话" : "数字人空闲");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询说话状态失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取会话信息（验证 sessionid 是否有效）
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    @GetMapping("/session/{sessionId}")
    public Map<String, Object> getSessionInfo(@PathVariable("sessionId") String sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String response = liveTalkingUtil.getSessionInfo(sessionId);
            result.put("success", true);
            result.put("sessionId", sessionId);
            result.put("data", response);
            result.put("message", "会话有效");
        } catch (Exception e) {
            result.put("success", false);
            result.put("sessionId", sessionId);
            result.put("message", "会话无效或已断开: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取使用说明
     *
     * @return 使用说明信息
     */
    @GetMapping("/help")
    public Map<String, Object> help() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "LiveTalking 数字人服务使用说明");
        result.put("usage", new String[]{
            "1. 确保 LiveTalking 服务已启动（默认 http://localhost:8010）",
            "2. 调用 POST /digital-human/offer 接口建立 WebRTC 连接，获取 sessionId",
            "3. 调用 POST /digital-human/speak 接口，传入 text 和 sessionId 驱动数字人"
        });
        result.put("apis", new String[]{
            "POST /digital-human/offer - 建立 WebRTC 连接，获取 sessionId",
            "POST /digital-human/speak?text=xxx&sessionId=xxx&type=echo - 文本驱动数字人",
            "GET /digital-human/is-speaking?sessionId=xxx - 检查说话状态",
            "GET /digital-human/session/{sessionId} - 验证会话是否有效"
        });
        return result;
    }
}
