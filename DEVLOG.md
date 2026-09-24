# Diário de Desenvolvimento — TCC BOLA Detector

Registro completo de todas as alterações realizadas durante a sessão de desenvolvimento,
com contexto técnico de cada decisão.

---

## 1. Criação do projeto do zero

**O que foi feito:** estrutura completa do projeto gerada a partir do `projeto_codigo.md`.

**Arquivos criados:**

| Arquivo | Descrição |
|---|---|
| `pom.xml` | Configuração Maven com dependências `swagger-parser 2.1.22` e `jackson-databind 2.17.0` |
| `src/.../Main.java` | Ponto de entrada — orquestra setup, LLM e verificação |
| `src/.../openapi/OpenApiParser.java` | Extrai endpoints com path parameters do spec OpenAPI |
| `src/.../llm/LlmAnalyzer.java` | Chama a API da OpenAI (GPT-4o-mini) para identificar endpoints sensíveis a BOLA |
| `src/.../llm/LlmResponse.java` | Record de resposta tipada da LLM |
| `src/.../verifier/AuthSession.java` | Encapsula uma sessão autenticada HTTP |
| `src/.../verifier/DifferentialVerifier.java` | Motor de verificação diferencial (acessa recurso de A com token de A e de B) |
| `src/.../setup/CrApiSetup.java` | Setup automatizado do crAPI (criar usuários e veículos) |
| `src/.../baseline/RuleBasedBaseline.java` | Baseline de detecção por regras simples |
| `scripts/generate_charts.py` | Script Python para gerar gráficos de métricas |
| `docker-compose.yml` | Compose simplificado para o crAPI |
| `.gitignore` | Ignora `target/`, `.env`, `results/`, etc. |
| `README.md` | Instruções de setup e execução |

**Por quê:** o projeto precisava de uma base funcional compilável antes de qualquer execução.
O `mvn compile` foi rodado para confirmar que todos os arquivos estavam sintaticamente corretos — resultado: `BUILD SUCCESS`.

---

## 2. Problema: `openapi.json` inválido

**Arquivo afetado:** `openapi.json` (na raiz do projeto)

**O que estava errado:**  
O arquivo tinha 159 bytes de HTML de erro 404:
```html
<html><head><title>404 Not Found</title></head>...
```
Isso aconteceu porque o crAPI **não expõe** o endpoint `/openapi.json` — era o caminho errado.

**Como foi descoberto:**  
```bash
head -c 200 openapi.json
# retornou HTML em vez de JSON
```

**Solução:**  
O spec oficial do crAPI está no repositório OWASP no GitHub:
```bash
curl -s "https://raw.githubusercontent.com/OWASP/crAPI/develop/openapi-spec/crapi-openapi-spec.json" \
  -o openapi.json
```
O arquivo correto tem **101 KB** e **40 paths** cobrindo os três serviços (identity, community, workshop).

---

## 3. Problema: erro de parsing JSON no signup (`CRAPIResponse`)

**Arquivo afetado:** `CrApiSetup.java`

**O que estava errado:**  
O endpoint `/identity/api/v2/vehicle/add_vehicle` retorna texto puro no formato:
```
CRAPIResponse(message=Invalid Token, status=401)
```
O Jackson tentava fazer `MAPPER.readTree()` nessa string e lançava:
```
JsonParseException: Unrecognized token 'CRAPIResponse'
```

**Solução:**  
Separar a leitura em dois métodos:
- `postRaw()` — retorna o body como `String` sem tentar parsear
- `postJson()` — verifica se o body começa com `{` ou `[` antes de parsear; caso contrário retorna `ObjectNode` vazio

O mesmo tratamento foi aplicado ao `getJson()`.

---

## 4. Problema: número de telefone fixo causava colisão no signup

**Arquivo afetado:** `CrApiSetup.java` — método `createUser()`

**O que estava errado:**  
O número `11999999999` era fixo. Na segunda execução (ou se já havia sido usado antes), o crAPI retornava:
```json
{"message":"Number already registered! Number: 11999999999","status":403}
```
O signup falhava silenciosamente e o login retornava `token: null`.

**Solução:**  
Gerar número aleatório de 10 dígitos a cada execução:
```java
String number = String.valueOf(1_000_000_000L + (long)(Math.random() * 9_000_000_000L));
```

---

## 5. Problema: email longo estourava coluna `varchar(500)` do banco

**Arquivo afetado:** `CrApiSetup.java` — método `createUser()`

**O que estava errado:**  
O email `tcc_A_<UUID-completo>@test.com` tinha ~50 caracteres. O crAPI armazena o JWT inteiro na coluna `jwt_token` da tabela `user_login`. O JWT contém o email no campo `sub` — um email longo torna o JWT longo demais para a coluna `varchar(500)`. O banco retornava:
```
could not execute statement [ERROR: value too long for type character varying(500)]
```

**Como foi descoberto:**  
O log de debug do `postJson` mostrou exatamente esse erro no raw do login.

**Solução:**  
Usar email curto com apenas 8 caracteres do UUID:
```java
String shortId = UUID.randomUUID().toString().substring(0, 8);
String email = "tcc" + label.toLowerCase() + shortId + "@t.co";
// ex: tcca840d254b@t.co
```

