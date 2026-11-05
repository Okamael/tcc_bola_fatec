package br.edu.tcc.bola.metrics;

import br.edu.tcc.bola.verifier.DifferentialVerifier.Result;
import br.edu.tcc.bola.verifier.DifferentialVerifier.Verdict;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Calcula métricas de detecção (TP/FP/FN/TN, precisão, recall, F1, acurácia)
 * comparando os vereditos do DifferentialVerifier contra um gabarito.
 *
 * Regras de classificação (por endpoint candidato):
 *   - VULNERABLE  + está no gabarito  → TP (acertou a vulnerabilidade)
 *   - VULNERABLE  + NÃO no gabarito   → FP (falso alarme)
 *   - não-VULNER. + está no gabarito  → FN (deixou passar; inclui INCONCLUSIVE
 *                                           de endpoints vulneráveis, ex.: PUT
 *                                           testado via GET → 405)
 *   - não-VULNER. + NÃO no gabarito   → TN (corretamente não sinalizado)
 *
 * INCONCLUSIVE é contabilizado à parte (para transparência), mas também entra
 * na matriz de confusão como FN ou TN conforme o gabarito — já que, do ponto de
 * vista de detecção, um inconclusivo não reportou a vulnerabilidade.
 */
public final class MetricsCalculator {

    public record Metrics(int tp, int fp, int fn, int tn, int inconclusive,
                          double precision, double recall, double f1, double accuracy) {}

    private MetricsCalculator() {}

    public static Metrics compute(List<Result> results, Set<String> groundTruth) {
        int tp = 0, fp = 0, fn = 0, tn = 0, inconclusive = 0;

        for (Result r : results) {
            String method = r.endpoint().method();
            String path   = r.endpoint().path();
            boolean truthVulnerable = GroundTruth.isVulnerable(groundTruth, method, path);
            boolean detected = r.verdict() == Verdict.VULNERABLE;

            if (r.verdict() == Verdict.INCONCLUSIVE) inconclusive++;

            if (detected && truthVulnerable)      tp++;
            else if (detected && !truthVulnerable) fp++;
            else if (!detected && truthVulnerable) fn++;
            else                                   tn++;
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;
        int total = tp + fp + fn + tn;
        double accuracy = total > 0 ? (double) (tp + tn) / total : 0;

        return new Metrics(tp, fp, fn, tn, inconclusive, precision, recall, f1, accuracy);
    }

    /** Imprime o resumo no console. */
    public static void printSummary(String label, List<Result> results, Metrics m) {
        System.out.println("\n=== [" + label + "] Resumo (vs. gabarito) ===");
        System.out.println("Total testado:  " + results.size());
        System.out.printf ("TP=%d  FP=%d  FN=%d  TN=%d  (INCONCLUSIVE=%d)%n",
            m.tp(), m.fp(), m.fn(), m.tn(), m.inconclusive());
        System.out.printf (Locale.US, "Precisão=%.1f%%  Recall=%.1f%%  F1=%.1f%%  Acurácia=%.1f%%%n",
            m.precision() * 100, m.recall() * 100, m.f1() * 100, m.accuracy() * 100);
    }

    /** Escreve o CSV no formato consolidado (tool,tp,fp,fn,tn,inconclusive,...). */
    public static void writeCsv(String csvPath, String toolName, Metrics m,
                                double elapsedSeconds, int tokens) throws Exception {
        String line = String.format(Locale.US,
            "%s,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f,%d",
            toolName, m.tp(), m.fp(), m.fn(), m.tn(), m.inconclusive(),
            m.precision(), m.recall(), m.f1(), m.accuracy(), elapsedSeconds, tokens);

        Path out = Path.of(csvPath);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println("tool,tp,fp,fn,tn,inconclusive,precision,recall,f1,accuracy,time_s,tokens");
            pw.println(line);
        }
        System.out.println("\nCSV salvo em: " + out.toAbsolutePath());
        System.out.println(line);
    }
}
