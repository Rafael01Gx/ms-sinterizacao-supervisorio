package br.com.sinterpiloto.supervisorio.application.dto;

import java.time.Instant;

public record LogEvent(
        Instant timestamp,
        String level,     // INFO | WARN | ERROR
        String message,
        String state
) {}