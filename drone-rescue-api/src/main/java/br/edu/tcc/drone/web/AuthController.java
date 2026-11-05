package br.edu.tcc.drone.web;

import br.edu.tcc.drone.auth.TokenStore;
import br.edu.tcc.drone.model.Pilot;
import br.edu.tcc.drone.repo.PilotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Autenticação de pilotos. Não envolve BOLA — apenas provê os tokens
 * necessários para os testes diferenciais.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final PilotRepository pilots;
    private final TokenStore tokens;

    public AuthController(PilotRepository pilots, TokenStore tokens) {
        this.pilots = pilots;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String callsign = body.get("callsign");
        String password = body.get("password");
        if (callsign == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "callsign e password obrigatórios"));
        }
        if (pilots.findByCallsign(callsign).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "callsign já existe"));
        }
        Pilot p = pilots.save(new Pilot(callsign, password, body.getOrDefault("squadron", "independente")));
        return ResponseEntity.ok(Map.of("message", "piloto registrado", "pilotId", p.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String callsign = body.get("callsign");
        String password = body.get("password");
        return pilots.findByCallsign(callsign)
            .filter(p -> p.getPassword().equals(password))
            .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of(
                "token", tokens.issue(p.getId()),
                "pilotId", p.getId())))
            .orElse(ResponseEntity.status(401).body(Map.of("error", "credenciais inválidas")));
    }
}
