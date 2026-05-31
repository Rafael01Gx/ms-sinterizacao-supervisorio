package br.com.sinterpiloto.supervisorio.infra.config.spring.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "plcExecutor")
    public Executor plcExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);       // Quantidade de threads sempre ativas
        executor.setMaxPoolSize(10);       // Máximo de threads se o sistema gargalar
        executor.setQueueCapacity(500);    // Quantidade de requisições em fila
        executor.setThreadNamePrefix("PLC-Async-");
        executor.initialize();
        return executor;
    }
}
