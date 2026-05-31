package br.com.sinterpiloto.supervisorio.application.dto;

import java.util.Map;

public record CommandMessage(
        String command,          // START | STOP | EMERGENCY | RESET | FORCE_MAIN
        Map<String, Object> params
) {}