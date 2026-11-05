package br.edu.tcc.bola;

import br.edu.tcc.bola.llm.*;
import br.edu.tcc.bola.metrics.GroundTruth;
import br.edu.tcc.bola.metrics.MetricsCalculator;
import br.edu.tcc.bola.openapi.OpenApiParser;
import br.edu.tcc.bola.setup.DroneSetup;
import br.edu.tcc.bola.verifier.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Detector completo (LLM + Verificação Diferencial) aplicado à Drone Rescue API,
 * um alvo SINTÉTICO E INÉDITO criado para este TCC.
 *
 * Objetivo: eliminar o risco de contaminação de benchmark. Como a aplicação
 * nunca foi publicada antes dos experimentos, a LLM não pode tê-la memorizado —
 * se acertar quais endpoints são sensíveis a BOLA, é por raciocínio estrutural,
 * não por reconhecimento.
 */
public class MainDrone {

    public static void main(String[] args) throws Exception {
        String specUrl = args.length > 0 ? args[0] : "drone-openapi.json";
        String baseUrl = args.length > 1 ? args[1] : "http://localhost:7070";
        String csvPath = args.length > 2 ? args[2] : "results_drone.csv";
        String apiKey  = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Defina a variável de ambiente OPENAI_API_KEY");
            System.exit(1);
        }

        long start = System.currentTimeMillis();

        // 1. Criar dois pilotos + missões
        System.out.println("=== [Drone] Criando pilotos de teste ===");
        var pilotA = DroneSetup.createPilot(baseUrl, "A");
        var pilotB = DroneSetup.createPilot(baseUrl, "B");

        String missionA = DroneSetup.createMission(baseUrl, pilotA, "A");
        String missionB = DroneSetup.createMission(baseUrl, pilotB, "B");

        System.out.println("PilotA: " + pilotA.userId() + " | mission=" + missionA);
        System.out.println("PilotB: " + pilotB.userId() + " | mission=" + missionB);

        // 2. Parse OpenAPI
        var parser = new OpenApiParser();
        var candidates = parser.extractEndpoints(specUrl);
        System.out.println("\n=== [Drone] Endpoints candidatos: " + candidates.size() + " ===");

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
        var sessionA = new AuthSession(baseUrl, pilotA.token());
        var sessionB = new AuthSession(baseUrl, pilotB.token());
        var verifier = new DifferentialVerifier(sessionA, sessionB);

        var resourcesA = DroneSetup.fetchResourceIds(pilotA, missionA);
        var resourcesB = DroneSetup.fetchResourceIds(pilotB, missionB);

        var results = verifier.verify(llmResult.endpoints(), resourcesA, resourcesB);

        // 5. Relatório
        System.out.println("\n=== [Drone] Resultados ===");
        for (var r : results) {
            System.out.printf("[%s] %s %s → %s (%s)%n",
                r.verdict(), r.endpoint().method(), r.endpoint().path(),
                r.verdict(), r.evidence());
        }

        // 6. Métricas vs gabarito e CSV
        double elapsed = (System.currentTimeMillis() - start) / 1000.0;
        var metrics = MetricsCalculator.compute(results, GroundTruth.DRONE_VULNERABLE);
        MetricsCalculator.printSummary("Drone", results, metrics);
        MetricsCalculator.writeCsv(csvPath, "Proposta Drone (LLM + Diff)", metrics, elapsed, 0);
    }
}
