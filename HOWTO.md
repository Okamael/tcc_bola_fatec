# HOWTO — Execução do TCC BOLA Detector

Guia completo para reproduzir todos os experimentos do zero.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Java | 17+ | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Docker + Docker Compose | 24+ | `docker --version` |
| Python | 3.9+ | `python3 --version` |
| Chave OpenAI | — | https://platform.openai.com/api-keys |

---

## Estrutura do repositório

```
tcc_bola_fatec/
├── HOWTO.md                  ← este arquivo
├── DEVLOG.md                 ← diário de decisões técnicas
├── projeto_codigo.md         ← especificação original do código
├── key.env                   ← chave da OpenAI (NÃO commitar)
├── results.csv               ← métricas consolidadas dos experimentos
├── outputs/                  ← saídas geradas pelas ferramentas
│   ├── baseline-rule-based.txt
│   ├── llm-detector.txt
│   ├── zap_alerts.csv
│   ├── metrics_comparison.png
│   └── confusion_matrices.png
└── tcc-bola-detector/        ← projeto Java (código-fonte)
    ├── pom.xml
    ├── openapi.json
    ├── scripts/
    │   ├── generate_charts.py
    │   └── zap_json_to_csv.py
    └── src/
```

---

## Passo 1 — Subir o crAPI

O crAPI é a API vulnerável usada como alvo dos experimentos.

```bash
# Clonar o crAPI oficial (se ainda não tiver)
git clone https://github.com/OWASP/crAPI.git crapi-official
cd crapi-official

# Subir todos os serviços
docker compose -f deploy/docker/docker-compose.yml up -d

# Aguardar ~2 minutos e verificar
curl http://localhost:8888/identity/api/auth/signup
```

> O crAPI expõe a API na porta `8888`.

---

## Passo 2 — Compilar o projeto Java

```bash
cd tcc-bola-detector
mvn clean package -q
```

---

## Passo 3 — Configurar a chave da OpenAI

```bash
# Carregar a chave do arquivo key.env (na raiz do repositório)
export OPENAI_API_KEY=$(cat ../key.env)

# Verificar
echo "Chave carregada: ${OPENAI_API_KEY:0:10}..."
```

> O arquivo `key.env` deve conter apenas a chave, sem aspas:
> ```
> sk-proj-SUACHAVEAQUI
> ```

---

## Passo 4 — Executar o Baseline por Regras

Detecta endpoints com path parameters — sem análise semântica.

```bash
cd tcc-bola-detector

mvn exec:java \
  -Dexec.mainClass="br.edu.tcc.bola.baseline.RuleBasedBaseline" \
  -Dexec.args="openapi.json" 2>&1 | tee ../outputs/baseline-rule-based.txt
```

**Saída esperada:** lista de endpoints com path parameters (total: 9).

---

## Passo 5 — Executar o Detector LLM + Verificação Diferencial

Pipeline principal: LLM identifica endpoints sensíveis e o verificador testa acessos cruzados.

```bash
cd tcc-bola-detector

mvn exec:java \
  -Dexec.mainClass="br.edu.tcc.bola.Main" \
  -Dexec.args="openapi.json http://localhost:8888 results_proposta.csv" 2>&1 | tee ../outputs/llm-detector.txt
```

**Saída esperada:**
```
=== Endpoints candidatos: 9 ===
Endpoints sensíveis identificados: 9
=== Resultados ===
[VULNERABLE] get /identity/api/v2/vehicle/{vehicleId}/location
[VULNERABLE] get /community/api/v2/community/posts/{postId}
[VULNERABLE] get /workshop/api/shop/orders/{order_id}
[VULNERABLE] put /workshop/api/shop/orders/{order_id}
[DENIED]     get /identity/api/v2/user/videos/{video_id}
...
=== [Proposta] Resumo (vs. gabarito) ===
TP=4  FP=0  FN=1  TN=4  (INCONCLUSIVE=2)
Precisão=100.0%  Recall=80.0%  F1=88.9%  Acurácia=88.9%
```

> Agora o `Main` também exporta `results_proposta.csv` com as métricas.

> **Atenção:** cada execução cria novos usuários e veículos no crAPI.
> Se o banco retornar `duplicate key`, corrija o sequence:
> ```bash
> docker exec postgresdb psql -U admin -d crapi -c \
>   "SELECT setval('user_login_id_seq', (SELECT MAX(id) FROM user_login) + 1);"
> ```

---

## Passo 5b — Experimento "Sem LLM" (ablação)

Isola a contribuição do LLM: usa a lista de endpoints do `RuleBasedBaseline`
(todos com path parameter) em vez da inferência semântica, mas passa pelo
mesmo motor de verificação diferencial.

```bash
cd tcc-bola-detector

docker exec postgresdb psql -U admin -d crapi -c \
  "SELECT setval('user_login_id_seq', (SELECT MAX(id) FROM user_login) + 1);"

mvn exec:java \
  -Dexec.mainClass="br.edu.tcc.bola.MainSemLLM" \
  -Dexec.args="openapi.json http://localhost:8888 results_sem_llm.csv" 2>&1 \
  | tee ../outputs/sem-llm-detector.txt
```

**Saída esperada:** `Sem LLM (Regras + Diff),4,0,1,4,2,1.000,0.800,0.889,0.889,<tempo>,0`
e o arquivo `results_sem_llm.csv`.

> Não requer `OPENAI_API_KEY` — o custo de tokens é 0.

---

## Passo 5c — Teste de generalização no VAmPI

Valida a arquitetura em um segundo benchmark (erev0s/vampi), com estrutura de
API, autenticação e identificadores diferentes do crAPI.

