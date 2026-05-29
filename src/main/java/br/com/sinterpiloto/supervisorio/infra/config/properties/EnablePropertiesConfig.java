package br.com.sinterpiloto.supervisorio.infra.config.properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PlcProperties.class})
public class EnablePropertiesConfig {
}
