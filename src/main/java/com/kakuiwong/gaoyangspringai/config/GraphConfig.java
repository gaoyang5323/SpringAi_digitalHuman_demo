package com.kakuiwong.gaoyangspringai.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    @Value("${spring.datasource.host}")
    String host;
    @Value("${spring.datasource.port}")
    Integer port;
    @Value("${spring.datasource.user}")
    String user;
    @Value("${spring.datasource.password}")
    String password;

    @Bean
    public PostgresSaver postgresqlSaver() {
        return PostgresSaver.builder()
                .database("postgres")
                .port(port)
                .host(host)
                .user(user)
                .password(password)
                .dropTablesFirst(false)
                .build();
    }
}
