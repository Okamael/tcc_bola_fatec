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
