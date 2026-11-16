package com.maverickignite.holmes.toolset;

import java.util.List;

/**
 * A group of read-only diagnostic tools against one kind of system (Kubernetes, Postgres, Kafka,
 * ...). Like HolmesGPT toolsets, a toolset is offered to the model only when it is enabled and
 * configured.
 */
public interface Toolset {

  String name();

  String description();

  /** Enabled in configuration (says nothing about reachability). */
  boolean enabled();

  List<ToolSpec> tools();

  /** Cheap reachability probe used by the Integrations page. */
  ToolsetHealth health();

  record ToolsetHealth(boolean healthy, String message) {

    public static ToolsetHealth ok(String message) {
      return new ToolsetHealth(true, message);
    }

    public static ToolsetHealth failed(String message) {
      return new ToolsetHealth(false, message);
    }
  }
}
