package br.com.sinterpiloto.supervisorio.application.dto;

import br.com.sinterpiloto.supervisorio.domain.enums.BurningState;

import java.time.Instant;

public record SessionInfo(
        String sessionId,
        BurningState state,
        Instant startedAt,
        boolean active
) {}