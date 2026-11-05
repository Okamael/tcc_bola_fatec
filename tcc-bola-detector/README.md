# tcc-bola-detector

Detector de **BOLA** (Broken Object Level Authorization) em APIs REST:
inferência semântica via LLM (`gpt-4o-mini`) + verificação diferencial
determinística multi-sessão.

Este é o módulo do detector. Para a visão geral do projeto, o guia de execução
completo e a análise dos resultados, veja os documentos na raiz do repositório:

- **[../README.md](../README.md)** — visão geral e índice
- **[../HOWTO.md](../HOWTO.md)** — passo a passo de todos os experimentos
- **[../RESULTADOS.md](../RESULTADOS.md)** — métricas e análise
- **[../DEVLOG.md](../DEVLOG.md)** — diário de decisões técnicas

## Stack

- Java 17 + Maven
- `swagger-parser` (parsing OpenAPI) e `jackson-databind` (JSON)

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `openapi/` | Extrai endpoints com path parameters do spec |
| `llm/` | Inferência semântica via OpenAI |
| `verifier/` | Motor de verificação diferencial (cross-user) |
| `setup/` | Adaptadores de setup por alvo (crAPI, VAmPI, Drone) |
| `baseline/` | Baseline de regras estáticas |
| `metrics/` | Gabarito (ground truth) e cálculo de TP/FP/FN/TN |

## Pontos de entrada (classes `Main*`)

| Classe | Experimento |
|---|---|
| `Main` | Proposta completa (LLM + Diff) no crAPI |
| `MainSemLLM` | Ablação: regras estáticas + Diff (sem LLM) |
| `MainVampi` | Proposta no VAmPI (generalização) |
| `MainDrone` | Proposta no alvo sintético inédito |
| `baseline.RuleBasedBaseline` | Baseline de regras |

## Execução rápida

```bash
mvn clean package -q
export OPENAI_API_KEY=$(cut -d= -f2- ../key.env)

mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.Main" \
  -Dexec.args="openapi.json http://localhost:8888 results_proposta.csv"
```

Os comandos de cada experimento estão em **[../HOWTO.md](../HOWTO.md)**.

## Licença

Uso acadêmico.
