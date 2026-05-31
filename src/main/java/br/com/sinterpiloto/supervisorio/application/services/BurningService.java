package br.com.sinterpiloto.supervisorio.application.services;

import br.com.sinterpiloto.supervisorio.domain.enums.BurningState;
import br.com.sinterpiloto.supervisorio.domain.exceptions.PlcCommunicationException;
import br.com.sinterpiloto.supervisorio.domain.models.BurningSession;
import br.com.sinterpiloto.supervisorio.domain.models.PlcSnapshot;
import br.com.sinterpiloto.supervisorio.domain.ports.in.BurningCommandPort;
import br.com.sinterpiloto.supervisorio.domain.ports.in.BurningQueryPort;
import br.com.sinterpiloto.supervisorio.domain.ports.out.PlcGateway;
import br.com.sinterpiloto.supervisorio.domain.ports.out.RealtimePublisher;
import br.com.sinterpiloto.supervisorio.infra.config.plc.PlcTags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class BurningService implements BurningCommandPort, BurningQueryPort {

    private final PlcGateway plcGateway;
    private final RealtimePublisher realtimePublisher;



    // Estado da sessão atual — thread-safe via AtomicReference
    private final AtomicReference<BurningSession> activeSession = new AtomicReference<>();

    // ── Polling periódico: lê PLC e publica via WebSocket ─────────────────

    @Scheduled(fixedRateString = "${plc.polling.process-interval-ms:500}")
    public void pollAndPublish() {
        try {
            if (!plcGateway.isConnected()) {
                realtimePublisher.publishLogEvent("WARN", "PLC desconectada — aguardando reconexão");
                return;
            }

            PlcSnapshot snapshot = plcGateway.readAll();
            realtimePublisher.publishSnapshot(snapshot);

            // Verifica alarmes ativos no snapshot
            checkAlarms(snapshot);

        } catch (PlcCommunicationException ex) {
            log.error("Erro de comunicação com a PLC durante polling", ex);
            realtimePublisher.publishAlarm("PLC_COMM_ERROR", "Falha na comunicação com a PLC: " + ex.getMessage());
        }
    }

    // ── BurningCommandPort ─────────────────────────────────────────────────

    @Override
    public void startBurning() {
        if (activeSession.get() != null && activeSession.get().isActive()) {
            realtimePublisher.publishLogEvent("WARN", "Já existe uma queima em andamento");
            return;
        }

        BurningSession session = BurningSession.start();
        activeSession.set(session);

        log.info("Iniciando queima piloto — session={}", session.getId());
        session.transition(BurningState.IDLE, "Sequência iniciada pelo supervisório");

        // Escreve o comando de início na PLC
        plcGateway.writeBoolean(PlcTags.INICIO_IHM.getTagName(), true);
        plcGateway.writeBoolean(PlcTags.INICIAR_QUEIMA.getTagName(), true);

        session.transition(BurningState.PRE_PURGA, "Comando enviado à PLC — iniciando pré-purga");
        realtimePublisher.publishSessionState(session);
        realtimePublisher.publishLogEvent("INFO", "Queima iniciada — sessão " + session.getId());
    }

    @Override
    public void stopBurning() {
        BurningSession session = requireActiveSession();

        log.info("Finalizando queima — session={}", session.getId());
        plcGateway.writeBoolean(PlcTags.FINAL_QUEIMA.getTagName(), true);

        session.transition(BurningState.DESLIGAMENTO, "Comando de parada enviado pelo supervisório");
        realtimePublisher.publishSessionState(session);
        realtimePublisher.publishLogEvent("INFO", "Desligamento iniciado");
    }

    @Override
    public void triggerEmergency() {
        BurningSession session = activeSession.get();
        log.warn("EMERGÊNCIA acionada pelo supervisório — session={}",
                session != null ? session.getId() : "sem sessão ativa");

        // Fecha tudo imediatamente na PLC
        plcGateway.writeBoolean(PlcTags.EMERGENCIA_ALARME1.getTagName(), true);
        plcGateway.writeBoolean(PlcTags.FECHAR_VALVULA_01.getTagName(), true);
        plcGateway.writeBoolean(PlcTags.ABRIR_VALVULA_01.getTagName(), false);
        plcGateway.writeBoolean(PlcTags.FECHAR_VALVULA_02.getTagName(), true);
        plcGateway.writeBoolean(PlcTags.ABRIR_VALVULA_02.getTagName(), false);
        plcGateway.writeBoolean(PlcTags.ABRIR_PILOTO_GLP.getTagName(), false);
        plcGateway.writeBoolean(PlcTags.ABRIR_GLP_PRINCIPAL.getTagName(), false);
        plcGateway.writeBoolean(PlcTags.ACIONAR_IGNICAO.getTagName(), false);


        if (session != null) {
            session.transition(BurningState.FALHA, "EMERGÊNCIA acionada pelo operador");
            session.finish();
            realtimePublisher.publishSessionState(session);
        }

        realtimePublisher.publishAlarm("EMERGENCY_STOP", "Parada de emergência acionada pelo operador");
        realtimePublisher.publishLogEvent("ERROR", "🚨 PARADA DE EMERGÊNCIA");
    }

    @Override
    public void resetFault() {
        log.info("Reset de falha solicitado");

        plcGateway.writeBoolean(PlcTags.RESET_IHM.getTagName(), true);
        plcGateway.writeBoolean(PlcTags.RESET_SEQ.getTagName(), true);
        plcGateway.writeBoolean(PlcTags.EMERGENCIA_ALARME1.getTagName(), false);

        BurningSession session = activeSession.get();
        if (session != null) {
            session.transition(BurningState.IDLE, "Falha resetada pelo operador");
            realtimePublisher.publishSessionState(session);
        }

        realtimePublisher.publishLogEvent("INFO", "Reset de falha executado — sistema em IDLE");
    }

    @Override
    public void forceMainBurner() {
        BurningSession session = requireActiveSession();
        log.info("Forçando abertura do queimador principal — session={}", session.getId());

        plcGateway.writeBoolean(PlcTags.TESTE_IHM.getTagName(), true);

        session.transition(BurningState.ABRE_SV02, "Queimador principal forçado pelo operador");
        realtimePublisher.publishSessionState(session);
        realtimePublisher.publishLogEvent("WARN", "⚡ Queimador principal forçado manualmente");
    }

    // ── BurningQueryPort ───────────────────────────────────────────────────

    @Override
    public PlcSnapshot getCurrentSnapshot() {
        return plcGateway.readAll();
    }

    @Override
    public Optional<BurningSession> getActiveSession() {
        return Optional.ofNullable(activeSession.get())
                .filter(BurningSession::isActive);
    }

    // ── Verificação de alarmes ─────────────────────────────────────────────

    private void checkAlarms(PlcSnapshot snapshot) {
        Boolean emergencia = snapshot.get(PlcTags.EMERGENCIA_ALARME1.getTagName(), Boolean.class);
        if (Boolean.TRUE.equals(emergencia)) {
            realtimePublisher.publishAlarm("EMERGENCIA_ALARME1", "Alarme de emergência ativo na PLC");
        }

        Boolean defeito = snapshot.get(PlcTags.DEFEITO_GERAL.getTagName(), Boolean.class);
        if (Boolean.TRUE.equals(defeito)) {
            realtimePublisher.publishAlarm("DEFEITO_GERAL", "Defeito geral detectado na PLC");
        }

        Float tempCxVento = snapshot.get(PlcTags.TEMPERATURA_CX_VENTO.getTagName(), Float.class);
        Float tempCxVentoMax = snapshot.get(PlcTags.TEMPERATURA_CX_VENTO_MAX.getTagName(), Float.class);
        if (tempCxVento != null && tempCxVentoMax != null && tempCxVento > tempCxVentoMax) {
            realtimePublisher.publishAlarm("TEMP_CX_VENTO_HH",
                    "Temperatura caixa de vento excedida: %.1f°C (max: %.1f°C)"
                            .formatted(tempCxVento, tempCxVentoMax));
        }
    }

    private BurningSession requireActiveSession() {
        BurningSession session = activeSession.get();
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("Nenhuma queima ativa no momento");
        }
        return session;
    }
}
