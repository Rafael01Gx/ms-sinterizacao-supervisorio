package br.com.sinterpiloto.supervisorio.infra.config.plc;


import org.apache.plc4x.java.api.PlcDriverManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class PlcConnectionConfig {

    @Bean
    public PlcDriverManager plcDriverManager() {
        return PlcDriverManager.getDefault();
    }

}
