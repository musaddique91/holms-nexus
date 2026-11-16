package com.maverickignite.holmes.workflow;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.incident.IncidentStore;
import com.maverickignite.holmes.investigation.InvestigationKind;
import com.maverickignite.holmes.investigation.InvestigationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Pulls NEW incidents from the database and starts investigations, bounded by a concurrency
 * limit and an hourly budget. Driving work from the database (instead of from the Kafka consumer)
 * makes it restart-safe and lets several Holmes replicas share the load.
 */
@Slf4j
@Component
class InvestigationScheduler implements SchedulingConfigurer {

  private final IncidentStore store;
  private final InvestigationRepository investigations;
  private final IncidentWorkflow workflow;
  private final ExecutorService workers;
  private final HolmesProperties.Investigation config;
  private final InFlightWork inFlight;

  InvestigationScheduler(
      IncidentStore store,
      InvestigationRepository investigations,
      IncidentWorkflow workflow,
      ExecutorService holmesWorkers,
      HolmesProperties properties,
      InFlightWork inFlight) {
    this.inFlight = inFlight;
    this.store = store;
    this.investigations = investigations;
    this.workflow = workflow;
    this.workers = holmesWorkers;
    this.config = properties.getInvestigation();
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.addFixedDelayTask(this::tick, config.getPollInterval());
  }

  void tick() {
    try {
      store.heartbeat(inFlight.snapshot());
      int requeued = store.requeueStale(Instant.now().minus(config.getStaleAfter()));
      if (requeued > 0) {
        log.warn("Re-queued {} incidents left in progress by a previous run", requeued);
      }
      if (!config.isEnabled()) {
        return;
      }
      int free = config.getConcurrency() - inFlight.size();
      long startedLastHour =
          investigations.countByKindAndStartedAtAfter(
              InvestigationKind.INCIDENT, Instant.now().minus(Duration.ofHours(1)));
      int budget = (int) Math.min(free, config.getMaxPerHour() - startedLastHour);
      if (budget <= 0) {
        return;
      }
      List<Long> claimed = store.claimNew(budget);
      for (Long id : claimed) {
        inFlight.start(id);
        workers.submit(
            () -> {
              try {
                workflow.process(id);
              } catch (Throwable t) {
                log.error("Worker for incident #{} died", id, t);
              } finally {
                inFlight.finish(id);
              }
            });
      }
    } catch (RuntimeException e) {
      log.warn("Investigation scheduler tick failed: {}", e.getMessage());
    }
  }
}