```bash
# 1. Subir o VAmPI na porta 5001 (a 5000 conflita com o AirPlay no macOS)
docker rm -f vampi 2>/dev/null
docker run -d -e vulnerable=1 -p 5001:5000 --name vampi erev0s/vampi

# 2. Baixar o spec do VAmPI
curl -s http://localhost:5001/openapi.json -o tcc-bola-detector/vampi-openapi.json

# 3. Rodar o detector completo
cd tcc-bola-detector
export OPENAI_API_KEY=$(cut -d= -f2- ../key.env)

mvn exec:java \
  -Dexec.mainClass="br.edu.tcc.bola.MainVampi" \
  -Dexec.args="vampi-openapi.json http://localhost:5001 results_vampi.csv" 2>&1 \
  | tee ../outputs/vampi-detector.txt
```

**Saída esperada:** `Proposta VAmPI (LLM + Diff),3,0,2,0,2,1.000,0.600,0.750,0.600,<tempo>,0`
e o arquivo `results_vampi.csv`.

> O VAmPI popula seu banco automaticamente via `GET /createdb` (feito pelo `MainVampi`).

---

## Passo 6 — Executar o ZAP (Baseline DAST)

```bash
cd tcc-bola-detector

# Varredura e geração de relatórios HTML e JSON
docker run --rm --network host -v $(pwd):/zap/wrk/:rw \
  -t ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
  -t openapi.json -f openapi \
  -r zap-report.html \
  -J zap-report.json

# Converter JSON para CSV
python3 scripts/zap_json_to_csv.py zap-report.json > ../outputs/zap_alerts.csv
```

> A primeira execução baixa a imagem (~1GB) e pode demorar.

---

## Passo 7 — Gerar os gráficos

```bash
cd tcc-bola-detector

pip3 install matplotlib numpy

python3 scripts/generate_charts.py ../results.csv

mv metrics_comparison.png confusion_matrices.png ../outputs/
```

---

## Estrutura do `results.csv`

Resultados consolidados dos experimentos (reproduzidos em 05/11/2026):

```csv
tool,tp,fp,fn,tn,inconclusive,precision,recall,f1,accuracy,time_s,tokens
Proposta (LLM + Diff),4,0,1,4,2,1.000,0.800,0.889,0.889,31.4,4500
Sem LLM (Regras + Diff),4,0,1,4,2,1.000,0.800,0.889,0.889,3.7,0
Baseline (Regras),4,5,0,0,0,0.444,1.000,0.615,0.444,0.5,0
Baseline (ZAP),0,80,4,0,0,0.000,0.000,0.000,0.000,300,0
Proposta VAmPI (LLM + Diff),3,0,2,0,2,1.000,0.600,0.750,0.600,5.7,0
```

> As métricas são calculadas contra um gabarito (`metrics/GroundTruth.java`);
> FP e FN não são mais fixados em zero. Ver `RESULTADOS.md` seção 1 (Metodologia).

| Coluna | Descrição |
|---|---|
| `tp` | Verdadeiro Positivo — BOLA real detectado corretamente |
| `fp` | Falso Positivo — falso alarme |
| `fn` | Falso Negativo — BOLA real não detectado |
| `tn` | Verdadeiro Negativo — endpoint seguro classificado corretamente |
| `inconclusive` | Resultado indeterminado (405/erro — apenas para a proposta e sem-LLM) |

> A análise detalhada de cada veredito está em `RESULTADOS.md`.

---

## Sequência limpa completa

```bash
# 1. Posicionar na raiz do repositório
cd ~/Documents/GitHub/tcc_bola_fatec

# 2. Carregar chave (key.env está no formato OPEN_API_KEY=sk-...)
export OPENAI_API_KEY=$(cut -d= -f2- key.env)

# 3. Garantir crAPI (8888) e VAmPI (5001) rodando
curl -s -o /dev/null -w "crAPI:%{http_code}\n" http://localhost:8888/identity/api/auth/signup
curl -s -o /dev/null -w "VAmPI:%{http_code}\n" http://localhost:5001/

# 4. Entrar no projeto e compilar
cd tcc-bola-detector
mvn clean package -q

# 5. Corrigir sequence do Postgres do crAPI (evita duplicate key)
docker exec postgresdb psql -U admin -d crapi -c \
  "SELECT setval('user_login_id_seq', (SELECT MAX(id) FROM user_login) + 1);"

# 6. Baseline regras (crAPI)
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.baseline.RuleBasedBaseline" \
  -Dexec.args="openapi.json" 2>&1 | tee ../outputs/baseline-rule-based.txt

# 7. Detector LLM (crAPI — proposta completa)
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.Main" \
  -Dexec.args="openapi.json http://localhost:8888 results_proposta.csv" 2>&1 | tee ../outputs/llm-detector.txt

# 8. Experimento Sem LLM (crAPI — ablação)
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.MainSemLLM" \
  -Dexec.args="openapi.json http://localhost:8888 results_sem_llm.csv" 2>&1 \
  | tee ../outputs/sem-llm-detector.txt

# 9. Detector LLM (VAmPI — generalização)
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.MainVampi" \
  -Dexec.args="vampi-openapi.json http://localhost:5001 results_vampi.csv" 2>&1 \
  | tee ../outputs/vampi-detector.txt

# 10. ZAP (crAPI — baseline DAST)
docker run --rm --network host -v $(pwd):/zap/wrk/:rw \
  -t ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
  -t openapi.json -f openapi -r zap-report.html -J zap-report.json
python3 scripts/zap_json_to_csv.py zap-report.json > ../outputs/zap_alerts.csv

# 11. Gráficos
python3 scripts/generate_charts.py ../results.csv
mv metrics_comparison.png confusion_matrices.png ../outputs/
```
