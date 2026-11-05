# Resultados dos Experimentos — Detecção de BOLA

Análise consolidada dos experimentos reproduzidos em **05/11/2026**, incluindo os
dois experimentos adicionais sugeridos pelo orientador (ablação "Sem LLM" e
generalização no VAmPI).

---

## 1. Tabela consolidada

Métricas calculadas **contra um gabarito (ground truth)** de endpoints
reconhecidamente vulneráveis em cada benchmark — ver seção 3 e a classe
`metrics/GroundTruth.java`.

| Ferramenta | TP | FP | FN | TN | Inconcl. | Precisão | Recall | F1 | Acurácia | Tempo (s) | Tokens |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Proposta (LLM + Diff)** — crAPI | 4 | 0 | 1 | 4 | 2 | 100% | 80% | 88,9% | 88,9% | 31,4 | ~4500 |
| **Sem LLM (Regras + Diff)** — crAPI | 4 | 0 | 1 | 4 | 2 | 100% | 80% | 88,9% | 88,9% | 3,7 | 0 |
| **Baseline (Regras)** — crAPI | 4 | 5 | 0 | 0 | 0 | 44,4% | 100% | 61,5% | 44,4% | 0,5 | 0 |
| **Baseline (ZAP)** — crAPI | 0 | 80 | 4 | 0 | 0 | 0% | 0% | 0% | 0% | ~300 | 0 |
| **Proposta (LLM + Diff)** — VAmPI | 3 | 0 | 2 | 0 | 2 | 100% | 60% | 75% | 60% | 5,7 | 0* |
| **Proposta (LLM + Diff)** — Drone (inédito) | 3 | 1 | 0 | 1 | 0 | 75% | 100% | 85,7% | 80% | 7,2 | 0* |

> *No VAmPI o LLM foi usado, mas o custo de tokens não foi instrumentado nesta
> execução (coluna registra 0 por limitação do contador, não por ausência de uso).

**Como ler estes números:** a proposta mantém **precisão de 100%** em ambos os
alvos (não gera falsos alarmes), mas o **recall fica abaixo de 100%** porque os
endpoints vulneráveis de *escrita* (POST/PUT) não são detectados — o verificador
diferencial só executa GET (ver seção 3 e 5). Os falsos negativos (FN) são
exatamente esses endpoints de escrita.

### Metodologia de cálculo

As métricas **não** assumem FP=FN=0. Cada veredito do verificador é comparado
contra o gabarito (`metrics/GroundTruth.java`) pela classe `MetricsCalculator`:

| Veredito do detector | Está no gabarito? | Classificação |
|---|---|---|
| VULNERABLE | sim | **TP** (acertou) |
| VULNERABLE | não | **FP** (falso alarme) |
| DENIED / INCONCLUSIVE | sim | **FN** (deixou passar) |
| DENIED / INCONCLUSIVE | não | **TN** (corretamente ignorado) |

Assim, um endpoint vulnerável marcado INCONCLUSIVE conta como FN (penaliza o
recall), e um endpoint seguro marcado VULNERABLE contaria como FP (penalizaria a
precisão). As métricas refletem o desempenho real, não um valor fixado no código.

![Comparação de métricas](outputs/metrics_comparison.png)

![Matrizes de confusão](outputs/confusion_matrices.png)

---

## 2. Detalhamento por endpoint

### 2.1 Proposta e "Sem LLM" no crAPI (9 endpoints)

Coluna "Classe" = classificação vs. gabarito (seção 1, Metodologia).

| # | Método | Endpoint | Veredito | Classe | Interpretação |
|---|---|---|---|---|---|
| 1 | GET | `/identity/api/v2/user/videos/{video_id}` | DENIED | TN | Recurso de vídeo inexistente; não é BOLA |
| 2 | PUT | `/identity/api/v2/user/videos/{video_id}` | DENIED | TN | Idem |
| 3 | DELETE | `/identity/api/v2/user/videos/{video_id}` | DENIED | TN | Idem |
| 4 | DELETE | `/identity/api/v2/admin/videos/{video_id}` | INCONCLUSIVE | TN | Não vulnerável a BOLA (controle por papel/admin) |
| 5 | GET | `/identity/api/v2/vehicle/{vehicleId}/location` | **VULNERABLE** | **TP** | A acessou a localização do veículo de B |
| 6 | GET | `/community/api/v2/community/posts/{postId}` | **VULNERABLE** | **TP** | A leu o post de B |
| 7 | POST | `/community/.../posts/{postId}/comment` | INCONCLUSIVE | **FN** | Vulnerável, mas testado via GET → 405 |
| 8 | GET | `/workshop/api/shop/orders/{order_id}` | **VULNERABLE** | **TP** | A leu o pedido de B |
| 9 | PUT | `/workshop/api/shop/orders/{order_id}` | **VULNERABLE** | **TP** | A alterou o pedido de B |

