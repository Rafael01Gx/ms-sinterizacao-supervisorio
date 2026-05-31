package br.com.sinterpiloto.supervisorio.application.dto;

public record DigitalStatus(
        boolean valvulaGlpFechada,
        boolean valvulaGlpAberta,
        boolean valvula01Fechada,
        boolean valvula01Aberta,
        boolean valvula02Fechada,
        boolean valvula02Aberta,
        boolean valvulaCxVentoFechada,
        boolean valvulaCxVentoAberta,
        boolean queimadorPilotoAceso,
        boolean queimadorPrincipalAceso,
        boolean sopradorLigado,
        boolean emergenciaAcionada,
        boolean defeito,
        boolean alarmeEmergencia,
        boolean pilotoAcesso,
        boolean seloTempoSinterizacao
) {}
