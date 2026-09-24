package br.edu.tcc.bola.verifier;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class AuthSession {

    private final String token;
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    public AuthSession(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .GET()
            .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public String getToken() { return token; }
}
