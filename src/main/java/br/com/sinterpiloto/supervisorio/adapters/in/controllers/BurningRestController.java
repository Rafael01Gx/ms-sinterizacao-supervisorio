package br.com.sinterpiloto.supervisorio.adapters.in.controllers;

import br.com.sinterpiloto.supervisorio.application.dto.StatusResponse;
import br.com.sinterpiloto.supervisorio.domain.exceptions.PlcCommunicationException;
import br.com.sinterpiloto.supervisorio.domain.models.BurningSession;
import br.com.sinterpiloto.supervisorio.domain.ports.in.BurningCommandPort;
import br.com.sinterpiloto.supervisorio.domain.ports.in.BurningQueryPort;
import br.com.sinterpiloto.supervisorio.domain.ports.out.PlcGateway;
import br.com.sinterpiloto.supervisorio.infra.config.websocket.WebSocketSessionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/burning")
@RequiredArgsConstructor
public class BurningRestController {

    private final BurningCommandPort burningCommandPort;
    private final BurningQueryPort burningQueryPort;
    private final PlcGateway plcGateway;
    private final WebSocketSessionListener sessionListener;

    // ── Status ─────────────────────────────────────────────────────────────

    /**
     * GET /api/burning/status
     * Retorna status geral do sistema — útil para health check do frontend ao inicializar.
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> getStatus() {
        Optional<BurningSession> session = burningQueryPort.getActiveSession();

        StatusResponse response = new StatusResponse(
                plcGateway.isConnected(),
                session.map(s -> s.getCurrentState().name()).orElse("IDLE"),
                session.map(BurningSession::getId).orElse(null),
                session.map(BurningSession::getStartedAt).orElse(null),
                sessionListener.getActiveConnections().get()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/burning/snapshot
     * Retorna snapshot atual da PLC via HTTP (fallback para clientes sem WebSocket).
     */
    @GetMapping("/snapshot")
    public ResponseEntity<?> getSnapshot() {
        try {
            var snapshot = burningQueryPort.getCurrentSnapshot();
            return ResponseEntity.ok(snapshot.values());
        } catch (PlcCommunicationException ex) {
            log.error("Erro ao obter snapshot via HTTP", ex);
            return ResponseEntity.internalServerError()
                    .body("PLC inacessível: " + ex.getMessage());
        }
    }

    // ── Comandos HTTP ──────────────────────────────────────────────────────

    /** POST /api/burning/start */
    @PostMapping("/start")
    public ResponseEntity<Void> start() {
        burningCommandPort.startBurning();
        return ResponseEntity.accepted().build();
    }

    /** POST /api/burning/stop */
    @PostMapping("/stop")
    public ResponseEntity<Void> stop() {
        burningCommandPort.stopBurning();
        return ResponseEntity.accepted().build();
    }

    /** POST /api/burning/emergency */
    @PostMapping("/emergency")
    public ResponseEntity<Void> emergency() {
        burningCommandPort.triggerEmergency();
        return ResponseEntity.accepted().build();
    }

    /** POST /api/burning/reset */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        burningCommandPort.resetFault();
        return ResponseEntity.accepted().build();
    }

    /** POST /api/burning/force-main */
    @PostMapping("/force-main")
    public ResponseEntity<Void> forceMain() {
        burningCommandPort.forceMainBurner();
        return ResponseEntity.accepted().build();
    }

}