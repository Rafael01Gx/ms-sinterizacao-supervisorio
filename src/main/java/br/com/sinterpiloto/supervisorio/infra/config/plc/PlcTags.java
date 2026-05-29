package br.com.sinterpiloto.supervisorio.infra.config.plc;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum PlcTags {
    // --- VARIÁVEIS ANALÓGICAS (Leitura de Sensores) ---
    TEMPERATURA_REFRATARIO("iTIT_01_Refratario_PV"),            // REAL
    TEMPO_SINTERIZACAO("Min_TempoSinterizacao"),                // REAL
    TEMPERATURA_IGNICAO("iTIT_02_Ignicao_PV"),                  // REAL
    TEMPERATURA_CX_VENTO("iTIT_03_Cx_Vento_PV"),                // REAL
    TEMPERATURA_CX_VENTO_MAX("Temp_CxVento_Max"),               // REAL
    PRESSAO_GLP("iPIT_01_GLP_PV"),                              // REAL
    VAZAO_GLP("iFIT_01_GLP_PV"),                                // REAL
    PRESSAO_GASES("iPIT_02_Gases_PV"),                          // REAL
    VAZAO_AR("iFIT_02_Ar_PV"),                                  // REAL
    TEMPO_PARCIAL_SINTERIZACAO("Parcial_TempoSinterizacao"),    // REAL

    // --- STATUS E ENTRADAS DIGITAIS (Sensores/Bits) ---
    VALVULA_GLP_FECHADA("iSV_GLP_Closed"),                      // BOOL
    VALVULA_GLP_ABERTA("iSV_GLP_Opened"),                       // BOOL
    VALVULA_01_FECHADA("iSV_01_Closed"),                        // BOOL
    VALVULA_01_ABERTA("iSV_01_Opened"),                         // BOOL
    VALVULA_02_FECHADA("iSV_02_Closed"),                        // BOOL
    VALVULA_02_ABERTA("iSV_02_Opened"),                         // BOOL
    VALVULA_CX_VENTO_FECHADA("iCx_Vento_Closed"),               // BOOL
    VALVULA_CX_VENTO_ABERTA("iCx_Vento_Opened"),                // BOOL
    QUEIMADOR_PILOTO_ACESO("iZS_Queimador_Piloto"),             // BOOL
    QUEIMADOR_PRINCIPAL_ACESO("iZS_Queimador_Principal"),       // BOOL
    PILOTO_PRONTO("iPiloto_Pronto"),                            // BOOL
    EMERGENCIA_ACIONADA("iZS_Emerg"),                           // BOOL
    SOPRADOR_LIGADO("iZS_Soprador"),                            // BOOL
    DEFEITO_GERAL("iDefeito_Bool"),                             // BOOL
    FIM_DE_CURSO_POSICAO("iFimDeCurso_Posicao"),                // BOOL
    TESTE_INICIADO("Teste_Iniciado"),                           // BOOL
    TESTE_IHM("Teste_IHM"),                                     // BOOL
    TRAVA_SUPERVISORIO("trava_supervi"),                        // BOOL
    INICIO_IHM("Inicio_HMI"),                                   // BOOL
    SUBINDO("Subindo"),                                         // BOOL
    DESCENDO("Descendo"),                                       // BOOL
    HAB_DESCENDO("Hab_Descendo"),                               // BOOL
    FINAL_QUEIMA("Final_Queima"),                               // BOOL
    SELO_TEMPO_SINTERIZACAO("Selo_TempoSinterizacao"),          // BOOL
    EMERGENCIA_ALARME1("EMERGENCIA_ALARME1"),                   // BOOL
    O_RESERVA_BOOL("oReserva_Bool"),                            // BOOL
    I_RESERVA_BOOL("iReserva_Bool"),                            // BOOL
    TESTE("teste"),                                             // BOOL

    // --- SAÍDAS E AÇÕES GERAIS ---
    GLP_FECHADA("oGLP_Fechada"),                                // BOOL
    SV_01_DUAS_SAIDAS("SV_01_DuasSaidas"),                      // BOOL
    SV_02_DUAS_SAIDAS("SV_02_DuasSaidas"),                      // BOOL
    AUX_A_SV_02("AUX_A_SV_02"),                                 // BOOL
    AUX_F_SV_02("AUX_F_SV_02"),                                 // BOOL
    CX_VENTO_OUT("oCx_Vento_Out"),                              // BOOL
    PILOTO_ACESSO("oPiloto_Acesso"),                            // BOOL

    // --- COMANDOS E SAÍDAS DIGITAIS (Escrita/Ações) ---
    ACIONAR_IGNICAO("oAciona_Ignicao"),                         // BOOL
    ACIONAR_SOPRADOR("oAciona_Soprador"),                       // BOOL
    ABRIR_PILOTO_GLP("oGLP_Piloto_Abrir"),                      // BOOL
    ABRIR_GLP_PRINCIPAL("oGLP_Principal_Abrir"),                // BOOL
    ABRIR_VALVULA_01("oSV_01_Abrir"),                           // BOOL
    FECHAR_VALVULA_01("oSV_01_Fechar"),                         // BOOL
    ABRIR_VALVULA_02("oSV_02_Abrir"),                           // BOOL
    FECHAR_VALVULA_02("oSV_02_Fechar"),                         // BOOL
    RESET_RELE("oReset_Rele"),                                  // BOOL
    RESET_IHM("Reset_IHM"),                                     // BOOL
    RESET_SEQ("Reset_Seq"),                                     // BOOL
    INICIAR_QUEIMA("Inicia_Queima"),                            // BOOL

    // --- TIMERS ---
    TEMPO_SINTERIZACAO_ACUMULADO("TempoSinterizacao.ACC"),      // DINT
    TEMPO_SINTERIZACAO_PRONTO("TempoSinterizacao.DN"),          // BOOL
    TEMPO_SINTERIZACAO_ZZZZ_ACC("TempoSinterizacaozzzz.ACC"),   // DINT
    TEMPO_RESFRIAMENTO_ACC("TempoResfriamento.ACC"),            // DINT
    MIN_TEMPO_RESFRIAMENTO("Min_TempoResfriamento"),            // REAL
    AVISO_120S_IHM_ACC("Aviso_120s_IHM.ACC"),                   // DINT
    AVISO_120S_IHM_DN("Aviso_120s_IHM.DN"),                     // BOOL
    AVISO_180S_IHM_ACC("Aviso_180s_IHM.ACC"),                   // DINT
    AVISO_180S_IHM_DN("Aviso_180s_IHM.DN"),                     // BOOL
    PARTIDA_CHAMA_PRINCIPAL_ACC("Partida_ChamaPrincipal_Timer.ACC"), // DINT
    DESLIGA_PILOTO_ACC("Desliga_PilotoTimer.ACC"),              // DINT
    SUBINDO_TIMER_ACC("Subindo_Timer.ACC"),                     // DINT
    DESCENDO_TIMER_ACC("Descendo_Timer.ACC"),                   // DINT
    FIM_DE_CURSO_POSICAO_TIMER_ACC("FimDeCurso_Posicao_Timer.ACC"), // DINT
    ESTABILIZACAO_500_TIMER_ACC("Estabilizacao_500_Timer.ACC"), // DINT
    TMR_ABRE_SV_002_ACC("TMR_ABRE_SV_002.ACC"),                 // DINT
    TMR_F_SV_02_ACC("TMR_F_SV_02.ACC"),                         // DINT
    TMR_A_SV_02_ACC("TMR_A_SV_02.ACC"),                         // DINT
    TMR_PULSO_ATUALIZA_TEMP_ACC("TMR_PULSO_ATUALIZA_TEMP.ACC"), // DINT

    // --- ALARMES E STATUS GERAIS ---
    ALARMES_CONSOLIDADOS("ALARMES_JUNTOS"),                     // DINT
    STATUS_CONSOLIDADOS("STATUS_JUNTOS"),                       // DINT
    VARIAVEIS_JUNTAS("VARIAVEIS_JUNTAS"),                       // DINT
    STATUS("Status");                                           // DINT

    private final String tagName;

    PlcTags(String tagName) {
        this.tagName = tagName;
    }

    public static List<String> getTags() {
        return Arrays.stream(PlcTags.values()).map(PlcTags::getTagName).toList();
    }
}
