# TCC — Detecção de BOLA em APIs REST

Arquitetura híbrida para detecção de **BOLA** (Broken Object Level Authorization)
em APIs REST, combinando:

1. **Inferência semântica via LLM** (`gpt-4o-mini`) para identificar endpoints
   candidatos a BOLA a partir do spec OpenAPI.
2. **Verificação diferencial determinística multi-sessão** — o Usuário A tenta
   acessar o recurso do Usuário B; a diferença de resposta confirma (ou refuta)
   a vulnerabilidade empiricamente.

TCC — Fatec Ourinhos.

---

## Estrutura do repositório

```
tcc_bola_fatec/
├── README.md              ← este arquivo (visão geral)
├── HOWTO.md               ← passo a passo completo de execução
├── DEVLOG.md              ← diário de decisões técnicas
├── RESULTADOS.md          ← análise dos experimentos e métricas
├── results.csv            ← métricas consolidadas
├── outputs/               ← logs, CSVs e gráficos das execuções
├── tcc-bola-detector/     ← detector (Java 17 + Maven)
└── drone-rescue-api/      ← alvo sintético inédito (Spring Boot + H2)
```

---

## Componentes do detector (`tcc-bola-detector/`)

- `openapi/OpenApiParser` — extrai endpoints com path parameters do spec.
- `llm/LlmAnalyzer` — consulta a OpenAI para apontar endpoints sensíveis.
- `verifier/DifferentialVerifier` — executa o teste cross-user e emite veredito
  (VULNERABLE / DENIED / INCONCLUSIVE).
- `setup/CrApiSetup`, `setup/VampiSetup`, `setup/DroneSetup` — adaptadores de
  setup para cada alvo (criação de usuários e recursos).
- `baseline/RuleBasedBaseline` — baseline de regras estáticas.
- `metrics/GroundTruth` + `metrics/MetricsCalculator` — cálculo de TP/FP/FN/TN
  contra um gabarito conhecido.
- `Main`, `MainSemLLM`, `MainVampi`, `MainDrone` — orquestradores de cada
  experimento.

---

## Experimentos

| Experimento | Alvo | Objetivo |
|---|---|---|
| Proposta (LLM + Diff) | crAPI | Benchmark principal |
| Sem LLM (Regras + Diff) | crAPI | Ablação — isola a contribuição do LLM |
| Baseline (Regras) | crAPI | Comparação — só regras estáticas |
| Baseline (ZAP) | crAPI | Comparação — DAST genérico |
| Proposta (LLM + Diff) | VAmPI | Generalização em 2º benchmark público |
| Proposta (LLM + Diff) | Drone Rescue API | Alvo **inédito** — robustez a contaminação |

Resultados e análise completos em **[RESULTADOS.md](RESULTADOS.md)**.

---

## Como reproduzir

O guia detalhado (pré-requisitos, subida dos alvos, comandos de cada experimento
e sequência limpa completa) está em **[HOWTO.md](HOWTO.md)**.

Resumo rápido:

```bash
# chave da OpenAI (arquivo key.env no formato OPEN_API_KEY=sk-...)
export OPENAI_API_KEY=$(cut -d= -f2- key.env)

cd tcc-bola-detector
mvn clean package -q

# detector principal (crAPI precisa estar rodando na porta 8888)
mvn exec:java -Dexec.mainClass="br.edu.tcc.bola.Main" \
  -Dexec.args="openapi.json http://localhost:8888 results_proposta.csv"
```

---

## Pré-requisitos

- Java 17+
- Maven 3.9+
- Docker + Docker Compose (para crAPI e VAmPI)
- Python 3.9+ (scripts de visualização)
- Chave de API da OpenAI

---

## Licença

Uso acadêmico.
