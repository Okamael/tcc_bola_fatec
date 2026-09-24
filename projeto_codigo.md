# Projeto TCC — Código-fonte completo

Repositório: `tcc-bola-detector`
Linguagem: Java 17
Build: Maven

Este documento descreve a estrutura completa do projeto para ser montado no Git.

---

## Estrutura de diretórios

```
tcc-bola-detector/
├── pom.xml
├── README.md
├── .gitignore
├── docker-compose.yml
├── openapi.json
├── results.csv
├── endpoints_detail.csv
├── scripts/
│   └── generate_charts.py
└── src/
    └── main/
        └── java/
            └── br/
                └── edu/
                    └── tcc/
                        └── bola/
                            ├── Main.java
                            ├── openapi/
                            │   └── OpenApiParser.java
                            ├── llm/
                            │   ├── LlmAnalyzer.java
                            │   └── LlmResponse.java
                            ├── setup/
                            │   └── CrApiSetup.java
                            ├── verifier/
                            │   ├── AuthSession.java
                            │   └── DifferentialVerifier.java
                            └── baseline/
                                └── RuleBasedBaseline.java
```

---

## 1. `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>br.edu.tcc</groupId>
    <artifactId>bola-detector</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.swagger.parser.v3</groupId>
            <artifactId>swagger-parser</artifactId>
            <version>2.1.22</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>br.edu.tcc.bola.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 2. `src/main/java/br/edu/tcc/bola/llm/LlmResponse.java`

```java
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
```

---

## 3. `src/main/java/br/edu/tcc/bola/openapi/OpenApiParser.java`

```java
package br.edu.tcc.bola.openapi;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.List;

public class OpenApiParser {

    public record Endpoint(String path, String method, List<String> pathParams) {}

    public List<Endpoint> extractEndpoints(String specUrlOrFile) {
        OpenAPI api = new OpenAPIV3Parser().read(specUrlOrFile);
        List<Endpoint> result = new ArrayList<>();

        api.getPaths().forEach((path, item) -> {
            item.readOperationsMap().forEach((httpMethod, op) -> {
                List<String> params = extractPathParams(path);
                if (!params.isEmpty()) {
                    result.add(new Endpoint(path, httpMethod.name(), params));
                }
            });
        });
        return result;
    }

    private List<String> extractPathParams(String path) {
        List<String> params = new ArrayList<>();
        int i = 0;
        while ((i = path.indexOf('{', i)) != -1) {
            int end = path.indexOf('}', i);
            if (end > i) params.add(path.substring(i + 1, end));
            i = end + 1;
        }
        return params;
    }
}
```

---

## 4. `src/main/java/br/edu/tcc/bola/llm/LlmAnalyzer.java`

```java
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
```

---

## 5. `src/main/java/br/edu/tcc/bola/verifier/AuthSession.java`

```java
package br.edu.tcc.bola.verifier;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class AuthSession {

    private final String token;
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    public AuthSession(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .GET()
            .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public String getToken() { return token; }
}
```

---

## 6. `src/main/java/br/edu/tcc/bola/verifier/DifferentialVerifier.java`

```java
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
```

---

## 7. `src/main/java/br/edu/tcc/bola/setup/CrApiSetup.java`

```java
package br.edu.tcc.bola.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.UUID;

public class CrApiSetup {

    private static final String BASE = "http://localhost:8888";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public record UserSession(String email, String token, String userId) {}

    public static UserSession createUser(String label) throws Exception {
        String email = "tcc_" + label + "_" + UUID.randomUUID() + "@test.com";
        String password = "Test@12345";

        postJson("/identity/api/auth/signup", Map.of(
            "name", "TCC User " + label,
            "email", email,
            "number", "11999999999",
            "password", password
        ), null);

        JsonNode loginResp = postJson("/identity/api/auth/login", Map.of(
            "email", email,
            "password", password
        ), null);

        String token = loginResp.path("token").asText();
        if (token.isEmpty()) {
            throw new IllegalStateException("Login falhou: " + loginResp);
        }

        String userId = extractSubFromJwt(token);
        return new UserSession(email, token, userId);
    }

    public static String createVehicle(UserSession user) throws Exception {
        postJson("/identity/api/v2/vehicle/add_vehicle",
            Map.of(
                "vin", randomVin(),
                "year", 2020,
                "make", "Toyota",
                "model", "Corolla",
                "type", "car"
            ),
            user.token());

        JsonNode dashboard = getJson("/identity/api/v2/user/dashboard", user.token());
        JsonNode vehicles = dashboard.path("vehicles");
        if (vehicles.isArray() && vehicles.size() > 0) {
            return vehicles.get(0).path("uuid").asText();
        }
        throw new IllegalStateException("Não foi possível criar veículo: " + dashboard);
    }

    private static JsonNode postJson(String path, Map<String, Object> body, String token)
            throws Exception {
        String json = MAPPER.writeValueAsString(body);
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) builder.header("Authorization", "Bearer " + token);

        HttpResponse<String> resp = HTTP.send(builder.build(),
            HttpResponse.BodyHandlers.ofString());

        if (resp.body() == null || resp.body().isBlank()) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(resp.body());
    }

    private static JsonNode getJson(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(resp.body());
    }

    private static String extractSubFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = MAPPER.readTree(payload);
            return node.path("sub").asText();
        } catch (Exception e) {
            return "";
        }
    }

    private static String randomVin() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 17).toUpperCase();
    }
}
```

