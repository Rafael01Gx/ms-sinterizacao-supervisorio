package br.com.sinterpiloto.supervisorio.domain.models;

import br.com.sinterpiloto.supervisorio.domain.enums.BurningState;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Representa uma sessão de queima completa — do início ao fim.
 * Agrega o histórico de eventos e estado atual.
 */
public class BurningSession {

    // getters
    @Getter
    private final String id;
    @Getter
    private final Instant startedAt;
    @Getter
    private BurningState currentState;
    @Getter
    private Instant finishedAt;
    private final List<String> eventLog;

    private BurningSession(String id, Instant startedAt) {
        this.id = id;
        this.startedAt = startedAt;
        this.currentState = BurningState.IDLE;
        this.eventLog = new ArrayList<>();
    }

    public static BurningSession start() {
        return new BurningSession(UUID.randomUUID().toString(), Instant.now());
    }

    public void transition(BurningState newState, String message) {
        this.currentState = newState;
        this.eventLog.add("[%s] [%s] %s".formatted(Instant.now(), newState, message));
    }

    public void finish() {
        this.finishedAt = Instant.now();
    }

    public boolean isActive() {
        return finishedAt == null;
    }

    public List<String> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }
}