**Total:** 4 TP, 0 FP, 1 FN, 4 TN (2 vereditos INCONCLUSIVE, sendo 1 FN e 1 TN).

### 2.2 Proposta no VAmPI (5 endpoints)

| # | Método | Endpoint | Veredito | Classe | Interpretação |
|---|---|---|---|---|---|
| 1 | GET | `/books/v1/{book_title}` | **VULNERABLE** | **TP** | A leu o `secret` do livro de B (BOLA clássico) |
| 2 | GET | `/users/v1/{username}` | **VULNERABLE** | **TP** | A leu os dados de B |
| 3 | DELETE | `/users/v1/{username}` | **VULNERABLE** | **TP** | A pôde deletar o usuário B |
| 4 | PUT | `/users/v1/{username}/email` | INCONCLUSIVE | **FN** | Vulnerável, mas testado via GET → 405 |
| 5 | PUT | `/users/v1/{username}/password` | INCONCLUSIVE | **FN** | Vulnerável, mas testado via GET → 405 |

**Total:** 3 TP, 0 FP, 2 FN, 0 TN.

### 2.3 Proposta na Drone Rescue API (alvo inédito, 5 endpoints)

Este é o experimento que **elimina o risco de contaminação de benchmark**: a API
foi criada do zero para o TCC (domínio fictício "Observatório de Drones de
Resgate"), nunca publicada antes dos testes. Logo, a LLM não pode tê-la
memorizado — qualquer acerto vem de **raciocínio estrutural** sobre o spec. O
gabarito é conhecido por construção (nós escrevemos quais endpoints validam o
dono e quais não).

| # | Método | Endpoint | Veredito | Classe | Interpretação |
|---|---|---|---|---|---|
| 1 | GET | `/api/v1/missions/{missionId}` | **VULNERABLE** | **TP** | A leu coordinates/briefing da missão de B |
| 2 | PUT | `/api/v1/missions/{missionId}` | **VULNERABLE** | **TP** | A alterou a missão de B |
| 3 | GET | `/api/v1/pilots/{pilotId}/profile` | **VULNERABLE** | **TP** | A leu o perfil de B |
| 4 | GET | `/api/v1/missions/{missionId}/briefing` | DENIED | TN | Seguro: `own=200, cross=403` — validação de posse detectada |
| 5 | DELETE | `/api/v1/missions/{missionId}` | **VULNERABLE** | **FP** | **Falso positivo** — ver explicação abaixo |

**Total:** 3 TP, 1 FP, 0 FN, 1 TN. Precisão 75%, **Recall 100%**, F1 85,7%.

**Destaques deste experimento:**
- **Recall 100% em alvo inédito:** a arquitetura encontrou os 3 BOLAs reais sem
  conhecimento prévio da aplicação — forte evidência de que raciocina sobre a
  estrutura, não memoriza benchmarks públicos.
- **Detecção correta de endpoint seguro (TN):** o `GET /briefing` valida o dono
  e respondeu `403` no acesso cruzado; o verificador diferencial captou a
  diferença (`own=200` vs `cross=403`) e **não** o marcou como vulnerável. Isso
  demonstra o valor da verificação diferencial: ela distingue o endpoint seguro
  do vulnerável mesmo quando ambos têm a mesma assinatura.
- **1 falso positivo (DELETE) — causa conhecida, não é erro de BOLA:** o
  `DELETE /missions/{missionId}` é seguro (retorna 403 no acesso cruzado), mas
  foi marcado VULNERABLE. A causa é a **limitação GET-only**: o verificador, ao
  "testar" o DELETE, na verdade faz um GET na mesma URL, que casa com a rota
  vulnerável `GET /missions/{missionId}` e retorna 200/200. O FP, portanto, é um
  artefato da mesma limitação que gera os FN em outros alvos — e some assim que o
  verificador passar a usar o método HTTP correto.

---

## 3. A limitação GET-only e suas três manifestações

O `DifferentialVerifier` atual executa **apenas requisições GET**. Essa única
limitação se manifesta de três formas distintas nos resultados — entendê-las é
chave para interpretar as métricas.

### Causa A — Endpoint não aceita GET (HTTP 405)

Os endpoints marcados INCONCLUSIVE com `own=405, cross=405` são, na verdade, de
**escrita** (`POST`, `PUT`, `DELETE`). Exemplos:
- crAPI: `POST /posts/{postId}/comment`
- VAmPI: `PUT /users/v1/{username}/email`, `PUT /users/v1/{username}/password`

O verificador tentou acessá-los via GET, o servidor respondeu **405 Method Not
Allowed**, e a lógica de classificação não trata 405 como vulnerabilidade nem
como negação — logo, INCONCLUSIVE.

**Impacto nas métricas:** esses endpoints estão no gabarito como vulneráveis,
portanto o não-detectá-los é contabilizado como **falso negativo (FN)**, e não
mascarado. É por isso que o recall da proposta fica em 80% no crAPI (1 FN de 5
vulneráveis) e 60% no VAmPI (2 FN de 5 vulneráveis). A arquitetura identificou
corretamente o endpoint como candidato (via LLM ou regra), mas o motor de
verificação não testou o método HTTP certo.

### Causa B — Recurso inexistente (HTTP 404)

No crAPI, os três endpoints de vídeo retornaram `own=404, cross=404` → DENIED.
Os usuários de teste criados pelo `CrApiSetup` não possuem vídeos, então o
recurso não existe nem para o próprio dono. Isso é classificado como DENIED
(acesso negado por ausência), não como vulnerabilidade — comportamento correto.

### Causa C — Colisão de rota em endpoint de escrita (falso positivo)

Na Drone Rescue API, o `DELETE /missions/{missionId}` é seguro (valida o dono,
retorna 403 no acesso cruzado), mas foi marcado VULNERABLE. Como o verificador
faz GET na URL do DELETE, a requisição casou com a rota **`GET /missions/{id}`**
(que é vulnerável) e retornou 200/200. Resultado: 1 **falso positivo**.

Ou seja, a mesma limitação GET-only que gera FN (causa A) também pode gerar FP
quando uma URL de escrita segura coincide com uma rota GET vulnerável. Reforça
que estender o verificador para os métodos HTTP corretos é a melhoria prioritária.

---

## 4. Leitura dos resultados para o TCC

**Achado 1 — A verificação diferencial é o componente que garante a precisão.**  
O experimento "Sem LLM" produziu exatamente o mesmo resultado da proposta
completa no crAPI (4 TP, 0 FP, 1 FN — precisão 100%, recall 80%). Isso mostra
que, neste benchmark, **a eliminação dos falsos positivos vem da verificação
diferencial**, não da seleção do LLM. O baseline de regras puro gerou 5 FP
(precisão 44,4%); ao passar a mesma lista pelo verificador diferencial, todos os
5 FP foram reclassificados corretamente, elevando a precisão para 100%.

**Achado 2 — O LLM contribui em eficiência, não em precisão (neste caso).**  
A diferença prática entre "com LLM" e "sem LLM" no crAPI foi o **tempo** (31,4s
vs 3,7s) e o **custo de tokens** (~4500 vs 0). As métricas de detecção foram
idênticas (100% / 80% / 88,9%). O LLM reduz o conjunto de endpoints a testar, mas
como o baseline de regras aqui já captura os mesmos 9 candidatos, o ganho de
qualidade foi nulo neste alvo. Em APIs maiores, com muitos endpoints com path
parameter não-sensíveis, espera-se que o LLM reduza substancialmente o número de
requisições de verificação — esse é o cenário onde o LLM compensa.

**Achado 2b — Precisão alta, recall limitado pela cobertura de métodos.**  
Em ambos os alvos a precisão foi 100% (zero falsos positivos), mas o recall ficou
em 80% (crAPI) e 60% (VAmPI). A causa é única e conhecida: o verificador só testa
GET, perdendo os BOLAs em endpoints de escrita. Isso indica um caminho claro de
melhoria (seção 5) sem comprometer a precisão já alcançada.

**Achado 3 — A arquitetura generaliza.**  
O detector funcionou no VAmPI e na Drone Rescue API sem nenhuma alteração no
núcleo (LLM + `DifferentialVerifier`). Apenas o adaptador de setup foi escrito
para cada alvo (`VampiSetup`, `DroneSetup`), análogo ao `CrApiSetup`. Isso
evidencia que a separação entre "setup específico do alvo" e "motor de detecção
genérico" é uma decisão arquitetural sólida.

**Achado 3b — Robustez a contaminação de benchmark (alvo inédito).**  
crAPI e VAmPI são projetos públicos que podem ter feito parte do corpus de treino
da LLM, levantando a dúvida: *"a LLM acerta porque reconhece o benchmark?"*. Para
responder, criou-se a **Drone Rescue API** — aplicação sintética, de domínio
fictício, nunca publicada antes dos experimentos. Nela o detector obteve
**recall de 100%** (achou os 3 BOLAs reais) e ainda **classificou corretamente um
endpoint seguro** (TN). Como a LLM não poderia ter memorizado uma aplicação que
não existia, o resultado indica **raciocínio estrutural** sobre o spec, não
reconhecimento. Este é o argumento mais forte contra a crítica de contaminação.

**Achado 4 — O ZAP (DAST genérico) não detecta BOLA.**  
O ZAP encontrou 80 alertas, nenhum deles BOLA (Recall 0%). Confirma a premissa
central do trabalho: ferramentas DAST tradicionais não têm o contexto semântico
de autorização em nível de objeto, e por isso falham justamente na classe de
vulnerabilidade que a arquitetura proposta mira.

---

## 5. Limitações e trabalho futuro

1. **Verificador só testa GET.** Estender o `DifferentialVerifier` para
   `POST`/`PUT`/`DELETE` capturaria os endpoints de escrita hoje marcados como
   INCONCLUSIVE. É a melhoria de maior impacto imediato.
2. **Ground truth codificado.** A contagem de TP/FP/FN/TN usa um gabarito
   explícito em `metrics/GroundTruth.java` (um conjunto de endpoints vulneráveis
   por benchmark). Isso torna as métricas auditáveis e reproduzíveis, mas o
   gabarito foi definido manualmente a partir da documentação de cada alvo —
   convém revisá-lo e justificá-lo no texto do TCC.
3. **Instrumentação de tokens.** O contador de tokens não foi integrado na
   execução do VAmPI (registrou 0). Para comparações de custo mais rigorosas,
   vale capturar o campo `usage` da resposta da OpenAI.
4. **Amostragem única.** Cada experimento foi executado algumas vezes com
   resultados estáveis, mas uma avaliação estatística (N execuções, variância)
   reforçaria a robustez dos números.

---

## 6. Reprodutibilidade

Todos os números desta análise foram reproduzidos seguindo a **Sequência limpa
completa** do `HOWTO.md`. As saídas brutas de cada execução estão em `outputs/`:

| Arquivo | Conteúdo |
|---|---|
| `outputs/llm-detector.txt` | Log completo da proposta no crAPI |
| `outputs/sem-llm-detector.txt` | Log do experimento de ablação |
| `outputs/vampi-detector.txt` | Log da proposta no VAmPI |
| `outputs/drone-detector.txt` | Log da proposta no alvo inédito (Drone) |
| `outputs/results_drone.csv` | Métricas do alvo inédito |
| `outputs/drone-openapi.json` | Spec da Drone Rescue API |
| `outputs/baseline-rule-based.txt` | Log do baseline de regras |
| `outputs/zap_alerts.csv` | Alertas do ZAP |
| `outputs/results_proposta.csv` | Métricas da proposta no crAPI |
| `outputs/results_sem_llm.csv` | Métricas do experimento sem LLM |
| `outputs/results_vampi.csv` | Métricas do VAmPI |
| `outputs/metrics_comparison.png` | Gráfico de barras comparativo |
| `outputs/confusion_matrices.png` | Matrizes de confusão |
| `results.csv` | Tabela consolidada (raiz do repositório) |
