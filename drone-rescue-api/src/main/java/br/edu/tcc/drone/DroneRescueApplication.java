package br.edu.tcc.drone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API sintética "Observatório de Drones de Resgate".
 *
 * Alvo inédito criado especificamente para este TCC: por ser um domínio
 * fictício e nunca publicado antes dos experimentos, garante que a LLM não
 * conhece a aplicação previamente — eliminando o risco de contaminação de
 * benchmark (a LLM precisa RACIOCINAR sobre a estrutura, não memorizar).
 *
 * Contém um mix controlado de endpoints vulneráveis e seguros a BOLA, de modo
 * que o gabarito (ground truth) é 100% conhecido por construção.
 */
@SpringBootApplication
public class DroneRescueApplication {
    public static void main(String[] args) {
        SpringApplication.run(DroneRescueApplication.class, args);
    }
}
