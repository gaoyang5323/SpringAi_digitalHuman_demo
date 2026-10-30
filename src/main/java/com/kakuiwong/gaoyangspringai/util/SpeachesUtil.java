package com.kakuiwong.gaoyangspringai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class SpeachesUtil {

    @Value("${speaches.base-url:http://localhost:8000}")
    private String speachesBaseUrl;

    @Value("${speaches.model:Systran/faster-whisper-large-v3}")
    private String speachesModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * 异步安全版本：直接接收字节数组，避免 MultipartFile 临时文件被清理后无法读取
     */
    public String transcribe(byte[] fileBytes, String rawFilename, String rawContentType) throws IOException {
        String url = speachesBaseUrl + "/v1/audio/transcriptions";

        // 兜底文件名，确保带有音频扩展名
        final String originalFilename = rawFilename != null && rawFilename.matches(".*\\.(wav|mp3|ogg|flac|m4a|webm)$")
                ? rawFilename : "audio.wav";

        // 为 file 字段设置正确的音频 Content-Type，避免 Speaches 拒绝请求
        String contentType = rawContentType != null && rawContentType.startsWith("audio/")
                ? rawContentType : "audio/wav";
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(
                new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return originalFilename;
                    }
                }, fileHeaders);

        return doTranscribe(url, filePart);
    }

    /**
     * 同步版本：直接接收 MultipartFile（仅在请求线程内可用）
     */
    public String transcribe(MultipartFile file) throws IOException {
        return transcribe(file.getBytes(), file.getOriginalFilename(), file.getContentType());
    }

    private String doTranscribe(String url, HttpEntity<ByteArrayResource> filePart) throws IOException {

        // 构建 multipart 请求体
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        body.add("model", speachesModel);
        body.add("response_format", "json");
        body.add("language", "zh");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.has("text") ? jsonNode.get("text").asText() : response.getBody();
        }

        throw new IOException("Speaches 服务返回异常, status=" + response.getStatusCode());
    }
}
