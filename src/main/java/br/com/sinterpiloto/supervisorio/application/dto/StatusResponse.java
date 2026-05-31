package br.com.sinterpiloto.supervisorio.application.dto;

import java.time.Instant;

public record StatusResponse(
        boolean plcConnected,
        String currentState,
        String sessionId,
        Instant sessionStartedAt,
        int subscribersCount
) {}
