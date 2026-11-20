package com.kakuiwong.gaoyangspringai.controller.demo.asr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * @author: gaoyang
 * @Description: 实时语音转文字 WebSocket 端点
 */
@Configuration
@EnableWebSocket
public class AsrWebSocketConfig implements WebSocketConfigurer {

    private final AsrRealtimeWebSocketHandler asrRealtimeWebSocketHandler;

    public AsrWebSocketConfig(AsrRealtimeWebSocketHandler asrRealtimeWebSocketHandler) {
        this.asrRealtimeWebSocketHandler = asrRealtimeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(asrRealtimeWebSocketHandler, "/ws/asr")
                .setAllowedOriginPatterns("*");
    }

    /**
     * 默认 8KB 的二进制帧上限低于 1 秒 opus 分片，需调大避免连接被断开
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
    }
}
