# Drone Rescue API — alvo sintético inédito

API vulnerável a BOLA criada **especificamente para este TCC**, com o propósito
de validar o detector em um alvo que a LLM **não pode conhecer** (nunca foi
publicado antes dos experimentos). Isso elimina o risco de contaminação de
benchmark que existe com alvos públicos como crAPI e VAmPI.

## Stack

- Java 17 + Spring Boot 3.2.5
- Banco **H2 em memória** (recriado a cada boot — zero setup)
- Porta **7070**

## Domínio (fictício)

"Observatório de Drones de Resgate": pilotos (`pilots`) operam missões de resgate
(`missions`). Cada missão pertence a um piloto. Acessar a missão de outro piloto
sem autorização é o BOLA.

## Gabarito (ground truth) — por construção

| Endpoint | Classe |
|---|---|
| `GET /api/v1/missions/{missionId}` | **VULNERÁVEL** (não valida dono) |
| `PUT /api/v1/missions/{missionId}` | **VULNERÁVEL** (não valida dono) |
| `GET /api/v1/pilots/{pilotId}/profile` | **VULNERÁVEL** (expõe perfil alheio) |
| `GET /api/v1/missions/{missionId}/briefing` | SEGURO (403 se não for o dono) |
| `DELETE /api/v1/missions/{missionId}` | SEGURO (403 se não for o dono) |

## Como rodar

```bash
cd drone-rescue-api
mvn clean package -DskipTests
java -jar target/drone-rescue-api-1.0.0.jar
```

Verificar:

```bash
curl http://localhost:7070/openapi.json
```

## Endpoints de autenticação

```bash
# registrar
curl -X POST http://localhost:7070/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"callsign":"falcon-07","password":"segredo","squadron":"alpha"}'

# login (retorna token + pilotId)
curl -X POST http://localhost:7070/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"callsign":"falcon-07","password":"segredo"}'
```

O detector consome esta API via `br.edu.tcc.bola.MainDrone` (no projeto
`tcc-bola-detector`). Ver `HOWTO.md` na raiz do repositório.
