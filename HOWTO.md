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
  -Dexec.args="openapi.json http://localhost:8888" 2>&1 | tee ../outputs/llm-detector.txt
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
```

> **Atenção:** cada execução cria novos usuários e veículos no crAPI.
> Se o banco retornar `duplicate key`, corrija o sequence:
> ```bash
> docker exec postgresdb psql -U admin -d crapi -c \
>   "SELECT setval('user_login_id_seq', (SELECT MAX(id) FROM user_login) + 1);"
> ```

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

Preencha com os resultados reais de cada ferramenta:

```csv
tool,tp,fp,fn,tn,inconclusive,precision,recall,f1,accuracy,time_s,tokens
Proposta (LLM + Diff),4,0,0,3,2,1.000,1.000,1.000,0.778,14.5,4500
Baseline (Regras),4,5,0,0,0,0.444,1.000,0.615,0.444,0.5,0
Baseline (ZAP),0,80,4,0,0,0.000,0.000,0.000,0.000,300,0
```

| Coluna | Descrição |
|---|---|
| `tp` | Verdadeiro Positivo — BOLA real detectado corretamente |
| `fp` | Falso Positivo — falso alarme |
| `fn` | Falso Negativo — BOLA real não detectado |
| `tn` | Verdadeiro Negativo — endpoint seguro classificado corretamente |
| `inconclusive` | Resultado indeterminado (apenas para a proposta) |

---

## Sequência limpa completa

```bash
# 1. Posicionar na raiz do repositório
cd ~/Documents/GitHub/tcc_bola_fatec

# 2. Carregar chave
export OPENAI_API_KEY=$(cat key.env)

# 3. Garantir crAPI rodando
curl -s http://localhost:8888/identity/api/auth/signup | head -c 50

# 4. Entrar no projeto
cd tcc-bola-detector

# 5. Compilar
mvn clean package -q

# 6. Baseline regras
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.baseline.RuleBasedBaseline" \
  -Dexec.args="openapi.json" 2>&1 | tee ../outputs/baseline-rule-based.txt

# 7. Detector LLM
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.Main" \
  -Dexec.args="openapi.json http://localhost:8888" 2>&1 | tee ../outputs/llm-detector.txt

# 8. ZAP
docker run --rm --network host -v $(pwd):/zap/wrk/:rw \
  -t ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
  -t openapi.json -f openapi -r zap-report.html -J zap-report.json
python3 scripts/zap_json_to_csv.py zap-report.json > ../outputs/zap_alerts.csv

# 9. Gráficos
python3 scripts/generate_charts.py ../results.csv
mv metrics_comparison.png confusion_matrices.png ../outputs/
```
