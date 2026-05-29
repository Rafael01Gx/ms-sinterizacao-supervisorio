package br.com.sinterpiloto.supervisorio.adapters.plc;

import br.com.sinterpiloto.supervisorio.infra.config.plc.PlcTags;
import br.com.sinterpiloto.supervisorio.infra.config.properties.PlcProperties;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.messages.*;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Controller de diagnóstico e teste para comunicação com PLC Allen-Bradley ControlLogix 1756.
 *
 * REGRAS DE SINTAXE DE TAGS (ControlLogix via EtherNet/IP):
 *   - Tag global:               NomeDaTag
 *   - Tag de programa:          Program:NomePrograma.NomeDaTag
 *   - Elemento de array:        NomeDaTag[0]
 *   - Campo de UDT:             NomeDaTag.Campo
 *   - NÃO usar :BOOL, :DINT etc — o driver descobre o tipo automaticamente.
 *   - NÃO usar AT%QW — essa é sintaxe CODESYS/WAGO, incompatível com ControlLogix.
 *
 * URL de conexão:
 *   logix://<IP>:44818?backplane=1&slot=0&force-unconnected-operation=true
 *   - backplane: número do backplane (normalmente 1)
 *   - slot:      slot do processador no chassi (verificar no Studio 5000; normalmente 0)
 */
@RestController
@RequestMapping("/api/plc")
public class PlcTestController {

    private static final Logger log = LoggerFactory.getLogger(PlcTestController.class);

    private static final int    TIMEOUT_SECONDS     = 10;
    private static final String BROWSE_QUERY_LABEL  = "allTags";

    private final PlcDriverManager plcDriverManager;
    private final PlcProperties    plcProperties;

    public PlcTestController(PlcDriverManager plcDriverManager, PlcProperties plcProperties) {
        this.plcDriverManager = plcDriverManager;
        this.plcProperties    = plcProperties;
    }

    // ─────────────────────────────────────────────────────────────
    // Utilitário: monta a URL de conexão a partir das propriedades
    // ─────────────────────────────────────────────────────────────

    private String buildConnectionUrl() {
        return String.format(
                "logix://%s:44818?backplane=1&slot=0&force-unconnected-operation=true",
                plcProperties.address()
        );
    }

    /**
     * Sanitiza o endereço de uma tag removendo anotações de tipo (:BOOL, :DINT, :WORD...)
     * e sufixos de endereço IEC 61131 (AT%QW...) que são inválidos no ControlLogix.
     *
     * Exemplos de entrada e saída:
     *   "PARADA_RELOGIO_INICIO_CICLO1:BOOL"         → "PARADA_RELOGIO_INICIO_CICLO1"
     *   "PARADA_RELOGIO_FINAL_CICLO1AT%QW346:BOOL"  → "PARADA_RELOGIO_FINAL_CICLO1"
     *   "Program:MainProgram.Temperatura_Zona1"     → inalterado (sintaxe correta)
     */
    private String sanitizeTagAddress(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        // Remove sufixo IEC (AT%QW...) antes de tratar o tipo
        String cleaned = raw.replaceAll("AT%[A-Z]+[0-9]+", "");
        // Remove anotação de tipo (:BOOL, :DINT, :REAL, :WORD...)
        int colonIndex = cleaned.lastIndexOf(':');
        if (colonIndex > 0 && !cleaned.startsWith("Program:")) {
            cleaned = cleaned.substring(0, colonIndex);
        }
        return cleaned.trim();
    }