---

## 8. `src/main/java/br/edu/tcc/bola/baseline/RuleBasedBaseline.java`

```java
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
```

---

## 9. `src/main/java/br/edu/tcc/bola/Main.java`

```java
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

        // 3. Inferência LLM
        String spec = java.net.http.HttpClient.newHttpClient()
            .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(specUrl)).build(),
                  java.net.http.HttpResponse.BodyHandlers.ofString()).body();

        var llm = new LlmAnalyzer(apiKey);
        var llmResult = llm.analyze(spec, candidates.stream()
            .map(e -> e.method() + " " + e.path()).toList());

        System.out.println("Endpoints sensíveis identificados: " + llmResult.endpoints().size());

        // 4. Verificação diferencial
        var sessionA = new AuthSession(baseUrl, userA.token());
        var sessionB = new AuthSession(baseUrl, userB.token());
        var verifier = new DifferentialVerifier(sessionA, sessionB);

        var idsA = Map.of("id", vehicleA, "userId", userA.userId());
        var idsB = Map.of("id", vehicleB, "userId", userB.userId());

        var results = verifier.verify(llmResult.endpoints(), idsA, idsB);

        // 5. Relatório
        System.out.println("\n=== Resultados ===");
        for (var r : results) {
            System.out.printf("[%s] %s %s → %s (%s)%n",
                r.verdict(), r.endpoint().method(), r.endpoint().path(),
                r.verdict(), r.evidence());
        }
    }
}
```

---

## 10. `scripts/generate_charts.py`

```python
#!/usr/bin/env python3
"""
TCC BOLA Detecção — Script de Visualização de Resultados
Uso: python generate_charts.py results.csv
"""

import sys
import csv
import matplotlib.pyplot as plt
import numpy as np

def load_results(filepath):
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            data[row['tool']] = {
                'tp': int(row['tp']),
                'fp': int(row['fp']),
                'fn': int(row['fn']),
                'tn': int(row['tn']),
            }
    return data

def compute_metrics(tp, fp, fn, tn):
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0
    accuracy = (tp + tn) / (tp + fp + fn + tn) if (tp + fp + fn + tn) > 0 else 0
    return {'Precisão': precision, 'Recall': recall, 'F1-Score': f1, 'Acurácia': accuracy}

def plot_metrics(data, output='metrics_comparison.png'):
    tools = list(data.keys())
    metrics = ['Precisão', 'Recall', 'F1-Score', 'Acurácia']
    x = np.arange(len(metrics))
    width = 0.25

    fig, ax = plt.subplots(figsize=(10, 6))
    for i, tool in enumerate(tools):
        values = compute_metrics(**data[tool])
        bars = [values[m] * 100 for m in metrics]
        offset = (i - len(tools)/2 + 0.5) * width
        ax.bar(x + offset, bars, width, label=tool)
        for j, v in enumerate(bars):
            ax.text(x[j] + offset, v + 1, f'{v:.1f}%', ha='center', fontsize=8)

    ax.set_ylabel('Percentual (%)')
    ax.set_title('Comparação de Métricas — Detecção de BOLA')
    ax.set_xticks(x)
    ax.set_xticklabels(metrics)
    ax.legend()
    ax.set_ylim(0, 115)
    ax.grid(axis='y', alpha=0.3)

    plt.tight_layout()
    plt.savefig(output, dpi=150)
    print(f"Gráfico salvo em: {output}")

def plot_confusion_matrix(data, output='confusion_matrices.png'):
    fig, axes = plt.subplots(1, len(data), figsize=(5 * len(data), 4))
    if len(data) == 1:
        axes = [axes]

    for ax, (tool, d) in zip(axes, data.items()):
        matrix = np.array([[d['tn'], d['fp']], [d['fn'], d['tp']]])
        ax.imshow(matrix, cmap='Blues', vmin=0)
        for i in range(2):
            for j in range(2):
                ax.text(j, i, str(matrix[i, j]), ha='center', va='center',
                       fontsize=16, color='white' if matrix[i, j] > matrix.max()/2 else 'black')
        ax.set_xticks([0, 1]); ax.set_xticklabels(['Negativo (real)', 'Positivo (real)'])
        ax.set_yticks([0, 1]); ax.set_yticklabels(['Negativo (previsto)', 'Positivo (previsto)'])
        ax.set_title(tool)

    plt.tight_layout()
    plt.savefig(output, dpi=150)
    print(f"Matrizes de confusão salvas em: {output}")

def main():
    if len(sys.argv) < 2:
        print("Uso: python generate_charts.py results.csv")
        sys.exit(1)

    data = load_results(sys.argv[1])
    print("=== Métricas Calculadas ===")
    for tool, d in data.items():
        m = compute_metrics(**d)
        print(f"\n{tool}:")
        for k, v in m.items():
            print(f"  {k}: {v*100:.1f}%")

    plot_metrics(data)
    plot_confusion_matrix(data)

if __name__ == '__main__':
    main()
```

