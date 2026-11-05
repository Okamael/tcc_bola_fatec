package br.edu.tcc.bola.setup;

import br.edu.tcc.bola.setup.CrApiSetup.UserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Setup para a API sintética "Observatório de Drones de Resgate" (alvo inédito).
 *
 * Fluxo:
 *   POST /api/v1/auth/register  → {callsign, password, squadron}
 *   POST /api/v1/auth/login     → {callsign, password} → retorna token + pilotId
 *   POST /api/v1/missions       → cria missão (recurso de posse) → retorna missionId
 *
 * Identificadores são numéricos (missionId, pilotId).
 */
public class DroneSetup {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Registra e autentica um piloto; retorna sessão com token e pilotId. */
    public static UserSession createPilot(String baseUrl, String label) throws Exception {
        String callsign = "pil" + label.toLowerCase() + UUID.randomUUID().toString().substring(0, 6);
        String password = "p@ss";

        postJson(baseUrl + "/api/v1/auth/register",
            Map.of("callsign", callsign, "password", password, "squadron", "sq-" + label), null);

        JsonNode login = postJson(baseUrl + "/api/v1/auth/login",
            Map.of("callsign", callsign, "password", password), null);
        String token   = login.path("token").asText("");
        String pilotId = login.path("pilotId").asText("");
        if (token.isEmpty()) throw new IllegalStateException("Login Drone falhou [" + label + "]: " + login);
        System.out.println("login    [" + label + "]: pilotId=" + pilotId);

        return new UserSession(callsign, token, pilotId);
    }

    /** Cria uma missão (recurso de posse) e retorna o missionId. */
    public static String createMission(String baseUrl, UserSession pilot, String label) throws Exception {
        JsonNode m = postJson(baseUrl + "/api/v1/missions",
            Map.of("codename", "op-" + label,
                   "coordinates", "11.1,22.2",
                   "briefing", "briefing confidencial de " + label),
            pilot.token());
        String missionId = m.path("missionId").asText("");
        System.out.println("mission  [" + label + "]: missionId=" + missionId);
        return missionId;
    }

    /** IDs para a verificação diferencial. Params do spec: missionId, pilotId. */
    public static Map<String, String> fetchResourceIds(UserSession pilot, String missionId) {
        Map<String, String> ids = new HashMap<>();
        ids.put("missionId", missionId);
        ids.put("pilotId",   pilot.userId());
        return ids;
    }

    private static JsonNode postJson(String url, Map<String, Object> body, String token) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String b = resp.body();
        if (b == null || b.isBlank()) return MAPPER.createObjectNode();
        String t = b.trim();
        if (!t.startsWith("{") && !t.startsWith("[")) return MAPPER.createObjectNode();
        return MAPPER.readTree(t);
    }
}
