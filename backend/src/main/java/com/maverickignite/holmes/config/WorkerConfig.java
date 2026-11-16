package com.maverickignite.holmes.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WorkerConfig {

  /** Runs agent work (investigations, fixes, questions). Concurrency is bounded by callers. */
  @Bean(destroyMethod = "shutdownNow")
  ExecutorService holmesWorkers() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