    /**
     * Gera um alias único e seguro a partir do endereço da tag para uso como
     * chave de requisição no PlcReadRequest (sem pontos, dois-pontos ou espaços).
     *
     * Garante unicidade mesmo que duas tags terminem com o mesmo segmento.
     */
    private String buildAlias(String cleanAddress, int index) {
        return "tag_" + index + "_" + cleanAddress.replaceAll("[^a-zA-Z0-9]", "_");
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 1 — Teste básico de conectividade
    // GET /api/plc/ping
    // ─────────────────────────────────────────────────────────────

    /**
     * Verifica se o socket TCP é estabelecido com o PLC e reporta as capacidades
     * anunciadas pelo driver (leitura, escrita, browse).
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        String url = buildConnectionUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectionUrl", url);

        try (PlcConnection conn = plcDriverManager.getConnectionManager().getConnection(url)) {

            boolean connected    = conn.isConnected();
            boolean readSupport  = conn.getMetadata().isReadSupported();
            boolean writeSupport = conn.getMetadata().isWriteSupported();
            boolean browseSupport = conn.getMetadata().isBrowseSupported();

            body.put("connected",       connected);
            body.put("readSupported",   readSupport);
            body.put("writeSupported",  writeSupport);
            body.put("browseSupported", browseSupport);
            body.put("status", connected ? "OK — conexão estabelecida" : "FALHA — PLC não respondeu");

            HttpStatus status = (connected && readSupport)
                    ? HttpStatus.OK
                    : HttpStatus.SERVICE_UNAVAILABLE;

            return ResponseEntity.status(status).body(body);

        } catch (Exception e) {
            log.error("[PLC PING] Erro: {}", e.getMessage(), e);
            body.put("connected", false);
            body.put("status",    "ERRO DE CONEXÃO");
            body.put("error",     e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }



    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 3 — Leitura das tags configuradas no application.yml
    // GET /api/plc/read-test
    // ─────────────────────────────────────────────────────────────

    /**
     * Lê em batch todas as tags definidas em plc.tags.process no YAML.
     *
     * Antes da leitura, sanitiza cada endereço removendo anotações de tipo
     * e sufixos IEC 61131 incompatíveis com o ControlLogix.
     *
     * O response inclui:
     *   - valores_lidos: mapa alias → valor lido com sucesso
     *   - tags_com_erro: mapa alias → código de erro + endereço original e sanitizado
     *   - avisos:        lista de endereços que foram modificados pela sanitização
     */
    @GetMapping("/read-test")
    public ResponseEntity<Map<String, Object>> readTest() {
        String url = buildConnectionUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectionUrl", url);

        List<String> rawTags = PlcTags.getTags();
        if (rawTags == null || rawTags.isEmpty()) {
            body.put("error", "Nenhuma tag configurada em plc.tags.process no application.yml.");
            return ResponseEntity.badRequest().body(body);
        }

        try (PlcConnection conn = plcDriverManager.getConnectionManager().getConnection(url)) {

            if (!conn.isConnected() || !conn.getMetadata().isReadSupported()) {
                body.put("error", "PLC indisponível ou leitura não suportada.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }

            // Monta mapeamento: índice → {alias, endereçoOriginal, endereçoSanitizado}
            record TagEntry(String alias, String original, String sanitized) {}
            List<TagEntry> entries = new ArrayList<>();
            List<String>   warnings = new ArrayList<>();

            for (int i = 0; i < rawTags.size(); i++) {
                String original  = rawTags.get(i);
                String sanitized = sanitizeTagAddress(original);
                String alias     = buildAlias(sanitized, i);

                entries.add(new TagEntry(alias, original, sanitized));

                if (!original.equals(sanitized)) {
                    warnings.add(String.format(
                            "'%s' foi sanitizado para '%s' — remova sufixos de tipo e AT%%QW do YAML.",
                            original, sanitized
                    ));
                }
            }

            // Monta e executa a requisição batch
            PlcReadRequest.Builder builder = conn.readRequestBuilder();
            entries.forEach(e -> builder.addTagAddress(e.alias(), e.sanitized()));
            PlcReadResponse response = builder.build().execute().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Classifica resultados
            Map<String, Object> valuesOk  = new LinkedHashMap<>();
            Map<String, Object> valuesErr = new LinkedHashMap<>();

            for (TagEntry entry : entries) {
                PlcResponseCode code = response.getResponseCode(entry.alias());
                if (code == PlcResponseCode.OK) {
                    valuesOk.put(entry.sanitized(), response.getObject(entry.alias()));
                } else {
                    valuesErr.put(entry.sanitized(), Map.of(
                            "codigo",     code.name(),
                            "original",   entry.original(),
                            "sanitizado", entry.sanitized(),
                            "dica",       resolveErrorHint(code)
                    ));
                }
            }

            body.put("status",        "Leitura executada");
            body.put("valores_lidos", valuesOk);
            if (!valuesErr.isEmpty())  body.put("tags_com_erro", valuesErr);
            if (!warnings.isEmpty())   body.put("avisos_sanitizacao", warnings);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            log.error("[PLC READ] Erro crítico: {}", e.getMessage(), e);
            body.put("status", "Falha crítica");
            body.put("error",  e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 4 — Leitura de tag avulsa por query param
    // GET /api/plc/read?tag=NomeDaTag
    // ─────────────────────────────────────────────────────────────

    /**
     * Lê uma única tag informada como parâmetro.
     * Útil para validar nomes individuais descobertos via /browse.
     *
     * Exemplo: GET /api/plc/read?tag=Program:MainProgram.Temperatura_Zona1
     */
    @GetMapping("/read")
    public ResponseEntity<Map<String, Object>> readSingleTag(
            @RequestParam String tag) {

        String url       = buildConnectionUrl();
        String sanitized = sanitizeTagAddress(tag);
        String alias     = buildAlias(sanitized, 0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tagOriginal",   tag);
        body.put("tagSanitizada", sanitized);
        body.put("connectionUrl", url);

        if (!tag.equals(sanitized)) {
            body.put("aviso", String.format(
                    "Tag sanitizada de '%s' para '%s'. Corrija o endereço.", tag, sanitized));
        }

        try (PlcConnection conn = plcDriverManager.getConnectionManager().getConnection(url)) {

            PlcReadResponse response = conn.readRequestBuilder()
                    .addTagAddress(alias, sanitized)
                    .build()
                    .execute()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            PlcResponseCode code = response.getResponseCode(alias);
            body.put("responseCode", code.name());

            if (code == PlcResponseCode.OK) {
                Object value = response.getObject(alias);
                body.put("valor",  value);
                body.put("tipo",   value != null ? value.getClass().getSimpleName() : "null");
                body.put("status", "OK");
                return ResponseEntity.ok(body);
            } else {
                body.put("status", "ERRO");
                body.put("dica",   resolveErrorHint(code));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

        } catch (Exception e) {
            log.error("[PLC READ SINGLE] tag='{}' erro: {}", sanitized, e.getMessage(), e);
            body.put("status", "FALHA");
            body.put("error",  e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 5 — Escrita em tag avulsa (apenas para diagnóstico)
    // POST /api/plc/write
    // Body JSON: { "tag": "NomeDaTag", "valor": 1 }
    // ─────────────────────────────────────────────────────────────

    /**
     * Escreve um valor em uma tag do PLC.
     *
     * USE COM CUIDADO: este endpoint é para diagnóstico.
     * Em produção, a escrita deve passar pelo PlcTagService com
     * validação de whitelist, autenticação e auditoria completa.
     */
    @PostMapping("/write")
    public ResponseEntity<Map<String, Object>> writeSingleTag(
            @RequestBody Map<String, Object> payload) {

        String tag   = (String) payload.get("tag");
        Object valor = payload.get("valor");

        if (tag == null || tag.isBlank() || valor == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Campos obrigatórios: 'tag' (string) e 'valor' (qualquer tipo)."
            ));
        }

        String url       = buildConnectionUrl();
        String sanitized = sanitizeTagAddress(tag);
        String alias     = buildAlias(sanitized, 0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tagOriginal",   tag);
        body.put("tagSanitizada", sanitized);
        body.put("valorEnviado",  valor);

        try (PlcConnection conn = plcDriverManager.getConnectionManager().getConnection(url)) {

            if (!conn.getMetadata().isWriteSupported()) {
                body.put("error", "Escrita não suportada pelo driver/firmware.");
                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
            }

            PlcWriteResponse response = conn.writeRequestBuilder()
                    .addTagAddress(alias, sanitized, valor)
                    .build()
                    .execute()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            PlcResponseCode code = response.getResponseCode(alias);
            body.put("responseCode", code.name());

            if (code == PlcResponseCode.OK) {
                body.put("status", "Escrita realizada com sucesso.");
                log.info("[PLC WRITE] tag='{}' valor='{}' — OK", sanitized, valor);
                return ResponseEntity.ok(body);
            } else {
                body.put("status", "Escrita falhou.");
                body.put("dica",   resolveErrorHint(code));
                log.warn("[PLC WRITE] tag='{}' code={}", sanitized, code);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

        } catch (Exception e) {
            log.error("[PLC WRITE] tag='{}' erro: {}", sanitized, e.getMessage(), e);
            body.put("status", "FALHA CRÍTICA");
            body.put("error",  e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 6 — Diagnóstico completo: conectividade + leitura
    // GET /api/plc/diagnostics
    // ─────────────────────────────────────────────────────────────

    /**
     * Executa ping + leitura em uma única chamada e retorna um sumário
     * consolidado do estado da integração com o PLC.
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> diagnostics() {
        String url = buildConnectionUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectionUrl", url);
        body.put("plcAddress",    plcProperties.address());

        try (PlcConnection conn = plcDriverManager.getConnectionManager().getConnection(url)) {

            // Conectividade
            body.put("connected",       conn.isConnected());
            body.put("readSupported",   conn.getMetadata().isReadSupported());
            body.put("writeSupported",  conn.getMetadata().isWriteSupported());
            body.put("browseSupported", conn.getMetadata().isBrowseSupported());

            // Leitura rápida das tags de processo
            List<String> rawTags = PlcTags.getTags();
            if (rawTags != null && !rawTags.isEmpty() && conn.getMetadata().isReadSupported()) {

                PlcReadRequest.Builder builder = conn.readRequestBuilder();
                Map<String, String> aliasIndex = new LinkedHashMap<>();

                for (int i = 0; i < rawTags.size(); i++) {
                    String clean = sanitizeTagAddress(rawTags.get(i));
                    String alias = buildAlias(clean, i);
                    builder.addTagAddress(alias, clean);
                    aliasIndex.put(alias, clean);
                }

                PlcReadResponse resp = builder.build().execute()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                long ok  = aliasIndex.keySet().stream()
                        .filter(a -> resp.getResponseCode(a) == PlcResponseCode.OK).count();
                long err = aliasIndex.size() - ok;

                body.put("tagsConfiguradas", aliasIndex.size());
                body.put("tagsLidasComSucesso", ok);
                body.put("tagsComErro", err);
                body.put("saude", err == 0 ? "SAUDAVEL" : (ok > 0 ? "PARCIAL" : "DEGRADADO"));
            }

        } catch (Exception e) {
            log.error("[PLC DIAGNOSTICS] {}", e.getMessage(), e);
            body.put("connected", false);
            body.put("saude",     "FALHA");
            body.put("error",     e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        return ResponseEntity.ok(body);
    }

    // ─────────────────────────────────────────────────────────────
    // Utilitário: dica legível por ResponseCode do PLC4X
    // ─────────────────────────────────────────────────────────────

    private String resolveErrorHint(PlcResponseCode code) {
        return switch (code) {
            case NOT_FOUND ->
                    "Tag não encontrada no PLC. Verifique o nome exato no Studio 5000 (Controller Tags).";
            case ACCESS_DENIED ->
                    "Acesso negado. Verifique as permissões de leitura/escrita no controlador.";
            case INVALID_ADDRESS ->
                    "Endereço inválido. Remova :BOOL, :DINT, AT%QW e similares do endereço.";
            case INVALID_DATA ->
                    "Tipo de dado incompatível. Verifique o tipo da tag no Studio 5000.";
            case INTERNAL_ERROR ->
                    "Erro interno no PLC. Prováveis causas: (1) sintaxe incorreta do endereço — " +
                            "remova sufixos de tipo e AT%QW; (2) nome da tag não existe no controlador; " +
                            "(3) tag dentro de Program Block — use o prefixo 'Program:NomePrograma.NomeDaTag'.";
            case RESPONSE_PENDING ->
                    "Resposta pendente — timeout possível. Tente novamente.";
            default ->
                    "Código: " + code.name() + ". Consulte a documentação do Apache PLC4X.";
        };
    }
}