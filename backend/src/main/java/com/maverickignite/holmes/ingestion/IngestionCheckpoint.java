package com.maverickignite.holmes.ingestion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resume position of a pull-based source (OpenObserve poller), in source-native units. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ingestion_checkpoint")
public class IngestionCheckpoint {

  @Id private String name;
  private long position;
  private Instant updatedAt;

  public IngestionCheckpoint(String name, long position) {
    this.name = name;
    this.position = position;
    this.updatedAt = Instant.now();
  }
}
