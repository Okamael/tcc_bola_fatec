package br.edu.tcc.bola;

import br.edu.tcc.bola.llm.*;
import br.edu.tcc.bola.openapi.OpenApiParser;
import br.edu.tcc.bola.setup.VampiSetup;
import br.edu.tcc.bola.verifier.*;
import java.io.PrintWriter;
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

        // 5. Relatório
        int vuln = 0, denied = 0, inconclusive = 0;
        System.out.println("\n=== [VAmPI] Resultados ===");
        for (var r : results) {
            System.out.printf("[%s] %s %s → %s (%s)%n",
                r.verdict(), r.endpoint().method(), r.endpoint().path(),
                r.verdict(), r.evidence());
            switch (r.verdict()) {
                case VULNERABLE   -> vuln++;
                case DENIED       -> denied++;
                case INCONCLUSIVE -> inconclusive++;
            }
        }

        System.out.println("\n=== [VAmPI] Resumo ===");
        System.out.println("Total testado:  " + results.size());
        System.out.println("VULNERABLE:     " + vuln);
        System.out.println("DENIED:         " + denied);
        System.out.println("INCONCLUSIVE:   " + inconclusive);

        // 6. Exportar CSV (mesmo formato do results.csv)
        int tp = vuln, fp = 0, fn = 0, tn = denied;
        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;
        int total = tp + fp + fn + tn;
        double accuracy = total > 0 ? (double) (tp + tn) / total : 0;
        double elapsed = (System.currentTimeMillis() - start) / 1000.0;

        String csvLine = String.format(Locale.US,
            "Proposta VAmPI (LLM + Diff),%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f,0",
            tp, fp, fn, tn, inconclusive, precision, recall, f1, accuracy, elapsed);

        Path out = Path.of(csvPath);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println("tool,tp,fp,fn,tn,inconclusive,precision,recall,f1,accuracy,time_s,tokens");
            pw.println(csvLine);
        }

        System.out.println("\nCSV salvo em: " + out.toAbsolutePath());
        System.out.println(csvLine);
    }
}
