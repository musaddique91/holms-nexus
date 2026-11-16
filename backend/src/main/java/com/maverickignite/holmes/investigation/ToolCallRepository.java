package com.maverickignite.holmes.investigation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolCallRepository extends JpaRepository<ToolCallRecord, Long> {

  List<ToolCallRecord> findByInvestigationIdOrderBySequenceAsc(Long investigationId);

  List<ToolCallRecord> findByRemediationIdOrderBySequenceAsc(Long remediationId);
}
