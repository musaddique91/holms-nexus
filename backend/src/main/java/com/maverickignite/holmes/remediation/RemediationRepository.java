package com.maverickignite.holmes.remediation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationRepository extends JpaRepository<Remediation, Long> {

  List<Remediation> findByIncidentIdOrderByStartedAtDesc(Long incidentId);

  long countByStatus(RemediationStatus status);
}
