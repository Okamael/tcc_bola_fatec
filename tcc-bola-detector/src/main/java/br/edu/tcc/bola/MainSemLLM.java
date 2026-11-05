package br.edu.tcc.bola;

import br.edu.tcc.bola.baseline.RuleBasedBaseline;
import br.edu.tcc.bola.baseline.RuleBasedBaseline.BaselineFinding;
import br.edu.tcc.bola.llm.LlmResponse.SensitiveEndpoint;
import br.edu.tcc.bola.setup.CrApiSetup;
import br.edu.tcc.bola.verifier.*;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Experimento "Sem LLM": usa a lista de endpoints do RuleBasedBaseline
 * (todos com path parameters) em vez da inferência semântica do LlmAnalyzer,
 * e passa para o mesmo motor de verificação diferencial.
 *
 * Objetivo: isolar a contribuição do LLM na seleção de endpoints.
 */
public class MainSemLLM {

    public static void main(String[] args) throws Exception {
        String specUrl  = args.length > 0 ? args[0] : "openapi.json";
        String baseUrl  = args.length > 1 ? args[1] : "http://localhost:8888";
        String csvPath  = args.length > 2 ? args[2] : "results_sem_llm.csv";

        long start = System.currentTimeMillis();

        // 1. Setup: criar dois usuários e seus recursos (igual ao Main)
        System.out.println("=== [Sem LLM] Criando usuários de teste ===");
        var userA = CrApiSetup.createUser("A");
        var userB = CrApiSetup.createUser("B");

        String vehicleA = CrApiSetup.createVehicle(userA);
        String vehicleB = CrApiSetup.createVehicle(userB);

        System.out.println("UserA: " + userA.email() + " | vehicle=" + vehicleA);
        System.out.println("UserB: " + userB.email() + " | vehicle=" + vehicleB);

        // 2. Obter endpoints via RuleBasedBaseline (sem LLM)
        var baseline = new RuleBasedBaseline();
        var findings = baseline.detect(specUrl);
        System.out.println("\n=== [Sem LLM] Endpoints candidatos (regras): " + findings.size() + " ===");

        // 3. Converter BaselineFinding → SensitiveEndpoint
        List<SensitiveEndpoint> endpoints = new ArrayList<>();
        for (BaselineFinding f : findings) {
            String objectIdParam = (!f.pathParams().isEmpty())
                ? f.pathParams().get(0)
                : "id";
            endpoints.add(new SensitiveEndpoint(
                f.path(),
                f.method(),
                objectIdParam,
                List.of(),           // ownershipHints vazios
                "Regra estática"     // rationale genérico
            ));
        }

        // 4. Verificação diferencial (igual ao Main)
        var sessionA = new AuthSession(baseUrl, userA.token());
        var sessionB = new AuthSession(baseUrl, userB.token());
        var verifier = new DifferentialVerifier(sessionA, sessionB);

        var resourcesA = CrApiSetup.fetchResourceIds(userA, vehicleA, baseUrl);
        var resourcesB = CrApiSetup.fetchResourceIds(userB, vehicleB, baseUrl);

        var results = verifier.verify(endpoints, resourcesA, resourcesB);

        // 5. Relatório no console
        int vuln = 0, denied = 0, inconclusive = 0;
        System.out.println("\n=== [Sem LLM] Resultados ===");
        for (var r : results) {
            System.out.printf("[%s] %s %s → %s (%s)%n",
                r.verdict(), r.endpoint().method(), r.endpoint().path(),
                r.verdict(), r.evidence());
            switch (r.verdict()) {
                case VULNERABLE  -> vuln++;
                case DENIED      -> denied++;
                case INCONCLUSIVE -> inconclusive++;
            }
        }

        System.out.println("\n=== [Sem LLM] Resumo ===");
        System.out.println("Total testado:    " + results.size());
        System.out.println("VULNERABLE:       " + vuln);
        System.out.println("DENIED:          " + denied);
        System.out.println("INCONCLUSIVE:    " + inconclusive);

        // 6. Exportar CSV
        // TP = VULNERABLE, FP = 0, FN = 0, TN = DENIED, INCONCLUSIVE = inconclusive
        int tp = vuln;
        int fp = 0;
        int fn = 0;
        int tn = denied;

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = (precision + recall) > 0
            ? 2 * precision * recall / (precision + recall) : 0;
        int total = tp + fp + fn + tn;
        double accuracy = total > 0 ? (double) (tp + tn) / total : 0;

        double elapsed = (System.currentTimeMillis() - start) / 1000.0;

        String csvLine = String.format(Locale.US,
            "Sem LLM,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f,0",
            tp, fp, fn, tn, inconclusive,
            precision, recall, f1, accuracy, elapsed
        );

        Path out = Path.of(csvPath);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println("tool,tp,fp,fn,tn,inconclusive,precision,recall,f1,accuracy,time_s,tokens");
            pw.println(csvLine);
        }

        System.out.println("\nCSV salvo em: " + out.toAbsolutePath());
        System.out.println(csvLine);
    }
}
