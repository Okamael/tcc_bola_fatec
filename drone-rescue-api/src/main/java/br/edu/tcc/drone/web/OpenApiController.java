package br.edu.tcc.drone.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Serve o spec OpenAPI estático em /openapi.json para o detector consumir. */
@RestController
public class OpenApiController {

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> openapi() throws Exception {
        var res = new ClassPathResource("openapi-drone.json");
        String json = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(json);
    }
}
