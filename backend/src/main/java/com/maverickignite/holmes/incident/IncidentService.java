package com.maverickignite.holmes.incident;

import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.common.NotFoundException;
import com.maverickignite.holmes.common.Texts;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentService {

  private final IncidentRepository repository;
  private final IncidentStore store;

  @Transactional(readOnly = true)
  public Incident get(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Incident", id));
  }

  @Transactional(readOnly = true)
  public Page<Incident> search(
      IncidentStatus status, String service, String query, Pageable pageable) {
    Specification<Incident> spec =
        (root, cq, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (!Texts.isBlank(service)) {
            predicates.add(cb.equal(root.get("serviceName"), service));
          }
          if (!Texts.isBlank(query)) {
            String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("errorType")), like)));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    return repository.findAll(spec, pageable);
  }

  @Transactional
  public void transition(Long id, IncidentStatus status) {
    if (repository.updateStatus(id, status, Instant.now()) == 0) {
      throw new NotFoundException("Incident", id);
    }
  }

  /**
   * Operator-driven status change. Workers own the in-flight states, so the update only applies
   * when no worker holds the incident (checked atomically in SQL, not read-then-write).
   */
  public void changeByOperator(Long id, IncidentStatus target) {
    java.util.Set<IncidentStatus> idle = java.util.EnumSet.allOf(IncidentStatus.class);
    idle.removeAll(IncidentStatus.IN_FLIGHT);
    if (!store.transitionIf(id, idle, target)) {
      get(id); // 404 if it does not exist
      throw new BadRequestException("Incident is being processed; try again when it finishes");
    }
  }

  @Transactional
  public void updateSeverity(Long id, Severity severity) {
    repository.updateSeverity(id, severity, Instant.now());
  }
}
