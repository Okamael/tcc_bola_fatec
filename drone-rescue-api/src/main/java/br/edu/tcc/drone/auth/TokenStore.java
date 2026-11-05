package br.edu.tcc.drone.auth;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autenticação mínima por token opaco (em memória).
 * Mapeia token → id do piloto. Suficiente para o experimento de BOLA.
 */
@Component
public class TokenStore {

    private final Map<String, Long> tokenToPilot = new ConcurrentHashMap<>();

    public String issue(Long pilotId) {
        String token = "drt_" + UUID.randomUUID().toString().replace("-", "");
        tokenToPilot.put(token, pilotId);
        return token;
    }

    /** Resolve o id do piloto a partir do header Authorization: Bearer <token>. */
    public Long resolve(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String token = authorizationHeader.startsWith("Bearer ")
            ? authorizationHeader.substring(7)
            : authorizationHeader;
        return tokenToPilot.get(token);
    }
}
