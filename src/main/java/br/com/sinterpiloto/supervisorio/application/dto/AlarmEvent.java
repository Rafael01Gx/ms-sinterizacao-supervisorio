package br.com.sinterpiloto.supervisorio.application.dto;

import java.time.Instant;

public record AlarmEvent(
        Instant timestamp,
        String alarmCode,
        String description,
        String severity   // LOW | MEDIUM | HIGH | CRITICAL
) {}
