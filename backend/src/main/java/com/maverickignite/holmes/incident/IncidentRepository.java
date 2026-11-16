package com.maverickignite.holmes.incident;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository
    extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

  @Modifying
  @Query(
      "update Incident i set i.status = :status, i.statusChangedAt = :now, i.updatedAt = :now"
          + " where i.id = :id")
  int updateStatus(
      @Param("id") Long id, @Param("status") IncidentStatus status, @Param("now") Instant now);

  @Modifying
  @Query("update Incident i set i.severity = :severity, i.updatedAt = :now where i.id = :id")
  int updateSeverity(
      @Param("id") Long id, @Param("severity") Severity severity, @Param("now") Instant now);

  @Query("select i.status, count(i) from Incident i group by i.status")
  List<Object[]> countByStatus();

  @Query(
      "select i.serviceName, count(i), sum(i.occurrenceCount) from Incident i"
          + " where i.lastSeenAt >= :since group by i.serviceName order by count(i) desc")
  List<Object[]> countByServiceSince(@Param("since") Instant since);

  long countByFirstSeenAtAfter(Instant since);
}
