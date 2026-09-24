package br.edu.tcc.bola.llm;

import java.util.List;

public record LlmResponse(List<SensitiveEndpoint> endpoints) {

    public record SensitiveEndpoint(
        String path,
        String method,
        String objectIdParam,
        List<String> ownershipHints,
        String rationale
    ) {}
}