---

## 6. Problema: `add_vehicle` sempre retornava "already added"

**Arquivo afetado:** `CrApiSetup.java` — método `createVehicle()`

**O que estava errado:**  
O código original tentava criar VINs aleatórios com UUID. O crAPI **não aceita VINs novos** — ele tem 26 VINs pré-cadastrados no banco, cada um com um `pincode` gerado aleatoriamente no momento do seed. Para associar um veículo a um usuário, é necessário:
1. Informar um VIN que **já existe** no banco
2. Informar o **pincode correto** daquele VIN (funciona como "número de chassi + código de ativação")

Enviar um VIN inexistente **ou** o pincode errado resultava em:
```json
{"message":"Sorry, This Vehicle is already added.","status":403}
```

**Como foi descoberto:**  
Descompilando a classe `VehicleServiceImpl.class` dentro do container do crAPI:
```
findByVin(vin) → se encontrou: verifica pincode.equalsIgnoreCase(input) → se bate: associa owner
```
E consultando o banco diretamente:
```sql
SELECT vin, pincode FROM vehicle_details WHERE owner_id IS NULL LIMIT 5;
```

**Solução:**  
Buscar VIN + pincode reais do banco via `docker exec` antes de chamar o endpoint:
```java
ProcessBuilder pb = new ProcessBuilder(
    "docker", "exec", "postgresdb",
    "psql", "-U", "admin", "-d", "crapi", "-t", "-A", "-F", "|",
    "-c", "SELECT vin, pincode FROM vehicle_details WHERE owner_id IS NULL LIMIT 1 OFFSET " + offset + ";"
);
```
Também foi adicionado polling de até 15 segundos no endpoint `/vehicle/vehicles` porque o crAPI processa o registro de forma assíncrona via fila de mensagens.

---

## 7. Problema: `URI with undefined scheme` ao ler o spec

**Arquivo afetado:** `Main.java`

**O que estava errado:**  
O código original tentava buscar o spec via HTTP:
```java
String spec = java.net.http.HttpClient.newHttpClient()
    .send(HttpRequest.newBuilder(URI.create(specUrl)).build(), ...)
    .body();
```
Quando `specUrl = "openapi.json"` (caminho local), `URI.create("openapi.json")` não tem scheme (`http://` ou `file://`) e o `HttpClient` lança:
```
IllegalArgumentException: URI with undefined scheme
```

**Solução:**  
Detectar se é URL ou caminho local:
```java
if (specUrl.startsWith("http://") || specUrl.startsWith("https://")) {
    spec = HttpClient.newHttpClient().send(...).body();
} else {
    spec = Files.readString(Path.of(specUrl));
}
```

---

## 8. Problema: todos os resultados `INCONCLUSIVE` por path parameters não substituídos

**Arquivo afetado:** `Main.java` e `CrApiSetup.java`

**O que estava errado:**  
O verificador diferencial recebia apenas:
```java
Map.of("id", vehicleA, "userId", userA.userId())
```
Mas os endpoints identificados pela LLM usavam outros nomes de parâmetro:
- `{video_id}` — em `/identity/api/v2/user/videos/{video_id}`
- `{vehicleId}` — em `/identity/api/v2/vehicle/{vehicleId}/location`
- `{postId}` — em `/community/api/v2/community/posts/{postId}`
- `{order_id}` — em `/workshop/api/shop/orders/{order_id}`

O método `fillPath()` no `DifferentialVerifier` não encontrava correspondência e deixava os `{}` na URL, causando:
```
Illegal character in path at index 50: http://localhost:8888/.../{video_id}
```

**Solução:**  
Novo método `fetchResourceIds()` no `CrApiSetup` que coleta os IDs reais de cada tipo de recurso:
- **`vehicleId` / `id`** — UUID do veículo criado no setup
- **`userId`** — extraído do JWT (`sub`)
- **`video_id`** — lido do dashboard via `/identity/api/v2/user/dashboard`
- **`postId`** — criado via `POST /community/api/v2/community/posts`; fallback para post mais recente
- **`order_id`** — criado via `POST /workshop/api/shop/orders` com produto existente; fallback `"1"`

O `Main.java` passou a chamar `fetchResourceIds()` para cada usuário antes de invocar o verificador.

---

## Resumo dos arquivos alterados

| Arquivo | Alterações |
|---|---|
| `openapi.json` | Substituído: de HTML 404 para o spec oficial do OWASP crAPI com 40 endpoints |
| `src/.../setup/CrApiSetup.java` | Reescrito em grande parte: email curto, número aleatório, `postRaw`/`postJson` defensivos, `pickFreeVinWithPincode()` via Postgres, polling assíncrono, novo método `fetchResourceIds()` |
| `src/.../Main.java` | Leitura do spec por arquivo ou HTTP; uso de `fetchResourceIds()` para mapear todos os path parameters reais |

Os demais arquivos (`OpenApiParser`, `LlmAnalyzer`, `LlmResponse`, `AuthSession`, `DifferentialVerifier`, `RuleBasedBaseline`, `pom.xml`, `docker-compose.yml`, `scripts/generate_charts.py`) **não foram alterados** em relação ao código original do `projeto_codigo.md`.
