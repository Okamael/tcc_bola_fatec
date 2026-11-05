package br.edu.tcc.drone.repo;

import br.edu.tcc.drone.model.Pilot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PilotRepository extends JpaRepository<Pilot, Long> {
    Optional<Pilot> findByCallsign(String callsign);
}
