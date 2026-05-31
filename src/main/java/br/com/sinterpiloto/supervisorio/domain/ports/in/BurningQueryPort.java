package br.com.sinterpiloto.supervisorio.domain.ports.in;

import br.com.sinterpiloto.supervisorio.domain.models.BurningSession;
import br.com.sinterpiloto.supervisorio.domain.models.PlcSnapshot;

import java.util.Optional;

/**
 * Porta de entrada para consultas sobre estado atual do processo.
 */
public interface BurningQueryPort {

    /** Retorna o snapshot atual da PLC (leitura ao vivo). */
    PlcSnapshot getCurrentSnapshot();

    /** Retorna a sessão de queima ativa, se existir. */
    Optional<BurningSession> getActiveSession();
}