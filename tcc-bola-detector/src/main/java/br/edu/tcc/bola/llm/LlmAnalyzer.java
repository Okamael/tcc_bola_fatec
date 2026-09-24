package br.edu.tcc.bola.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.List;

public class LlmAnalyzer {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmAnalyzer(String apiKey) {
        this.apiKey = apiKey;
    }

    public LlmResponse analyze(String openApiSpec, List<String> candidateEndpoints) throws Exception {
        String prompt = buildPrompt(openApiSpec, candidateEndpoints);
        String body = mapper.writeValueAsString(java.util.Map.of(
            "model", "gpt-4o-mini",
            "response_format", java.util.Map.of("type", "json_object"),
            "messages", List.of(
                java.util.Map.of("role", "system", "content", SYSTEM_PROMPT),
                java.util.Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0
        ));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        var root = mapper.readTree(resp.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();
        return mapper.readValue(content, LlmResponse.class);
    }

    private static final String SYSTEM_PROMPT = """
        Você é um analista de segurança de APIs. Dada uma especificação OpenAPI e uma
        lista de endpoints com path parameters, identifique quais são sensíveis a
        Broken Object Level Authorization (BOLA).

        Para cada endpoint sensível, retorne:
        - path e method
        - objectIdParam: nome do parâmetro que identifica o objeto
        - ownershipHints: campos de resposta que indicam o dono do recurso
        - rationale: justificativa curta

        Responda SOMENTE em JSON no formato:
        {"endpoints": [{"path": "...", "method": "...", "objectIdParam": "...",
          "ownershipHints": ["..."], "rationale": "..."}]}
        """;

    private String buildPrompt(String spec, List<String> candidates) throws Exception {
        return "Especificação OpenAPI:\n" + spec
            + "\n\nEndpoints candidatos:\n" + mapper.writeValueAsString(candidates);
    }
}
