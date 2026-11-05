package br.edu.tcc.bola;

import br.edu.tcc.bola.llm.*;
import br.edu.tcc.bola.metrics.GroundTruth;
import br.edu.tcc.bola.metrics.MetricsCalculator;
import br.edu.tcc.bola.openapi.OpenApiParser;
import br.edu.tcc.bola.setup.VampiSetup;
import br.edu.tcc.bola.verifier.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Detector completo (LLM + Verificação Diferencial) aplicado ao VAmPI.
 *
 * Valida a generalização da arquitetura proposta em um segundo benchmark,
 * com estrutura de API, autenticação e identificadores diferentes do crAPI.
 */
public class MainVampi {

    public static void main(String[] args) throws Exception {
        String specUrl = args.length > 0 ? args[0] : "vampi-openapi.json";
        String baseUrl = args.length > 1 ? args[1] : "http://localhost:5001";
        String csvPath = args.length > 2 ? args[2] : "results_vampi.csv";
        String apiKey  = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Defina a variável de ambiente OPENAI_API_KEY");
            System.exit(1);
        }

        long start = System.currentTimeMillis();

        // 1. Popular o banco e criar dois usuários + recursos
        System.out.println("=== [VAmPI] Inicializando banco ===");
        VampiSetup.initDatabase(baseUrl);

        System.out.println("\n=== [VAmPI] Criando usuários de teste ===");
        var userA = VampiSetup.createUser(baseUrl, "A");
        var userB = VampiSetup.createUser(baseUrl, "B");

        String bookA = VampiSetup.createBook(baseUrl, userA, "A");
        String bookB = VampiSetup.createBook(baseUrl, userB, "B");

        System.out.println("UserA: " + userA.userId() + " | book=" + bookA);
        System.out.println("UserB: " + userB.userId() + " | book=" + bookB);

        // 2. Parse OpenAPI
        var parser = new OpenApiParser();
        var candidates = parser.extractEndpoints(specUrl);
        System.out.println("\n=== [VAmPI] Endpoints candidatos: " + candidates.size() + " ===");

        // 3. Inferência LLM
        String spec;
        if (specUrl.startsWith("http://") || specUrl.startsWith("https://")) {
            spec = java.net.http.HttpClient.newHttpClient()
                .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(specUrl)).build(),
                      java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        } else {
            spec = Files.readString(Path.of(specUrl));
        }

        var llm = new LlmAnalyzer(apiKey);
        var llmResult = llm.analyze(spec, candidates.stream()
            .map(e -> e.method() + " " + e.path()).toList());

        System.out.println("Endpoints sensíveis identificados: " + llmResult.endpoints().size());

        // 4. Verificação diferencial
        var sessionA = new AuthSession(baseUrl, userA.token());
        var sessionB = new AuthSession(baseUrl, userB.token());
        var verifier = new DifferentialVerifier(sessionA, sessionB);

        var resourcesA = VampiSetup.fetchResourceIds(userA, bookA);
        var resourcesB = VampiSetup.fetchResourceIds(userB, bookB);

        var results = verifier.verify(llmResult.endpoints(), resourcesA, resourcesB);

        // 5. Relatório (vereditos brutos)
        System.out.println("\n=== [VAmPI] Resultados ===");
        for (var r : results) {
            System.out.printf("[%s] %s %s → %s (%s)%n",
                r.verdict(), r.endpoint().method(), r.endpoint().path(),
                r.verdict(), r.evidence());
        }

        // 6. Métricas contra o gabarito (VAmPI) e exportação do CSV
        double elapsed = (System.currentTimeMillis() - start) / 1000.0;
        var metrics = MetricsCalculator.compute(results, GroundTruth.VAMPI_VULNERABLE);
        MetricsCalculator.printSummary("VAmPI", results, metrics);
        MetricsCalculator.writeCsv(csvPath, "Proposta VAmPI (LLM + Diff)", metrics, elapsed, 0);
    }
}