---

## 11. `docker-compose.yml` (para o crAPI)

```yaml
version: '3.8'

services:
  crapi-web:
    image: crapi/crapi-web:latest
    ports:
      - "8888:8888"
    environment:
      - DB_HOST=crapi-mongodb
      - DB_NAME=crapi
    depends_on:
      - crapi-mongodb

  crapi-mongodb:
    image: mongo:5.0
    ports:
      - "27017:27017"
    volumes:
      - crapi-mongo-data:/data/db

volumes:
  crapi-mongo-data:
```

**Nota:** o crAPI oficial tem um `docker-compose.yml` próprio mais completo. Recomenda-se clonar de https://github.com/OWASP/crAPI e usar o compose oficial.

---

## 12. `.gitignore`

```
target/
*.class
*.log
.idea/
.vscode/
*.iml
.env
results/
charts/
```

---

## 13. `README.md`

````markdown
# TCC BOLA Detector

Detecção de BOLA (Broken Object Level Authorization) em APIs REST baseada em
inferência semântica por LLM e verificação diferencial determinística.

## Pré-requisitos

- Java 17+
- Maven 3.9+
- Docker + Docker Compose
- Python 3.9+ (para scripts de visualização)
- Chave de API da OpenAI (`OPENAI_API_KEY`)

## Setup

### 1. Subir o crAPI

```bash
cd crapi/
docker compose up -d
```

Verifique:

```bash
curl http://localhost:8888/health
```

### 2. Baixar a especificação OpenAPI

```bash
curl http://localhost:8888/openapi.json -o openapi.json
```

### 3. Compilar o projeto

```bash
mvn clean package
```

### 4. Exportar a chave da OpenAI

```bash
export OPENAI_API_KEY=sk-...
```

### 5. Executar o detector

```bash
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.Main" \
  -Dexec.args="openapi.json http://localhost:8888"
```

### 6. Executar o baseline

```bash
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.baseline.RuleBasedBaseline" \
  -Dexec.args="openapi.json"
```

### 7. Gerar gráficos

```bash
pip install matplotlib numpy
python scripts/generate_charts.py results.csv
```

## Estrutura

- `src/main/java/br/edu/tcc/bola/openapi/` — parsing OpenAPI
- `src/main/java/br/edu/tcc/bola/llm/` — inferência semântica via LLM
- `src/main/java/br/edu/tcc/bola/verifier/` — motor de verificação diferencial
- `src/main/java/br/edu/tcc/bola/setup/` — setup automatizado do crAPI
- `src/main/java/br/edu/tcc/bola/baseline/` — baseline baseado em regras
- `scripts/` — scripts Python auxiliares

## Licença

Uso acadêmico.