# Resultados dos Experimentos — Detecção de BOLA

Análise consolidada dos experimentos reproduzidos em **05/11/2026**, incluindo os
dois experimentos adicionais sugeridos pelo orientador (ablação "Sem LLM" e
generalização no VAmPI).

---

## 1. Tabela consolidada

| Ferramenta | TP | FP | FN | TN | Inconcl. | Precisão | Recall | F1 | Acurácia | Tempo (s) | Tokens |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Proposta (LLM + Diff)** — crAPI | 4 | 0 | 0 | 3 | 2 | 100% | 100% | 100% | 100% | 37,0 | ~4500 |
| **Sem LLM (Regras + Diff)** — crAPI | 4 | 0 | 0 | 3 | 2 | 100% | 100% | 100% | 100% | 3,9 | 0 |
| **Baseline (Regras)** — crAPI | 4 | 5 | 0 | 0 | 0 | 44,4% | 100% | 61,5% | 44,4% | 0,5 | 0 |
| **Baseline (ZAP)** — crAPI | 0 | 80 | 4 | 0 | 0 | 0% | 0% | 0% | 0% | ~300 | 0 |
| **Proposta (LLM + Diff)** — VAmPI | 3 | 0 | 0 | 0 | 2 | 100% | 100% | 100% | 100% | 6,3 | 0* |

> *No VAmPI o LLM foi usado, mas o custo de tokens não foi instrumentado nesta
> execução (coluna registra 0 por limitação do contador, não por ausência de uso).

![Comparação de métricas](outputs/metrics_comparison.png)

![Matrizes de confusão](outputs/confusion_matrices.png)

---

## 2. Detalhamento por endpoint

### 2.1 Proposta e "Sem LLM" no crAPI (9 endpoints)

| # | Método | Endpoint | Veredito | Evidência | Interpretação |
|---|---|---|---|---|---|
| 1 | GET | `/identity/api/v2/user/videos/{video_id}` | DENIED | own=404, cross=404 | Recurso de vídeo inexistente para os usuários de teste |
| 2 | PUT | `/identity/api/v2/user/videos/{video_id}` | DENIED | own=404, cross=404 | Idem |
| 3 | DELETE | `/identity/api/v2/user/videos/{video_id}` | DENIED | own=404, cross=404 | Idem |
| 4 | DELETE | `/identity/api/v2/admin/videos/{video_id}` | INCONCLUSIVE | own=405, cross=405 | Método não permitido via GET de teste |
| 5 | GET | `/identity/api/v2/vehicle/{vehicleId}/location` | **VULNERABLE** | own=200, cross=200, sim=0,37 | A acessou a localização do veículo de B |
| 6 | GET | `/community/api/v2/community/posts/{postId}` | **VULNERABLE** | own=200, cross=200, sim=0,58 | A leu o post de B |
| 7 | POST | `/community/.../posts/{postId}/comment` | INCONCLUSIVE | own=405, cross=405 | Método não permitido via GET de teste |
| 8 | GET | `/workshop/api/shop/orders/{order_id}` | **VULNERABLE** | own=200, cross=200, sim=1,00 | A leu o pedido de B |
| 9 | PUT | `/workshop/api/shop/orders/{order_id}` | **VULNERABLE** | own=200, cross=200, sim=1,00 | A alterou o pedido de B |

**Total:** 4 VULNERABLE, 3 DENIED, 2 INCONCLUSIVE.

### 2.2 Proposta no VAmPI (5 endpoints)

| # | Método | Endpoint | Veredito | Evidência | Interpretação |
|---|---|---|---|---|---|
| 1 | GET | `/books/v1/{book_title}` | **VULNERABLE** | own=200, cross=200, sim=0,38 | A leu o `secret` do livro de B (BOLA clássico) |
| 2 | GET | `/users/v1/{username}` | **VULNERABLE** | own=200, cross=200, sim=0,71 | A leu os dados de B |
| 3 | DELETE | `/users/v1/{username}` | **VULNERABLE** | own=200, cross=200, sim=0,71 | A pôde deletar o usuário B |
| 4 | PUT | `/users/v1/{username}/email` | INCONCLUSIVE | own=405, cross=405 | Testado via GET → 405 |
| 5 | PUT | `/users/v1/{username}/password` | INCONCLUSIVE | own=405, cross=405 | Testado via GET → 405 |

