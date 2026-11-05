package br.edu.tcc.drone.model;

import jakarta.persistence.*;

/**
 * Missão de resgate — o recurso "de posse". Cada missão pertence a um piloto
 * (ownerId). Acessar a missão de outro piloto sem autorização é o BOLA.
 */
@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;      // piloto dono da missão

    private String codename;   // nome público da missão
    private String coordinates; // dado sensível: coordenadas do resgate
    private String briefing;    // dado sensível: instruções confidenciais

    public Mission() {}

    public Mission(Long ownerId, String codename, String coordinates, String briefing) {
        this.ownerId = ownerId;
        this.codename = codename;
        this.coordinates = coordinates;
        this.briefing = briefing;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getCodename() { return codename; }
    public String getCoordinates() { return coordinates; }
    public String getBriefing() { return briefing; }

    public void setId(Long id) { this.id = id; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public void setCodename(String codename) { this.codename = codename; }
    public void setCoordinates(String coordinates) { this.coordinates = coordinates; }
    public void setBriefing(String briefing) { this.briefing = briefing; }
}
