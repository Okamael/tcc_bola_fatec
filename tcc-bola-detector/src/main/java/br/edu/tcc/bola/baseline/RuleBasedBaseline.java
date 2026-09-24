package br.edu.tcc.bola.baseline;

import br.edu.tcc.bola.openapi.OpenApiParser;
import java.util.List;

/**
 * Baseline de detecção de BOLA baseado em regras.
 * Estratégia: qualquer endpoint com path parameter é considerado potencialmente
 * sensível a BOLA. Comportamento típico de ferramentas SAST tradicionais.
 */
public class RuleBasedBaseline {

    public record BaselineFinding(String path, String method, String reason) {}

    public List<BaselineFinding> detect(String specUrl) {
        var parser = new OpenApiParser();
        var endpoints = parser.extractEndpoints(specUrl);

        return endpoints.stream()
            .map(ep -> new BaselineFinding(
                ep.path(),
                ep.method(),
                "Endpoint contém path parameter(s): " + ep.pathParams()
            ))
            .toList();
    }

    public static void main(String[] args) throws Exception {
        var baseline = new RuleBasedBaseline();
        var findings = baseline.detect(args[0]);

        System.out.println("=== Baseline (Baseado em Regras) — Achados ===");
        for (var f : findings) {
            System.out.printf("[%s] %s — %s%n", f.method(), f.path(), f.reason());
        }
        System.out.println("Total: " + findings.size());
    }
}
