package br.com.sinterpiloto.supervisorio.application.dto;

public record AnalogicData(
        float temperaturaRefratario,
        float temperaturaIgnicao,
        float temperaturaCxVento,
        float temperaturaCxVentoMax,
        float pressaoGlp,
        float vazaoGlp,
        float pressaoGases,
        float vazaoAr,
        float tempoSinterizacao,
        float tempoParcialSinterizacao,
        float tempoResfriamento
) {}
