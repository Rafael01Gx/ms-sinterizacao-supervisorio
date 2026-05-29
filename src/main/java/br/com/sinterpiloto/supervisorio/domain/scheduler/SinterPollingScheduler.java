package br.com.sinterpiloto.supervisorio.domain.scheduler;

import br.com.sinterpiloto.supervisorio.infra.config.plc.PlcTags;
import br.com.sinterpiloto.supervisorio.infra.config.properties.PlcProperties;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SinterPollingScheduler {

    private final PlcDriverManager plcDriverManager;
    private final PlcProperties plcProperties;

    public SinterPollingScheduler(PlcDriverManager plcDriverManager, PlcProperties plcProperties) {
        this.plcDriverManager = plcDriverManager;
        this.plcProperties = plcProperties;
    }

    /**
     * LOOP 1: Executa a cada 500ms lendo estritamente as tags de PROCESSO
     */
    @Scheduled(fixedRateString = "${plc.polling.process-interval-ms}")
    public void readProcessVariables() {
        // Passa a lista específica: "plcProperties.tags().process()"

        List<String> rawTags = PlcTags.getTags();
        executePolling(rawTags, "PROCESSO");
    }

    /**
     * LOOP 2: Executa a cada 250ms lendo estritamente as tags de ALARME
     */
    @Scheduled(fixedRateString = "${plc.polling.alarm-interval-ms}")
    public void readAlarmVariables() {
        // Passa a lista específica: "plcProperties.tags().alarm()"
        List<String> rawTags = List.of(PlcTags.EMERGENCIA_ALARME1.getTagName(), PlcTags.ALARMES_CONSOLIDADOS.getTagName());
        executePolling(rawTags, "ALARME");
    }

    /**
     * Método auxiliar genérico que faz o trabalho pesado para qualquer lista de tags
     */
    private void executePolling(List<String> tagAddresses, String contexto) {
        // Proteção caso a lista esteja vazia no YAML
        if (tagAddresses == null || tagAddresses.isEmpty()) {
            return;
        }

        String connectionUrl = String.format("eip://%s:44818?backplane=1&slot=0", plcProperties.address());

        // Correção aplicada: .getConnectionManager().getConnection(...)
        try (PlcConnection connection = plcDriverManager.getConnectionManager().getConnection(connectionUrl)) {

            if (connection.isConnected() && connection.getMetadata().isReadSupported()) {

                PlcReadRequest.Builder builder = connection.readRequestBuilder();

                // Adiciona dinamicamente as tags da lista que foi passada por parâmetro
                for (String tagAddress : tagAddresses) {
                    String alias = tagAddress.substring(tagAddress.lastIndexOf(".") + 1);
                    builder.addTagAddress(alias, tagAddress);
                }

                PlcReadRequest readRequest = builder.build();

                // Graças ao Java 25 + Virtual Threads, essas duas requisições paralelas (Processo e Alarme)
                // rodam sem disputar ou travar a CPU do seu servidor Tomcat!
                PlcReadResponse response = readRequest.execute().get();

                for (String alias : response.getTagNames()) {
                    if (response.getResponseCode(alias) == PlcResponseCode.OK) {
                        Object valor = response.getObject(alias);

                        // Aqui você direcionaria o fluxo:
                        // Se contexto.equals("ALARME") envia para o tópico de alertas do WebSocket, etc.
                        System.out.printf("[%s] Tag [%s] atualizada: %s%n", contexto, alias, valor);

                    } else {
                        System.err.printf("[%s] Falha na tag [%s]: %s%n", contexto, alias, response.getResponseCode(alias));
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("Falha temporária de comunicação no grupo [%s]: %s%n", contexto, e.getMessage());
        }
    }
}