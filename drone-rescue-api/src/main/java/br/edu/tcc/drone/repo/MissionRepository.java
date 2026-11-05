package br.edu.tcc.drone.repo;

import br.edu.tcc.drone.model.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRepository extends JpaRepository<Mission, Long> {
}
