package br.com.sinterpiloto.supervisorio.application.dto;

import java.time.Instant;

public record SnapshotMessage(
        Instant timestamp,
        AnalogicData analogic,
        DigitalStatus digital,
        SessionInfo session,
        boolean plcConnected
) {}
