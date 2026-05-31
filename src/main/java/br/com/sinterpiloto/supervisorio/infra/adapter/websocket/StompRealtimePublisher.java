package br.com.sinterpiloto.supervisorio.infra.adapter.websocket;

import br.com.sinterpiloto.supervisorio.application.dto.AlarmEvent;
import br.com.sinterpiloto.supervisorio.application.dto.LogEvent;
import br.com.sinterpiloto.supervisorio.application.dto.SessionInfo;
import br.com.sinterpiloto.supervisorio.application.dto.SnapshotMessage;
import br.com.sinterpiloto.supervisorio.application.usecase.SnapshotMapper;
import br.com.sinterpiloto.supervisorio.domain.models.BurningSession;
import br.com.sinterpiloto.supervisorio.domain.models.PlcSnapshot;
import br.com.sinterpiloto.supervisorio.domain.ports.out.PlcGateway;
import br.com.sinterpiloto.supervisorio.domain.ports.out.RealtimePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adaptador de infraestrutura — publica mensagens via STOMP/WebSocket.
 *
 * Tópicos STOMP:
 *   /topic/plc/snapshot  → snapshot completo (analógicos + digitais) a cada poll
 *   /topic/plc/session   → mudanças de estado da sessão de queima
 *   /topic/plc/log       → eventos de log para exibição no painel
 *   /topic/plc/alarm     → alarmes e emergências
 *
 * NOTA: O SimpMessagingTemplate é injetado com @Lazy porque ele é criado pelo
 * Spring apenas após o broker STOMP ser inicializado (fase posterior ao contexto
 * principal). Sem @Lazy, a dependência circular impede o startup da aplicação.
 */
@Slf4j
@Component
public class StompRealtimePublisher implements RealtimePublisher {

    private static final String TOPIC_SNAPSHOT = "/topic/plc/snapshot";
    private static final String TOPIC_SESSION   = "/topic/plc/session";
    private static final String TOPIC_LOG       = "/topic/plc/log";
    private static final String TOPIC_ALARM     = "/topic/plc/alarm";

    // @Lazy: resolve o bean somente no primeiro uso, após o broker STOMP estar pronto
    private final SimpMessagingTemplate messagingTemplate;
    private final SnapshotMapper snapshotMapper;
    private final PlcGateway plcGateway;

    @Autowired
    public StompRealtimePublisher(
            @Lazy SimpMessagingTemplate messagingTemplate,
            SnapshotMapper snapshotMapper,
            PlcGateway plcGateway
    ) {
        this.messagingTemplate = messagingTemplate;
        this.snapshotMapper    = snapshotMapper;
        this.plcGateway        = plcGateway;
    }

    // ── RealtimePublisher ──────────────────────────────────────────────────

    @Override
    public void publishSnapshot(PlcSnapshot snapshot) {
        try {
            SnapshotMessage message = snapshotMapper.toMessage(
                    snapshot,
                    null,           // session injetada pelo BurningService se necessário
                    plcGateway.isConnected()
            );
            messagingTemplate.convertAndSend(TOPIC_SNAPSHOT, message);
            log.trace("Snapshot publicado: {} tags", snapshot.values().size());
        } catch (Exception ex) {
            log.error("Erro ao publicar snapshot via WebSocket", ex);
        }
    }

    @Override
    public void publishSessionState(BurningSession session) {
        try {
            SessionInfo info = new SessionInfo(
                    session.getId(),
                    session.getCurrentState(),
                    session.getStartedAt(),
                    session.isActive()
            );
            messagingTemplate.convertAndSend(TOPIC_SESSION, info);
            log.debug("Estado de sessão publicado: {} → {}", session.getId(), session.getCurrentState());
        } catch (Exception ex) {
            log.error("Erro ao publicar estado de sessão via WebSocket", ex);
        }
    }

    @Override
    public void publishLogEvent(String level, String message) {
        try {
            LogEvent event = new LogEvent(
                    Instant.now(),
                    level,
                    message,
                    null
            );
            messagingTemplate.convertAndSend(TOPIC_LOG, event);
            log.debug("[WS-LOG] [{}] {}", level, message);
        } catch (Exception ex) {
            log.error("Erro ao publicar log event via WebSocket", ex);
        }
    }

    @Override
    public void publishAlarm(String alarmCode, String description) {
        try {
            String severity = resolveSeverity(alarmCode);
            AlarmEvent event = new AlarmEvent(
                    Instant.now(),
                    alarmCode,
                    description,
                    severity
            );
            messagingTemplate.convertAndSend(TOPIC_ALARM, event);
            log.warn("[ALARM] [{}] [{}] {}", severity, alarmCode, description);
        } catch (Exception ex) {
            log.error("Erro ao publicar alarme via WebSocket", ex);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveSeverity(String alarmCode) {
        if (alarmCode.contains("EMERGENCY") || alarmCode.contains("EMERGENCIA")) return "CRITICAL";
        if (alarmCode.contains("COMM_ERROR") || alarmCode.contains("FALHA"))     return "HIGH";
        if (alarmCode.contains("TEMP") || alarmCode.contains("DEFEITO"))         return "MEDIUM";
        return "LOW";
    }
}