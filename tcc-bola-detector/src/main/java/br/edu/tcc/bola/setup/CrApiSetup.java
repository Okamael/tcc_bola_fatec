package br.edu.tcc.bola.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrApiSetup {

    private static final String BASE = "http://localhost:8888";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public record UserSession(String email, String token, String userId) {}

    public static UserSession createUser(String label) throws Exception {
        // Email curto: evita JWT longo que estoura coluna varchar(500) do crAPI
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        String email = "tcc" + label.toLowerCase() + shortId + "@t.co";
        String password = "Test@12345";
        // número aleatório de 10 dígitos para evitar colisão
        String number = String.valueOf(1_000_000_000L + (long)(Math.random() * 9_000_000_000L));

        String signupResp = postRaw("/identity/api/auth/signup", Map.of(
            "name", "TCC User " + label,
            "email", email,
            "number", number,
            "password", password
        ), null);
        System.out.println("signup [" + label + "]: " + signupResp);

        JsonNode loginResp = postJson("/identity/api/auth/login", Map.of(
            "email", email,
            "password", password
        ), null);

        String token = loginResp.path("token").asText();
        if (token == null || token.isEmpty() || token.equals("null")) {
            throw new IllegalStateException("Login falhou para [" + label + "]: " + loginResp);
        }
        System.out.println("login  [" + label + "]: token obtido com sucesso");

        String userId = extractSubFromJwt(token);
        return new UserSession(email, token, userId);
    }

    public static String createVehicle(UserSession user) throws Exception {
        // O crAPI usa VINs pré-cadastrados — busca um VIN livre com seu pincode real
        String[] vinAndPin = pickFreeVinWithPincode();
        String vin     = vinAndPin[0];
        String pincode = vinAndPin[1];
        System.out.println("add_vehicle VIN=" + vin + " pincode=" + pincode);

        String addResp = postRaw("/identity/api/v2/vehicle/add_vehicle",
            Map.of(
                "vin",     vin,
                "year",    2020,
                "make",    "Toyota",
                "model",   "Corolla",
                "type",    "car",
                "pincode", pincode
            ),
            user.token());
        System.out.println("add_vehicle response: " + addResp);

        // Polling até 15s aguardando processamento assíncrono
        for (int i = 0; i < 15; i++) {
            Thread.sleep(1000);
            JsonNode vehicles = getJson("/identity/api/v2/vehicle/vehicles", user.token());
            if (vehicles.isArray() && vehicles.size() > 0) {
                String uuid = vehicles.get(0).path("uuid").asText();
                System.out.println("vehicle uuid: " + uuid);
                return uuid;
            }
        }
        throw new IllegalStateException("Veículo não disponível após 15s para: " + user.email());
    }

    /**
     * Coleta todos os IDs de recursos do usuário necessários para a verificação diferencial.
     * Cobre os parâmetros: vehicleId, video_id, postId, order_id, id, userId.
     */
    public static Map<String, String> fetchResourceIds(UserSession user, String vehicleUuid,
                                                        String baseUrl) throws Exception {
        Map<String, String> ids = new HashMap<>();

        // IDs fixos do usuário
        ids.put("id",        vehicleUuid);   // usado em /vehicle/{vehicleId}/location
        ids.put("vehicleId", vehicleUuid);
        ids.put("userId",    user.userId());

        // video_id — buscar no dashboard
        JsonNode dashboard = getJson("/identity/api/v2/user/dashboard", user.token());
        String videoId = dashboard.path("video_id").asText("0");
        ids.put("video_id", videoId.equals("0") ? "1" : videoId);

        // postId — criar um post no community
        JsonNode postResp = postJson("/community/api/v2/community/posts",
            Map.of("title",   "TCC Test Post",
                   "content", "test content for BOLA detection"),
            user.token());
        String postId = postResp.path("id").asText("");
        if (postId.isEmpty()) {
            // fallback: pegar post existente
            JsonNode recent = getJson("/community/api/v2/community/posts/recent", user.token());
            if (recent.path("posts").isArray() && recent.path("posts").size() > 0) {
                postId = recent.path("posts").get(0).path("id").asText("1");
            } else {
                postId = "1";
            }
        }
        ids.put("postId", postId);

        // order_id — criar uma order na shop
        JsonNode products = getJson("/workshop/api/shop/products", user.token());
        if (products.isArray() && products.size() > 0) {
            String productUuid = products.get(0).path("_id").asText("");
            if (!productUuid.isEmpty()) {
                JsonNode orderResp = postJson("/workshop/api/shop/orders",
                    Map.of("product_id", productUuid, "quantity", 1),
                    user.token());
                String orderId = orderResp.path("id").asText("");
                if (!orderId.isEmpty()) ids.put("order_id", orderId);
            }
        }
        if (!ids.containsKey("order_id")) ids.put("order_id", "1");

        System.out.println("resourceIds[" + user.email() + "]: " + ids);
        return ids;
    }
    private static String[] pickFreeVinWithPincode() throws Exception {
        int offset = (int)(Math.random() * 5);
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "exec", "postgresdb",
            "psql", "-U", "admin", "-d", "crapi", "-t", "-A", "-F", "|",
            "-c", "SELECT vin, pincode FROM vehicle_details WHERE owner_id IS NULL LIMIT 1 OFFSET " + offset + ";"
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        if (out.isEmpty() || !out.contains("|"))
            throw new IllegalStateException("Nenhum VIN livre no banco. out=" + out);
        String[] parts = out.split("\\|");
        return new String[]{ parts[0].trim(), parts[1].trim() };
    }

    private static JsonNode postJson(String path, Map<String, Object> body, String token)
            throws Exception {
        String raw = postRaw(path, body, token);
        System.out.println("postJson [" + path + "] raw='" + raw + "'");
        if (raw == null || raw.isBlank()) return MAPPER.createObjectNode();
        String trimmed = raw.trim();
        // Alguns endpoints do crAPI retornam texto puro (ex: "CRAPIResponse(...)")
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(trimmed);
    }

    private static String postRaw(String path, Map<String, Object> body, String token)
            throws Exception {
        String json = MAPPER.writeValueAsString(body);
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) builder.header("Authorization", "Bearer " + token);

        HttpResponse<String> resp = HTTP.send(builder.build(),
            HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static JsonNode getJson(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        if (body == null || body.isBlank()) return MAPPER.createObjectNode();
        if (!body.trim().startsWith("{") && !body.trim().startsWith("[")) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(body);
    }

    private static String extractSubFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = MAPPER.readTree(payload);
            return node.path("sub").asText();
        } catch (Exception e) {
            return "";
        }
    }


}
