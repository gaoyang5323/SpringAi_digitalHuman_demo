package com.kakuiwong.gaoyangspringai.controller.digitalHuman;

import com.kakuiwong.gaoyangspringai.util.LiveTalkingUtil;
import com.kakuiwong.gaoyangspringai.util.SpeachesUtil;
import com.kakuiwong.gaoyangspringai.util.ThreadPoolUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * @author: gaoyang
 * @Description: 语音转文字（ASR），调用 Speaches 服务，流式返回
 */
@Slf4j
@RestController
public class TtsController {

    private static final long SSE_TIMEOUT_MILLISECOND = 300_000L;
    /** 上传文件最大 50MB，与 servlet.multipart 配置保持一致 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    @Autowired
    SpeachesUtil speachesUtil;
    @Autowired
    private LiveTalkingUtil liveTalkingUtil;

    /**
     * 接收前端录音文件，调用 Speaches 服务转为文字，通过 SSE 流式返回
     */
    @RequestMapping(value = "/upload/asr", produces = {"text/event-stream;charset=UTF-8"})
    public void asr(@RequestPart("file") MultipartFile file,
                    @RequestParam("sessionId") String sessionId) throws IOException {

        // 校验文件大小，防止大文件堆积导致内存压力
        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("上传文件过大，拒绝处理: size={} bytes, max={}", file.getSize(), MAX_FILE_SIZE);
            return;
        }

        // 在请求线程中提前读取文件字节，避免异步执行时 Tomcat 临时文件已被清理
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();

        ThreadPoolUtil.getPool().execute(() -> {
            String text = "语音识别错误";
            try {
                text = speachesUtil.transcribe(fileBytes, originalFilename, contentType);
            } catch (IOException e) {
                log.error("",e);
            }
            String response = liveTalkingUtil.humanSpeak(text, sessionId, "echo");
            log.info(response);
        });
    }
}
