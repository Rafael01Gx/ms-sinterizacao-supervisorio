package br.com.sinterpiloto.supervisorio.infra.adapter.plc;

import br.com.sinterpiloto.supervisorio.domain.exceptions.PlcCommunicationException;
import br.com.sinterpiloto.supervisorio.domain.models.PlcSnapshot;
import br.com.sinterpiloto.supervisorio.domain.ports.out.PlcGateway;
import br.com.sinterpiloto.supervisorio.infra.config.plc.PlcTags;
import br.com.sinterpiloto.supervisorio.infra.config.properties.PlcProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class Plc4xGateway implements PlcGateway {

    private final PlcProperties plcProperties;
    private PlcConnection connection;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @PostConstruct
    public void connect() {
        String connectionString = plcProperties.connectionString();
        log.info("Conectando à PLC: {}", connectionString);
        try {
            connection = PlcDriverManager.getDefault()
                    .getConnectionManager()
                    .getConnection(connectionString);
            connected.set(connection.isConnected());
            log.info("PLC conectada: {}", connected.get());
        } catch (Exception ex) {
            connected.set(false);
            log.error("Falha ao conectar à PLC — modo offline ativo", ex);
        }
    }

    @PreDestroy
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Conexão com a PLC encerrada");
            } catch (Exception ex) {
                log.warn("Erro ao fechar conexão com a PLC", ex);
            }
        }
    }

    // ── PlcGateway ─────────────────────────────────────────────────────────

    @Override
    public PlcSnapshot readAll() {
        ensureConnected();

        try {
            PlcReadRequest.Builder builder = connection.readRequestBuilder();

            // Adiciona todas as tags declaradas no enum
            for (PlcTags tag : PlcTags.values()) {
                builder.addTagAddress(tag.name(), buildTagAddress(tag.getTagName()));
            }

            PlcReadRequest request = builder.build();
            PlcReadResponse response = request.execute().get(plcProperties.timeout(), TimeUnit.MILLISECONDS);

            return mapToSnapshot(response);

        } catch (Exception ex) {
            throw new PlcCommunicationException("Falha na leitura da PLC", ex);
        }
    }

    @Override
    public void writeBoolean(String enumOrTagName, boolean value) {
        ensureConnected();

        // 1. Garante que estamos pegando a tag real do CLP cadastrada no Enum
        String realTagName = java.util.Arrays.stream(PlcTags.values())
                .filter(t -> t.name().equalsIgnoreCase(enumOrTagName) || t.getTagName().equalsIgnoreCase(enumOrTagName))
                .map(PlcTags::getTagName)
                .findFirst()
                .orElse(enumOrTagName);

        // 2. Força o sufixo :BOOL para que o PLC4X monte o pacote CIP exato de bit sem precisar adivinhar
        String formattedAddress = realTagName.contains(":") ? realTagName : realTagName + ":BOOL";

        try {
            log.debug("Enviando escrita CIP [BOOL] -> {} = {}", formattedAddress, value);

            PlcWriteRequest request = connection.writeRequestBuilder()
                    .addTagAddress(realTagName, formattedAddress, value)
                    .build();

            // Executa com o timeout definido no YML
            var response = request.execute().thenAccept(plcWriteResponse -> {
                PlcResponseCode code = plcWriteResponse.getResponseCode(realTagName);

                if (code != PlcResponseCode.OK) {
                    log.warn("Escrita BOOL retornou código: {} para tag: {}", code, realTagName);
                } else {
                    log.info("Escrita efetuada com sucesso -> {} = {}", realTagName, value);
                }
            });

        } catch (Exception ex) {
            log.error("Erro crítico ao escrever na tag física {}", realTagName, ex);
            throw new PlcCommunicationException("Falha ao escrever BOOL na tag: " + realTagName, ex);
        }
    }

    @Override
    @Async("plcExecutor")
    public void writeBatch(Map<String, Object> tagsToWrite) {
        ensureConnected();

        if (tagsToWrite == null || tagsToWrite.isEmpty()) {
            return;
        }

        try {
            PlcWriteRequest.Builder builder = connection.writeRequestBuilder();

            // Mapeamento para sabermos qual endereço real corresponde a qual chave depois
            Map<String, String> trackingMap = new HashMap<>();

            for (Map.Entry<String, Object> entry : tagsToWrite.entrySet()) {
                String aliasOrTagName = entry.getKey();
                Object value = entry.getValue();

                // 1. Resolve o nome real da tag através do seu Enum PlcTags
                String realTagName = java.util.Arrays.stream(PlcTags.values())
                        .filter(t -> t.name().equalsIgnoreCase(aliasOrTagName) || t.getTagName().equalsIgnoreCase(aliasOrTagName))
                        .map(PlcTags::getTagName)
                        .findFirst()
                        .orElse(aliasOrTagName);

                // 2. Formata o endereço com o sufixo correto baseado no tipo do dado enviado
                String formattedAddress = realTagName;
                if (!realTagName.contains(":")) {
                    if (value instanceof Boolean) {
                        formattedAddress = realTagName + ":BOOL";
                    } else if (value instanceof Float || value instanceof Double) {
                        formattedAddress = realTagName + ":REAL";
                    } else if (value instanceof Integer || value instanceof Long) {
                        formattedAddress = realTagName + ":DINT";
                    }
                }

                // 3. Adiciona ao lote (o método aceita Object, mas o PLC4X valida o tipo interno)
                builder.addTagAddress(realTagName, formattedAddress, value);
                trackingMap.put(realTagName, formattedAddress);
            }

            // Constrói e envia todas as escritas em um único pacote
            PlcWriteRequest request = builder.build();
            var response = request.execute().get(plcProperties.timeout(), TimeUnit.MILLISECONDS);

            // 4. Valida a resposta de cada tag enviada
            for (String realTagName : trackingMap.keySet()) {
                PlcResponseCode code = response.getResponseCode(realTagName);
                if (code != PlcResponseCode.OK) {
                    log.warn("Falha na escrita em lote para a tag: {} (Código: {})", realTagName, code);
                } else {
                    log.debug("Escrita em lote confirmada para -> {}", realTagName);
                }
            }

        } catch (Exception ex) {
            log.error("Erro crítico na execução da escrita em lote", ex);
            throw new PlcCommunicationException("Falha ao executar escrita em lote na PLC", ex);
        }
    }

    @Override
    public void writeFloat(String enumOrTagName, float value) {
        ensureConnected();

        String realTagName = java.util.Arrays.stream(PlcTags.values())
                .filter(t -> t.name().equalsIgnoreCase(enumOrTagName) || t.getTagName().equalsIgnoreCase(enumOrTagName))
                .map(PlcTags::getTagName)
                .findFirst()
                .orElse(enumOrTagName);

        // Força o sufixo :REAL para variáveis analógicas de ponto flutuante
        String formattedAddress = realTagName.contains(":") ? realTagName : realTagName + ":REAL";

        try {
            PlcWriteRequest request = connection.writeRequestBuilder()
                    .addTagAddress(realTagName, formattedAddress, value)
                    .build();

            var response = request.execute().thenAccept(plcWriteResponse -> {

                PlcResponseCode code = plcWriteResponse.getResponseCode(realTagName);

                if (code != PlcResponseCode.OK) {
                    log.warn("Escrita REAL retornou código: {} para tag: {}", code, realTagName);
                }
            });
        } catch (Exception ex) {
            throw new PlcCommunicationException("Falha ao escrever REAL na tag: " + realTagName, ex);
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get() && connection != null && connection.isConnected();
    }

    // ── Helpers privados ───────────────────────────────────────────────────

    /**
     * Formata o endereço da tag no padrão EIP do PLC4X.
     * Timers com subcomponente (ex: TempoSinterizacao.ACC) são tratados como DINT.
     */
    private String buildTagAddress(String tagName) {
        // Acumuladores (.ACC) de Timers/Counters são inteiros de 32 bits -> DINT
        if (tagName.contains(".ACC")) {
            return tagName + ":DINT";
        }
        // Bits de Done (.DN) de Timers/Counters são estritamente booleanos -> BOOL
        if (tagName.contains(".DN")) {
            return tagName + ":BOOL";
        }
        // Tags de consolidação de dados -> DINT
        if (tagName.endsWith("_JUNTOS") || tagName.equals("Status")) {
            return tagName + ":DINT";
        }

        return tagName; // O PLC4X resolve o tipo via mapeamento CIP para o resto
    }

    /**
     * Converte PlcReadResponse → PlcSnapshot (domínio puro).
     */
    private PlcSnapshot mapToSnapshot(PlcReadResponse response) {
        Map<String, Object> values = new HashMap<>();

        for (PlcTags tag : PlcTags.values()) {
            try {
                PlcResponseCode code = response.getResponseCode(tag.name());
                if (code != PlcResponseCode.OK) {
                    log.trace("Tag {} retornou código: {}", tag.getTagName(), code);
                    continue;
                }

                Object value = extractValue(response, tag);
                if (value != null) {
                    values.put(tag.getTagName(), value);
                }
            } catch (Exception ex) {
                log.trace("Erro ao extrair tag {}: {}", tag.getTagName(), ex.getMessage());
            }
        }

        return new PlcSnapshot(Instant.now(), values);
    }

    private Object extractValue(PlcReadResponse response, PlcTags tag) {
        String name = tag.name();
        String tagName = tag.getTagName();

        // DINT (timers e consolidados)
        if (tagName.contains(".ACC") || tagName.contains(".DN") ||
                tagName.endsWith("_JUNTOS") || tagName.equals("Status")) {
            return response.getInteger(name);
        }

        // REAL (analógicos — prefixo i/o + tipos conhecidos)
        if (tagName.startsWith("iTIT") || tagName.startsWith("iFIT") ||
                tagName.startsWith("iPIT") || tagName.startsWith("Min_") ||
                tagName.startsWith("Temp_") || tagName.startsWith("Parcial_") ||
                tagName.endsWith("_Real") || tagName.equals("iReserva_Real") ||
                tagName.equals("oReserva_Real")) {
            Number val = response.getDouble(name).floatValue() != 0
                    ? response.getDouble(name)
                    : null;
            if (val == null) return response.getFloat(name);
            return response.getFloat(name);
        }

        // BOOL (todos os demais)
        return response.getBoolean(name);
    }

    private void ensureConnected() {
        if (!isConnected()) {
            log.warn("PLC não conectada — tentando reconexão...");
            connect();
            if (!isConnected()) {
                throw new PlcCommunicationException("PLC inacessível", null);
            }
        }
    }
}