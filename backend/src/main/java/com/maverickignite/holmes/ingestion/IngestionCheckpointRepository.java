package com.maverickignite.holmes.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

interface IngestionCheckpointRepository extends JpaRepository<IngestionCheckpoint, String> {}
