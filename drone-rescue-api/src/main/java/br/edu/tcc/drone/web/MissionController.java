package br.edu.tcc.drone.web;

import br.edu.tcc.drone.auth.TokenStore;
import br.edu.tcc.drone.model.Mission;
import br.edu.tcc.drone.model.Pilot;
import br.edu.tcc.drone.repo.MissionRepository;
import br.edu.tcc.drone.repo.PilotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Endpoints de missões e perfis — mix CONTROLADO de BOLA.
 *
 * GABARITO (ground truth) — conhecido por construção:
 *
 *   VULNERÁVEL (não valida o dono):
 *     GET    /api/v1/missions/{missionId}           → vaza coordinates/briefing alheios
 *     PUT    /api/v1/missions/{missionId}           → altera missão alheia
 *     GET    /api/v1/pilots/{pilotId}/profile       → expõe perfil alheio
 *
 *   SEGURO (valida token.pilot == recurso.owner):
 *     GET    /api/v1/missions/{missionId}/briefing  → 403 se não for o dono
 *     DELETE /api/v1/missions/{missionId}           → 403 se não for o dono
 */
@RestController
@RequestMapping("/api/v1")
public class MissionController {

    private final MissionRepository missions;
    private final PilotRepository pilots;
    private final TokenStore tokens;

    public MissionController(MissionRepository missions, PilotRepository pilots, TokenStore tokens) {
        this.missions = missions;
        this.pilots = pilots;
        this.tokens = tokens;
    }

    // ---------- Criação (não é foco do BOLA, mas necessário para setup) ----------

    @PostMapping("/missions")
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestBody Map<String, String> body) {
        Long pilotId = tokens.resolve(auth);
        if (pilotId == null) return ResponseEntity.status(401).body(Map.of("error", "não autenticado"));

        Mission m = missions.save(new Mission(
            pilotId,
            body.getOrDefault("codename", "sem-nome"),
            body.getOrDefault("coordinates", "0.0,0.0"),
            body.getOrDefault("briefing", "sem briefing")
        ));
        return ResponseEntity.ok(toJson(m));
    }

    // ---------- VULNERÁVEL: lê qualquer missão sem checar o dono ----------

    @GetMapping("/missions/{missionId}")
    public ResponseEntity<?> getMission(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable Long missionId) {
        Long pilotId = tokens.resolve(auth);
        if (pilotId == null) return ResponseEntity.status(401).body(Map.of("error", "não autenticado"));

        Optional<Mission> m = missions.findById(missionId);
        if (m.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "missão não encontrada"));
        // BOLA: retorna sem verificar se pilotId == m.getOwnerId()
        return ResponseEntity.ok(toJson(m.get()));
    }

    // ---------- VULNERÁVEL: altera qualquer missão sem checar o dono ----------

    @PutMapping("/missions/{missionId}")
    public ResponseEntity<?> updateMission(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @PathVariable Long missionId,
                                           @RequestBody Map<String, String> body) {
        Long pilotId = tokens.resolve(auth);
        if (pilotId == null) return ResponseEntity.status(401).body(Map.of("error", "não autenticado"));

        Optional<Mission> opt = missions.findById(missionId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "missão não encontrada"));
        Mission m = opt.get();
        // BOLA: altera sem verificar o dono
        if (body.containsKey("codename"))    m.setCodename(body.get("codename"));
        if (body.containsKey("coordinates")) m.setCoordinates(body.get("coordinates"));
        if (body.containsKey("briefing"))    m.setBriefing(body.get("briefing"));
        missions.save(m);
        return ResponseEntity.ok(toJson(m));
    }

    // ---------- VULNERÁVEL: expõe o perfil de qualquer piloto ----------

    @GetMapping("/pilots/{pilotId}/profile")
    public ResponseEntity<?> getProfile(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable Long pilotId) {
        Long caller = tokens.resolve(auth);
        if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "não autenticado"));

        Optional<Pilot> p = pilots.findById(pilotId);
        if (p.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "piloto não encontrado"));
        // BOLA: expõe perfil alheio sem checar caller == pilotId
        Pilot pilot = p.get();
        return ResponseEntity.ok(Map.of(
            "pilotId", pilot.getId(),
            "callsign", pilot.getCallsign(),
            "squadron", pilot.getSquadron()
        ));
    }

    // ---------- SEGURO: briefing só para o dono ----------

    @GetMapping("/missions/{missionId}/briefing")
    public ResponseEntity<?> getBriefing(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long missionId) {
        Long pilotId = tokens.resolve(auth);
        if (pilotId == null) return ResponseEntity.status(401).body(Map.of("error", "não autenticado"));

        Optional<Mission> opt = missions.findById(missionId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "missão não encontrada"));
        Mission m = opt.get();
        // SEGURO: valida a posse
        if (!m.getOwnerId().equals(pilotId)) {
            return ResponseEntity.status(403).body(Map.of("error", "acesso negado: você não é o dono"));
        }
        return ResponseEntity.ok(Map.of("missionId", m.getId(), "briefing", m.getBriefing()));
    }

    // ---------- SEGURO: delete só para o dono ----------

    @DeleteMapping("/missions/{missionId}")
    public ResponseEntity<?> deleteMission(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @PathVariable Long missionId) {
        Long pilotId = tokens.resolve(auth);
        if (pilotId == null) return ResponseEntity.status(401).body(Map.of("error", "não autenticado"));

        Optional<Mission> opt = missions.findById(missionId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "missão não encontrada"));
        Mission m = opt.get();
        // SEGURO: valida a posse
        if (!m.getOwnerId().equals(pilotId)) {
            return ResponseEntity.status(403).body(Map.of("error", "acesso negado: você não é o dono"));
        }
        missions.delete(m);
        return ResponseEntity.ok(Map.of("message", "missão removida"));
    }

    private Map<String, Object> toJson(Mission m) {
        return Map.of(
            "missionId", m.getId(),
            "ownerId", m.getOwnerId(),
            "codename", m.getCodename(),
            "coordinates", m.getCoordinates(),
            "briefing", m.getBriefing()
        );
    }
}
