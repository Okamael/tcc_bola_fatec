package br.edu.tcc.bola;

import br.edu.tcc.bola.llm.*;
import br.edu.tcc.bola.openapi.OpenApiParser;
import br.edu.tcc.bola.setup.CrApiSetup;
import br.edu.tcc.bola.verifier.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        String specUrl = args.length > 0 ? args[0] : "openapi.json";
        String baseUrl = args.length > 1 ? args[1] : "http://localhost:8888";
        String apiKey  = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Defina a variável de ambiente OPENAI_API_KEY");
            System.exit(1);
        }

        // 1. Setup: criar dois usuários e seus recursos
        System.out.println("=== Criando usuários de teste ===");
        var userA = CrApiSetup.createUser("A");
        var userB = CrApiSetup.createUser("B");

        String vehicleA = CrApiSetup.createVehicle(userA);
        String vehicleB = CrApiSetup.createVehicle(userB);

        System.out.println("UserA: " + userA.email() + " | vehicle=" + vehicleA);
        System.out.println("UserB: " + userB.email() + " | vehicle=" + vehicleB);

        // 2. Parse OpenAPI
        var parser = new OpenApiParser();
        var candidates = parser.extractEndpoints(specUrl);
        System.out.println("\n=== Endpoints candidatos: " + candidates.size() + " ===");

        // 3. Inferência LLM — lê o spec do disco ou via HTTP
        String spec;
        if (specUrl.startsWith("http://") || specUrl.startsWith("https://")) {
            spec = java.net.http.HttpClient.newHttpClient()
                .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(specUrl)).build(),
                      java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        } else {
            spec = java.nio.file.Files.readString(java.nio.file.Path.of(specUrl));
        }

        var llm = new LlmAnalyzer(apiKey);
        var llmResult = llm.analyze(spec, candidates.stream()
            .map(e -> e.method() + " " + e.path()).toList());

        System.out.println("Endpoints sensíveis identificados: " + llmResult.endpoints().size());

        // 4. Verificação diferencial
        var sessionA = new AuthSession(baseUrl, userA.token());
        var sessionB = new AuthSession(baseUrl, userB.token());
        var verifier = new DifferentialVerifier(sessionA, sessionB);

        // Buscar IDs reais dos recursos criados para cada usuário
        var resourcesA = CrApiSetup.fetchResourceIds(userA, vehicleA, baseUrl);
        var resourcesB = CrApiSetup.fetchResourceIds(userB, vehicleB, baseUrl);

        var results = verifier.verify(llmResult.endpoints(), resourcesA, resourcesB);

        // 5. Relatório
        System.out.println("\n=== Resultados ===");
        for (var r : results) {
            System.out.printf("[%s] %s %s → %s (%s)%n",
                r.verdict(), r.endpoint().method(), r.endpoint().path(),
                r.verdict(), r.evidence());
        }
    }
}
