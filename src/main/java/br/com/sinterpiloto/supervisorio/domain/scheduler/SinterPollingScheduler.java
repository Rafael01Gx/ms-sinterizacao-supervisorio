package br.com.sinterpiloto.supervisorio.domain.scheduler;

import br.com.sinterpiloto.supervisorio.infra.config.plc.PlcTags;
import br.com.sinterpiloto.supervisorio.infra.config.properties.PlcProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PreDestroy;

import java.util.List;

@Slf4j
@Component
// Garante que o Scheduler só inicialize DEPOIS que o driver do PLC estiver totalmente pronto no container
@DependsOn("plcDriverManager")
public class SinterPollingScheduler {

    private final PlcDriverManager plcDriverManager;
    private final String connectionString;
    private PlcConnection plcConnection;
    private boolean isReady = false;

    public SinterPollingScheduler(PlcDriverManager plcDriverManager, PlcProperties plcProperties) {
        this.connectionString = plcProperties.connectionString();
        this.plcDriverManager = plcDriverManager;
        this.isReady = true;
    }

    @Scheduled(fixedRateString = "${plc.polling.process-interval-ms:250}")
    public void readProcessVariables() {
        if (!isReady) return;
        log.debug("====== EXECUTANDO LOOP PROCESSO ======");
        List<String> rawTags = PlcTags.getTags();
        executePolling(rawTags, "PROCESSO");
    }

    @Scheduled(fixedRateString = "${plc.polling.alarm-interval-ms:250}")
    public void readAlarmVariables() {
        if (!isReady) return;
        log.debug("====== EXECUTANDO LOOP ALARME ======");
        List<String> rawTags = List.of(PlcTags.EMERGENCIA_ALARME1.getTagName(), PlcTags.ALARMES_CONSOLIDADOS.getTagName());
        executePolling(rawTags, "ALARME");
    }

    private synchronized void executePolling(List<String> tagAddresses, String contexto) {
        if (tagAddresses == null || tagAddresses.isEmpty()) {
            return;
        }

        try {
            if (plcConnection == null || !plcConnection.isConnected()) {
                log.info("Estabelecendo conexão persistente com o CLP via Logix...");
                this.plcConnection = plcDriverManager.getConnectionManager().getConnection(connectionString);
            }

            if (plcConnection.isConnected() && plcConnection.getMetadata().isReadSupported()) {
                PlcReadRequest.Builder builder = plcConnection.readRequestBuilder();

                // Adiciona dinamicamente as tags usando o NOME COMPLETO como ID (alias)
                for (String tagAddress : tagAddresses) {
                    // Ao usar o tagAddress como chave e como endereço, não há duplicatas!
                    builder.addTagAddress(tagAddress, tagAddress);
                }

                PlcReadRequest readRequest = builder.build();
                PlcReadResponse response = readRequest.execute().get();

                for (String alias : response.getTagNames()) { // O alias aqui agora é o nome completo da tag
                    if (response.getResponseCode(alias) == PlcResponseCode.OK) {
                        Object valor = response.getObject(alias);

                        // Exibe o nome real da tag no painel (ex: TempoSinterizacao.ACC)
                        log.info("[{}] Tag [{}] atualizada: {}", contexto, alias, valor);

                        // TODO: Aqui você já pode empacotar e mandar o mapa completo pro WebSocket!

                    } else {
                        log.warn("[{}] Falha na tag [{}]: {}", contexto, alias, response.getResponseCode(alias));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro na comunicação no grupo [{}]: {}", contexto, e.getMessage());
            this.plcConnection = null;
        }
    }

    @PreDestroy
    public void closeConnection() {
        try {
            if (plcConnection != null && plcConnection.isConnected()) {
                plcConnection.close();
            }
        } catch (Exception e) {
            log.error("Erro ao fechar conexão PLC", e);
        }
    }
}