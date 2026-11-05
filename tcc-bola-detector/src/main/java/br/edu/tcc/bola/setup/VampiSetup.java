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
 * Setup automatizado para o VAmPI (erev0s/vampi).
 *
 * Fluxo do VAmPI:
 *   - GET  /createdb                → popula o banco com usuários/livros dummy
 *   - POST /users/v1/register       → {username, password, email}
 *   - POST /users/v1/login          → {username, password} → retorna auth_token (JWT)
 *   - POST /books/v1                 → {book_title, secret}  (requer Bearer token)
 *   - GET  /books/v1/{book_title}    → retorna o secret (só o dono deveria ver) ← BOLA
 *
 * Diferente do crAPI, os identificadores de objeto são strings legíveis
 * (book_title, username), não UUIDs.
 */
public class VampiSetup {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Recurso criado por um usuário: o título do livro e seu secret. */
    public record VampiUserResources(UserSession user, String bookTitle) {}

    /** Popula o banco de dados do VAmPI com dados dummy. */
    public static void initDatabase(String baseUrl) throws Exception {
        String resp = get(baseUrl + "/createdb", null);
        System.out.println("createdb: " + resp);
    }

    /** Registra e autentica um usuário, retornando a sessão com o token. */
    public static UserSession createUser(String baseUrl, String label) throws Exception {
        String shortId  = UUID.randomUUID().toString().substring(0, 8);
        String username = "tcc" + label.toLowerCase() + shortId;
        String email    = username + "@t.co";
        String password = "Pass@12345";

        String signup = postJsonRaw(baseUrl + "/users/v1/register",
            Map.of("username", username, "password", password, "email", email), null);
        System.out.println("register [" + label + "]: " + signup);

        JsonNode loginResp = postJson(baseUrl + "/users/v1/login",
            Map.of("username", username, "password", password), null);
        String token = loginResp.path("auth_token").asText("");
        if (token.isEmpty() || token.equals("null")) {
            throw new IllegalStateException("Login VAmPI falhou para [" + label + "]: " + loginResp);
        }
        System.out.println("login    [" + label + "]: token obtido");

        // No VAmPI o identificador do usuário é o próprio username
        return new UserSession(email, token, username);
    }

    /** Cria um livro (recurso BOLA) para o usuário. Retorna o book_title. */
    public static String createBook(String baseUrl, UserSession user, String label) throws Exception {
        String bookTitle = "tccbook" + label + UUID.randomUUID().toString().substring(0, 6);
        String secret    = "secret-de-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);

        String resp = postJsonRaw(baseUrl + "/books/v1",
            Map.of("book_title", bookTitle, "secret", secret), user.token());
        System.out.println("add_book [" + label + "]: " + resp + " (title=" + bookTitle + ")");
        return bookTitle;
    }

    /**
     * Coleta os IDs de recursos para a verificação diferencial.
     * Parâmetros possíveis no VAmPI: book_title, username.
     */
    public static Map<String, String> fetchResourceIds(UserSession user, String bookTitle) {
        Map<String, String> ids = new HashMap<>();
        ids.put("book_title", bookTitle);
        ids.put("username",   user.userId());  // userId = username no VAmPI
        return ids;
    }

    // ---- helpers HTTP ----

    private static JsonNode postJson(String url, Map<String, Object> body, String token)
            throws Exception {
        String raw = postJsonRaw(url, body, token);
        if (raw == null || raw.isBlank()) return MAPPER.createObjectNode();
        String t = raw.trim();
        if (!t.startsWith("{") && !t.startsWith("[")) return MAPPER.createObjectNode();
        return MAPPER.readTree(t);
    }

    private static String postJsonRaw(String url, Map<String, Object> body, String token)
            throws Exception {
        String json = MAPPER.writeValueAsString(body);
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static String get(String url, String token) throws Exception {
        var builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
