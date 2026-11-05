package br.edu.tcc.drone.model;

import jakarta.persistence.*;

/** Piloto de drone — equivale ao "usuário" autenticável da API. */
@Entity
@Table(name = "pilots")
public class Pilot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String callsign;   // identificador de login (ex.: "falcon-07")

    @Column(nullable = false)
    private String password;

    private String squadron;   // esquadrão (dado de perfil)

    public Pilot() {}

    public Pilot(String callsign, String password, String squadron) {
        this.callsign = callsign;
        this.password = password;
        this.squadron = squadron;
    }

    public Long getId() { return id; }
    public String getCallsign() { return callsign; }
    public String getPassword() { return password; }
    public String getSquadron() { return squadron; }

    public void setId(Long id) { this.id = id; }
    public void setCallsign(String callsign) { this.callsign = callsign; }
    public void setPassword(String password) { this.password = password; }
    public void setSquadron(String squadron) { this.squadron = squadron; }
}
