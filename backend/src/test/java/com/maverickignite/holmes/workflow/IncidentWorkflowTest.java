package com.maverickignite.holmes.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IncidentWorkflowTest {

  @Test
  void confidenceThreshold() {
    assertThat(IncidentWorkflow.confidenceAtLeast("HIGH", "MEDIUM")).isTrue();
    assertThat(IncidentWorkflow.confidenceAtLeast("MEDIUM", "MEDIUM")).isTrue();
    assertThat(IncidentWorkflow.confidenceAtLeast("LOW", "MEDIUM")).isFalse();
    assertThat(IncidentWorkflow.confidenceAtLeast("garbage", "LOW")).isFalse();
    assertThat(IncidentWorkflow.confidenceAtLeast(null, "LOW")).isTrue();
  }
}
