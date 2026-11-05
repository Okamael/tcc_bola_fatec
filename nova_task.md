# Contexto

Estou finalizando meu TCC (Trabalho de Conclusão de Curso) na Fatec Ourinhos. O trabalho propõe uma arquitetura híbrida para detecção de BOLA (Broken Object Level Authorization) em APIs REST, combinando:
1. Inferência semântica via LLM (gpt-4o-mini) para identificar endpoints sensíveis.
2. Verificação diferencial determinística multi-sessão (Usuário A tenta acessar recurso do Usuário B) para confirmar a vulnerabilidade.

O código já está funcional e testado contra o OWASP crAPI (benchmark principal). Os resultados atuais são:
- Proposta (LLM + Diff): 4 TP, 0 FP, 5 inconclusivos → Precisão/Recall 100%
- Baseline (Regras): 4 TP, 5 FP → Precisão 44,4%
- Baseline (ZAP): 0 TP, 80 FP, 4 FN → Recall 0%

O professor sugeriu dois experimentos adicionais:
1. **Rodar o experimento "sem LLM"**: usar a lista de endpoints gerada pela regra estática (em vez da lista do LLM) e passar para o motor de verificação diferencial. Isso isola a contribuição do LLM.
2. **Testar em um segundo alvo (VAmPI)**: rodar o detector contra o VAmPI (outra API vulnerável) para validar a generalização.

# Estrutura do Projeto (já existente no repositório)

O projeto está em Java 17 (LTS), com Maven, Spring Boot, swagger-parser, jackson-databind, e Docker Compose. A estrutura de pacotes é:

- `br.edu.tcc.bola.openapi.OpenApiParser` — extrai endpoints com path parameters do spec OpenAPI.
- `br.edu.tcc.bola.llm.LlmAnalyzer` — chama a OpenAI para identificar endpoints sensíveis a BOLA.
- `br.edu.tcc.bola.verifier.AuthSession` — encapsula uma sessão autenticada HTTP (token JWT + URL base).
- `br.edu.tcc.bola.verifier.DifferentialVerifier` — executa o teste diferencial cross-user e emite veredito (VULNERABLE, DENIED, INCONCLUSIVE).
- `br.edu.tcc.bola.setup.CrApiSetup` — automatiza a criação de usuários e recursos no crAPI (signup, login, criação de veículos/pedidos).
- `br.edu.tcc.bola.baseline.RuleBasedBaseline` — detector de regras estáticas (qualquer endpoint com path parameter é candidato).
- `br.edu.tcc.bola.Main` — orquestra o fluxo completo (setup → LLM → verificação → relatório).

# Tarefas a Executar

## Tarefa 1: Experimento "Sem LLM"

**Objetivo:** Rodar o motor de verificação diferencial com a lista de endpoints gerada pelo `RuleBasedBaseline` (em vez da lista do `LlmAnalyzer`), para isolar a contribuição do LLM.

**O que criar:**
- Uma nova classe `MainSemLLM.java` (no pacote `br.edu.tcc.bola`) que:
  1. Executa o `CrApiSetup` para criar os dois usuários (A e B) e seus recursos (veículos/pedidos).
  2. Chama o `RuleBasedBaseline` para obter a lista de endpoints candidatos (todos com path parameters).
  3. Converte essa lista para o formato que o `DifferentialVerifier` espera (List<SensitiveEndpoint>).
  4. Executa o `DifferentialVerifier` com os dois usuários e seus respectivos IDs de recursos.
  5. Imprime um relatório no console com: total de endpoints testados, quantos VULNERABLE, quantos DENIED, quantos INCONCLUSIVE.
  6. Exporta os resultados para um CSV (`results_sem_llm.csv`) no mesmo formato do `results.csv` existente (tool, tp, fp, fn, tn, inconclusive, precision, recall, f1, accuracy, time_s, tokens).

**Requisitos:**
- O código deve compilar e rodar sem intervenção manual (após o crAPI estar rodando via Docker).
- O `RuleBasedBaseline` já retorna uma lista de `BaselineFinding` (path, method, reason). Você precisa adaptar para o formato `SensitiveEndpoint` (path, method, objectIdParam, ownershipHints, rationale). Para o objectIdParam, use o primeiro path parameter (ou "id" como fallback). Para ownershipHints e rationale, deixe vazio ou com uma string genérica.
- O tempo de execução deve ser medido (em segundos).
- O custo de tokens deve ser 0 (não usa LLM).

**Saída esperada:**
- Um arquivo `results_sem_llm.csv` com uma linha: `Sem LLM,4,0,0,0,5,1.00,1.00,1.00,0.44,<tempo>,0`.
- Um relatório no console que mostra quantos endpoints foram testados e o veredito de cada um.

## Tarefa 2: Teste no VAmPI

**Objetivo:** Rodar o detector completo (LLM + Diff) contra o VAmPI, um segundo benchmark de API vulnerável.

**Passos:**
1. **Subir o VAmPI via Docker:**
   ```bash
   docker run -e vulnerable=1 -p 5000:5000 erev0s/vampi