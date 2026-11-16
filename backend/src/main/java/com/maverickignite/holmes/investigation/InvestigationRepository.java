package com.maverickignite.holmes.investigation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRepository extends JpaRepository<Investigation, Long> {

  List<Investigation> findByIncidentIdOrderByStartedAtDesc(Long incidentId);

  Optional<Investigation> findFirstByIncidentIdAndStatusOrderByStartedAtDesc(
      Long incidentId, InvestigationStatus status);

  long countByKindAndStartedAtAfter(InvestigationKind kind, Instant since);

  List<Investigation> findByKindOrderByStartedAtDesc(InvestigationKind kind, Pageable pageable);
}
