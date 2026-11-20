package com.kakuiwong.gaoyangspringai.controller.demo.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.kakuiwong.gaoyangspringai.util.SpeachesUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author: gaoyang
 * @Description: 实时语音转文字：前端把「停顿 3 秒断句、单段最长 20 秒」切好的一段语音
 * 以完整 webm 推送，后端逐段转写并追加；客户端定时 ping，服务端对无心跳连接主动断连
 */
@Slf4j
@Component
public class AsrRealtimeWebSocketHandler extends BinaryWebSocketHandler {

    private static final String SESSION_STATE = "asrRealtimeState";
    /** 单段音频保护上限，20 秒 opus 远低于该值 */
    private static final long MAX_SEGMENT_BYTES = 4L * 1024 * 1024;
    /** 同一连接两次语音段提交的最小间隔，避免 2 秒内重复上传 */
    private static final long SEGMENT_MIN_INTERVAL_MILLIS = 2000L;
    /** 超过该时长没有任何消息（客户端每 10 秒 ping）判定连接已死 */
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 45_000L;
    private static final long WATCHDOG_INTERVAL_SECONDS = 15L;

    private final SpeachesUtil speachesUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "asr-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public AsrRealtimeWebSocketHandler(SpeachesUtil speachesUtil) {
        this.speachesUtil = speachesUtil;
    }

    @PreDestroy
    public void shutdownScheduler() {
        heartbeatScheduler.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SessionState state = new SessionState();
        state.lastActiveAtMillis = System.currentTimeMillis();
        session.getAttributes().put(SESSION_STATE, state);
        state.watchdog = heartbeatScheduler.scheduleAtFixedRate(
                () -> checkHeartbeat(session, state),
                WATCHDOG_INTERVAL_SECONDS, WATCHDOG_INTERVAL_SECONDS, TimeUnit.SECONDS);
        sendJson(session, Map.of("type", "ready"));
    }

    /**
     * 一条二进制消息即一段完整语音
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionState state = stateOf(session);
        if (state == null || message.getPayloadLength() == 0) {
            return;
        }
        if (message.getPayloadLength() > MAX_SEGMENT_BYTES) {
            sendError(session, "单段音频过大");
            return;
        }

        ByteBuffer payload = message.getPayload();
        byte[] audio = new byte[payload.remaining()];
        payload.get(audio);

        int index;
        synchronized (state) {
            if (state.closed) {
                return;
            }
            state.lastActiveAtMillis = System.currentTimeMillis();
            long interval = state.lastActiveAtMillis - state.lastSegmentAtMillis;
            if (interval < SEGMENT_MIN_INTERVAL_MILLIS) {
                log.warn("语音段提交过于频繁，已忽略: sessionId={}, interval={}ms", session.getId(), interval);
                return;
            }
            state.lastSegmentAtMillis = state.lastActiveAtMillis;
            index = ++state.segmentCount;
        }
        try {
            state.executor.execute(() -> transcribeSegment(session, state, audio, index));
        } catch (Exception e) {
            log.warn("提交分段转写任务失败: sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionState state = stateOf(session);
        if (state == null) {
            return;
        }
        String payload = message.getPayload().trim();
        synchronized (state) {
            state.lastActiveAtMillis = System.currentTimeMillis();
        }
        if ("ping".equalsIgnoreCase(payload)) {
            sendJson(session, Map.of("type", "pong"));
        } else if ("stop".equalsIgnoreCase(payload)) {
            finish(session, state);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = stateOf(session);
        if (state != null) {
            synchronized (state) {
                state.closed = true;
            }
            cancelWatchdog(state);
            state.executor.shutdownNow();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("实时转写连接异常: sessionId={}, error={}", session.getId(), exception.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 客户端每 10 秒 ping 一次，长时间无任何消息则判定断线并关闭
     */
    private void checkHeartbeat(WebSocketSession session, SessionState state) {
        synchronized (state) {
            if (state.closed) {
                cancelWatchdog(state);
                return;
            }
            if (state.finishing) {
                return;
            }
            if (System.currentTimeMillis() - state.lastActiveAtMillis <= HEARTBEAT_TIMEOUT_MILLIS) {
                return;
            }
            state.closed = true;
        }
        log.warn("心跳超时，关闭连接: sessionId={}", session.getId());
        sendError(session, "心跳超时，连接已断开");
        closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
        state.executor.shutdownNow();
    }

    /**
     * 单线程执行器保证多段语音按到达顺序转写、按顺序拼进会话文本
     */
    private void transcribeSegment(WebSocketSession session, SessionState state, byte[] audio, int index) {
        String text;
        try {
            text = normalize(speachesUtil.transcribeWebmChunk(audio));
        } catch (Exception e) {
            log.error("第 {} 段转写失败: sessionId={}", index, session.getId(), e);
            sendError(session, "第 " + index + " 段识别失败");
            return;
        }

        synchronized (state) {
            state.transcript.append(text);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "segment");
        payload.put("index", index);
        payload.put("text", text);
        sendJson(session, payload);
    }

    /**
     * 排队中的分段转写完成后回推完整文本并关闭连接
     */
    private void finish(WebSocketSession session, SessionState state) {
        synchronized (state) {
            if (state.finishing) {
                return;
            }
            state.finishing = true;
        }
        try {
            state.executor.execute(() -> {
                String fullText;
                synchronized (state) {
                    fullText = state.transcript.toString();
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "final");
                payload.put("text", fullText);
                sendJson(session, payload);
                closeQuietly(session, CloseStatus.NORMAL);
                synchronized (state) {
                    state.closed = true;
                }
                cancelWatchdog(state);
                state.executor.shutdown();
            });
        } catch (Exception e) {
            log.warn("提交收尾任务失败: sessionId={}, error={}", session.getId(), e.getMessage());
            cancelWatchdog(state);
            state.executor.shutdown();
        }
    }

    private void cancelWatchdog(SessionState state) {
        ScheduledFuture<?> future;
        synchronized (state) {
            future = state.watchdog;
            state.watchdog = null;
        }
        if (future != null) {
            future.cancel(false);
        }
    }

    private SessionState stateOf(WebSocketSession session) {
        Object state = session.getAttributes().get(SESSION_STATE);
        return state instanceof SessionState s ? s : null;
    }

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "error");
        payload.put("message", message);
        sendJson(session, payload);
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.debug("推送转写结果失败: sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.debug("关闭实时转写连接失败: sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    /**
     * 同一段音频在不同长度下可能输出繁体或简体，统一转简体
     */
    private String normalize(String text) {
        return ZhConverterUtil.toSimple(text == null ? "" : text.trim());
    }

    private static final class SessionState {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final StringBuilder transcript = new StringBuilder();
        private int segmentCount;
        private long lastSegmentAtMillis;
        private long lastActiveAtMillis;
        private ScheduledFuture<?> watchdog;
        private boolean finishing;
        private boolean closed;
    }
}