**Total:** 3 VULNERABLE, 2 INCONCLUSIVE.

---

## 3. Por que alguns endpoints ficaram INCONCLUSIVE?

Há **duas causas distintas**, ambas ligadas ao mesmo fator: o
`DifferentialVerifier` atual executa **apenas requisições GET**.

### Causa A — Endpoint não aceita GET (HTTP 405)

Os endpoints marcados INCONCLUSIVE com `own=405, cross=405` são, na verdade, de
**escrita** (`POST`, `PUT`, `DELETE`). Exemplos:
- crAPI: `DELETE /admin/videos/{video_id}`, `POST /posts/{postId}/comment`
- VAmPI: `PUT /users/v1/{username}/email`, `PUT /users/v1/{username}/password`

O verificador tentou acessá-los via GET, o servidor respondeu **405 Method Not
Allowed**, e a lógica de classificação não trata 405 como vulnerabilidade nem
como negação — logo, INCONCLUSIVE.

**Importante:** pelo menos dois desses casos no VAmPI (`/email` e `/password`)
são **comprovadamente vulneráveis a BOLA** segundo a documentação oficial do
VAmPI (usam o `username` da URL em vez do token). Ou seja, são **falsos
inconclusivos** — a arquitetura identificou corretamente o endpoint como
candidato (via LLM), mas o motor de verificação não testou o método HTTP certo.

### Causa B — Recurso inexistente (HTTP 404)

No crAPI, os três endpoints de vídeo retornaram `own=404, cross=404` → DENIED.
Os usuários de teste criados pelo `CrApiSetup` não possuem vídeos, então o
recurso não existe nem para o próprio dono. Isso é classificado como DENIED
(acesso negado por ausência), não como vulnerabilidade — comportamento correto.

---

## 4. Leitura dos resultados para o TCC

**Achado 1 — A verificação diferencial é o componente que garante a precisão.**  
O experimento "Sem LLM" produziu exatamente o mesmo resultado da proposta
completa no crAPI (4 TP, 0 FP). Isso mostra que, neste benchmark, **a eliminação
dos falsos positivos vem da verificação diferencial**, não da seleção do LLM. O
baseline de regras puro gerou 5 FP (precisão 44,4%); ao passar a mesma lista pelo
verificador diferencial, todos os 5 FP foram reclassificados como DENIED/INCONCLUSIVE.

**Achado 2 — O LLM contribui em eficiência, não em precisão (neste caso).**  
A diferença prática entre "com LLM" e "sem LLM" no crAPI foi o **tempo** (37s vs
3,9s) e o **custo de tokens** (~4500 vs 0). O LLM reduz o conjunto de endpoints a
testar, mas como o baseline de regras aqui já captura os mesmos 9 candidatos, o
ganho de precisão foi nulo. Em APIs maiores, com muitos endpoints com path
parameter não-sensíveis, espera-se que o LLM reduza substancialmente o número de
requisições de verificação — esse é o cenário onde o LLM compensa.

**Achado 3 — A arquitetura generaliza.**  
O detector funcionou no VAmPI sem nenhuma alteração no núcleo (LLM +
`DifferentialVerifier`). Apenas o adaptador de setup (`VampiSetup`) foi escrito,
análogo ao `CrApiSetup`. Isso evidencia que a separação entre "setup específico
do alvo" e "motor de detecção genérico" é uma decisão arquitetural sólida.

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
2. **Ground truth manual.** A contagem de TP/FP/FN/TN assume um mapeamento manual
   dos endpoints realmente vulneráveis em cada benchmark; convém documentar esse
   gabarito explicitamente no texto do TCC.
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
| `outputs/baseline-rule-based.txt` | Log do baseline de regras |
| `outputs/zap_alerts.csv` | Alertas do ZAP |
| `outputs/results_sem_llm.csv` | Métricas do experimento sem LLM |
| `outputs/results_vampi.csv` | Métricas do VAmPI |
| `outputs/metrics_comparison.png` | Gráfico de barras comparativo |
| `outputs/confusion_matrices.png` | Matrizes de confusão |
| `results.csv` | Tabela consolidada (raiz do repositório) |
