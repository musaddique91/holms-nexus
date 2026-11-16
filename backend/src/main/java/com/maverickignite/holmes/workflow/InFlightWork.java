package com.maverickignite.holmes.workflow;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Incidents this instance is working on: bounds concurrency and drives the heartbeat. */
@Component
class InFlightWork {

  private final Set<Long> ids = ConcurrentHashMap.newKeySet();

  boolean start(long id) {
    return ids.add(id);
  }

  void finish(long id) {
    ids.remove(id);
  }

  int size() {
    return ids.size();
  }

  Set<Long> snapshot() {
    return Set.copyOf(ids);
  }
}
