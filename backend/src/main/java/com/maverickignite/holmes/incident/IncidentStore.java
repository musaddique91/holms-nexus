package com.maverickignite.holmes.incident;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concurrency-sensitive writes as single SQL statements, so several Kafka consumers and several
 * Holmes replicas can run side by side without double-counting or double-investigating.
 */
@Repository
@RequiredArgsConstructor
public class IncidentStore {

  private static final String UPSERT =
      """
      INSERT INTO incident (fingerprint, service_name, environment, title, error_type, level,
                            sample_message, sample_stack_trace, logger, host, source, status,
                            severity, occurrence_count, first_seen_at, last_seen_at,
                            status_changed_at, created_at, updated_at)
      VALUES (:fingerprint, :service, :environment, :title, :errorType, :level, :message,
              :stackTrace, :logger, :host, :source, 'NEW', :severity, 1, :seenAt, :seenAt,
              now(), now(), now())
      ON CONFLICT (fingerprint) DO UPDATE SET
        occurrence_count   = incident.occurrence_count + 1,
        last_seen_at       = GREATEST(incident.last_seen_at, EXCLUDED.last_seen_at),
        sample_message     = EXCLUDED.sample_message,
        sample_stack_trace = COALESCE(EXCLUDED.sample_stack_trace, incident.sample_stack_trace),
        host               = COALESCE(EXCLUDED.host, incident.host),
        status             = CASE WHEN incident.status = 'RESOLVED'
                                    OR (incident.status = 'FAILED'
                                        AND incident.status_changed_at < now() - interval '30 minutes')
                                  THEN 'NEW' ELSE incident.status END,
        status_changed_at  = CASE WHEN incident.status = 'RESOLVED'
                                    OR (incident.status = 'FAILED'
                                        AND incident.status_changed_at < now() - interval '30 minutes')
                                  THEN now() ELSE incident.status_changed_at END,
        updated_at         = now()
      RETURNING id, status, (xmax = 0) AS inserted
      """;

  /** Claims NEW incidents for this worker; SKIP LOCKED keeps replicas from taking the same row. */
  private static final String CLAIM =
      """
      UPDATE incident SET status = 'INVESTIGATING', status_changed_at = now(), updated_at = now()
      WHERE id IN (SELECT id FROM incident WHERE status = 'NEW'
                   ORDER BY status_changed_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED)
      RETURNING id
      """;

  private static final String REQUEUE_STALE =
      """
      UPDATE incident SET status = 'NEW', status_changed_at = now(), updated_at = now()
      WHERE status IN ('INVESTIGATING', 'FIXING') AND status_changed_at < :cutoff
      """;

  private static final String TRANSITION_IF =
      """
      UPDATE incident SET status = :to, status_changed_at = now(), updated_at = now()
      WHERE id = :id AND status IN (:from)
      """;

  private static final String HEARTBEAT =
      """
      UPDATE incident SET status_changed_at = now()
      WHERE id IN (:ids) AND status IN ('INVESTIGATING', 'FIXING')
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public record UpsertResult(long id, IncidentStatus status, boolean inserted) {}

  public record NewOccurrence(
      String fingerprint,
      String service,
      String environment,
      String title,
      String errorType,
      String level,
      String message,
      String stackTrace,
      String logger,
      String host,
      String source,
      Severity severity,
      Instant seenAt) {}

  @Transactional
  public UpsertResult recordOccurrence(NewOccurrence o) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("fingerprint", o.fingerprint())
            .addValue("service", o.service())
            .addValue("environment", o.environment())
            .addValue("title", o.title())
            .addValue("errorType", o.errorType())
            .addValue("level", o.level())
            .addValue("message", o.message())
            .addValue("stackTrace", o.stackTrace())
            .addValue("logger", o.logger())
            .addValue("host", o.host())
            .addValue("source", o.source())
            .addValue("severity", o.severity().name())
            .addValue("seenAt", OffsetDateTime.ofInstant(o.seenAt(), ZoneOffset.UTC));
    return jdbc.queryForObject(
        UPSERT,
        params,
        (rs, n) ->
            new UpsertResult(
                rs.getLong("id"),
                IncidentStatus.valueOf(rs.getString("status")),
                rs.getBoolean("inserted")));
  }

  @Transactional
  public List<Long> claimNew(int limit) {
    return jdbc.queryForList(CLAIM, new MapSqlParameterSource("limit", limit), Long.class);
  }

  /**
   * Compare-and-set status change: only applies when the incident is still in one of the
   * expected states, so a worker and an operator (or two workers) cannot overwrite each other.
   */
  @Transactional
  public boolean transitionIf(long id, java.util.Set<IncidentStatus> from, IncidentStatus to) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("to", to.name())
            .addValue("from", from.stream().map(Enum::name).toList());
    return jdbc.update(TRANSITION_IF, params) == 1;
  }

  /** Keeps in-flight incidents from being re-queued as stale while their worker is alive. */
  @Transactional
  public void heartbeat(java.util.Collection<Long> ids) {
    if (!ids.isEmpty()) {
      jdbc.update(HEARTBEAT, new MapSqlParameterSource("ids", ids));
    }
  }

  @Transactional
  public int requeueStale(Instant cutoff) {
    return jdbc.update(
        REQUEUE_STALE,
        new MapSqlParameterSource("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)));
  }
}
