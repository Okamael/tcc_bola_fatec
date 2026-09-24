package br.edu.tcc.bola.verifier;

import br.edu.tcc.bola.llm.LlmResponse.SensitiveEndpoint;
import java.net.http.HttpResponse;
import java.util.*;

public class DifferentialVerifier {

    public enum Verdict { VULNERABLE, DENIED, INCONCLUSIVE }

    public record Result(SensitiveEndpoint endpoint, Verdict verdict,
                         int statusA, int statusB, String evidence) {}

    private final AuthSession userA;
    private final AuthSession userB;

    public DifferentialVerifier(AuthSession userA, AuthSession userB) {
        this.userA = userA;
        this.userB = userB;
    }

    public List<Result> verify(List<SensitiveEndpoint> endpoints,
                               Map<String, String> objectIdOfA,
                               Map<String, String> objectIdOfB) {
        List<Result> results = new ArrayList<>();

        for (SensitiveEndpoint ep : endpoints) {
            try {
                String pathOwn = fillPath(ep, objectIdOfA);
                String pathCross = fillPath(ep, objectIdOfB);

                HttpResponse<String> ownResp = userA.get(pathOwn);
                HttpResponse<String> crossResp = userA.get(pathCross);

                Verdict verdict = classify(ownResp, crossResp);
                results.add(new Result(ep, verdict,
                    ownResp.statusCode(), crossResp.statusCode(),
                    buildEvidence(ownResp, crossResp)));
            } catch (Exception e) {
                results.add(new Result(ep, Verdict.INCONCLUSIVE, -1, -1,
                    "Erro: " + e.getMessage()));
            }
        }
        return results;
    }

    private Verdict classify(HttpResponse<String> own, HttpResponse<String> cross) {
        int s = cross.statusCode();

        if (s == 401 || s == 403 || s == 404) return Verdict.DENIED;

        if (s >= 200 && s < 300) {
            if (similarity(own.body(), cross.body()) < 0.3) {
                return Verdict.INCONCLUSIVE;
            }
            return Verdict.VULNERABLE;
        }

        return Verdict.INCONCLUSIVE;
    }

    private double similarity(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.isEmpty() && b.isEmpty()) return 1;
        Set<String> sa = new HashSet<>(Arrays.asList(a.split("\\W+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.split("\\W+")));
        Set<String> inter = new HashSet<>(sa); inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa); union.addAll(sb);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    private String fillPath(SensitiveEndpoint ep, Map<String, String> ids) {
        String path = ep.path();
        for (var entry : ids.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return path;
    }

    private String buildEvidence(HttpResponse<String> own, HttpResponse<String> cross) {
        return String.format("own=%d, cross=%d, bodySimilarity=%.2f",
            own.statusCode(), cross.statusCode(),
            similarity(own.body(), cross.body()));
    }
}
