package br.com.sinterpiloto.supervisorio.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plc")
public record PlcProperties(
        String address,
        Polling polling
) {

    public record Polling(
            int processIntervalMs,
            int alarmIntervalMs
    ) {}

}
