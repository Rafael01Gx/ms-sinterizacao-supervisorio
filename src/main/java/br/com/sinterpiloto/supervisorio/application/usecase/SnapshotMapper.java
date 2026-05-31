package br.com.sinterpiloto.supervisorio.application.usecase;

import br.com.sinterpiloto.supervisorio.application.dto.AnalogicData;
import br.com.sinterpiloto.supervisorio.application.dto.DigitalStatus;
import br.com.sinterpiloto.supervisorio.application.dto.SessionInfo;
import br.com.sinterpiloto.supervisorio.application.dto.SnapshotMessage;
import br.com.sinterpiloto.supervisorio.domain.models.BurningSession;
import br.com.sinterpiloto.supervisorio.domain.models.PlcSnapshot;
import br.com.sinterpiloto.supervisorio.infra.config.plc.PlcTags;
import org.springframework.stereotype.Component;

/**
 * Mapeia PlcSnapshot (domínio) → DTOs (aplicação).
 * Centraliza toda a extração e conversão de valores da PLC.
 */
@Component
public class SnapshotMapper {

    public SnapshotMessage toMessage(
            PlcSnapshot snapshot,
            BurningSession session,
            boolean plcConnected
    ) {
        return new SnapshotMessage(
                snapshot.timestamp(),
                buildAnalogic(snapshot),
                buildDigital(snapshot),
                buildSession(session),
                plcConnected
        );
    }

    private AnalogicData buildAnalogic(PlcSnapshot s) {
        return new AnalogicData(
                getFloat(s, PlcTags.TEMPERATURA_REFRATARIO),
                getFloat(s, PlcTags.TEMPERATURA_IGNICAO),
                getFloat(s, PlcTags.TEMPERATURA_CX_VENTO),
                getFloat(s, PlcTags.TEMPERATURA_CX_VENTO_MAX),
                getFloat(s, PlcTags.PRESSAO_GLP),
                getFloat(s, PlcTags.VAZAO_GLP),
                getFloat(s, PlcTags.PRESSAO_GASES),
                getFloat(s, PlcTags.VAZAO_AR),
                getFloat(s, PlcTags.TEMPO_SINTERIZACAO),
                getFloat(s, PlcTags.TEMPO_PARCIAL_SINTERIZACAO),
                getFloat(s, PlcTags.MIN_TEMPO_RESFRIAMENTO)
        );
    }

    private DigitalStatus buildDigital(PlcSnapshot s) {
        return new DigitalStatus(
                getBool(s, PlcTags.VALVULA_GLP_FECHADA),
                getBool(s, PlcTags.VALVULA_GLP_ABERTA),
                getBool(s, PlcTags.VALVULA_01_FECHADA),
                getBool(s, PlcTags.VALVULA_01_ABERTA),
                getBool(s, PlcTags.VALVULA_02_FECHADA),
                getBool(s, PlcTags.VALVULA_02_ABERTA),
                getBool(s, PlcTags.VALVULA_CX_VENTO_FECHADA),
                getBool(s, PlcTags.VALVULA_CX_VENTO_ABERTA),
                getBool(s, PlcTags.QUEIMADOR_PILOTO_ACESO),
                getBool(s, PlcTags.QUEIMADOR_PRINCIPAL_ACESO),
                getBool(s, PlcTags.SOPRADOR_LIGADO),
                getBool(s, PlcTags.EMERGENCIA_ACIONADA),
                getBool(s, PlcTags.DEFEITO_GERAL),
                getBool(s, PlcTags.EMERGENCIA_ALARME1),
                getBool(s, PlcTags.PILOTO_ACESSO),
                getBool(s, PlcTags.SELO_TEMPO_SINTERIZACAO)
        );
    }

    private SessionInfo buildSession(BurningSession session) {
        if (session == null) return null;
        return new SessionInfo(
                session.getId(),
                session.getCurrentState(),
                session.getStartedAt(),
                session.isActive()
        );
    }

    // ── Helpers de extração segura ─────────────────────────────────────────

    private float getFloat(PlcSnapshot s, PlcTags tag) {
        Float val = s.get(tag.getTagName(), Float.class);
        return val != null ? val : 0f;
    }

    private boolean getBool(PlcSnapshot s, PlcTags tag) {
        Boolean val = s.get(tag.getTagName(), Boolean.class);
        return val != null && val;
    }
}