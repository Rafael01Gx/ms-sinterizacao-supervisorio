package br.com.sinterpiloto.supervisorio.domain.ports.in;

/**
 * Porta de entrada (driving port) — define os casos de uso
 * disponíveis para controle do processo de queima.
 */
public interface BurningCommandPort {

    /** Inicia a sequência completa de queima piloto. */
    void startBurning();

    /** Finaliza a queima de forma ordenada (desligamento + resfriamento). */
    void stopBurning();

    /** Aciona parada de emergência imediata. */
    void triggerEmergency();

    /** Reseta alarmes e volta ao estado IDLE após falha. */
    void resetFault();

    /** Força abertura do queimador principal (bypass de aquecimento). */
    void forceMainBurner();
}
