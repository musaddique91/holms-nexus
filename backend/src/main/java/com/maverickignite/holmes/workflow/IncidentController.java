package com.maverickignite.holmes.workflow;

import com.maverickignite.holmes.incident.Incident;
import com.maverickignite.holmes.incident.IncidentService;
import com.maverickignite.holmes.incident.IncidentStatus;
import com.maverickignite.holmes.investigation.InvestigationRepository;
import com.maverickignite.holmes.investigation.ToolCallRepository;
import com.maverickignite.holmes.remediation.RemediationRepository;
import com.maverickignite.holmes.workflow.Views.IncidentDetail;
import com.maverickignite.holmes.workflow.Views.IncidentView;
import com.maverickignite.holmes.workflow.Views.InvestigationView;
import com.maverickignite.holmes.workflow.Views.PageView;
import com.maverickignite.holmes.workflow.Views.RemediationView;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
class IncidentController {

  private final IncidentService incidents;
  private final IncidentWorkflow workflow;
  private final InvestigationRepository investigations;
  private final RemediationRepository remediations;
  private final ToolCallRepository toolCalls;
  private final ObjectMapper json;

  @GetMapping
  PageView<IncidentView> list(
      @RequestParam(required = false) IncidentStatus status,
      @RequestParam(required = false) String service,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    PageRequest pageable =
        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "lastSeenAt"));
    return PageView.of(incidents.search(status, service, q, pageable), i -> IncidentView.of(i, false));
  }

  @GetMapping("/{id}")
  IncidentDetail get(@PathVariable Long id) {
    Incident incident = incidents.get(id);
    return new IncidentDetail(
        IncidentView.of(incident, true),
        investigations.findByIncidentIdOrderByStartedAtDesc(id).stream()
            .map(i -> InvestigationView.of(i, toolCalls.findByInvestigationIdOrderBySequenceAsc(i.getId()), json))
            .toList(),
        remediations.findByIncidentIdOrderByStartedAtDesc(id).stream()
            .map(r -> RemediationView.of(r, toolCalls.findByRemediationIdOrderBySequenceAsc(r.getId())))
            .toList());
  }

  @PostMapping("/{id}/reinvestigate")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> reinvestigate(@PathVariable Long id) {
    incidents.changeByOperator(id, IncidentStatus.NEW);
    return Map.of("status", IncidentStatus.NEW.name());
  }

  @PostMapping("/{id}/fix")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> fix(@PathVariable Long id) {
    workflow.requestFix(id);
    return Map.of("status", IncidentStatus.FIXING.name());
  }

  @PostMapping("/{id}/resolve")
  Map<String, String> resolve(@PathVariable Long id) {
    incidents.changeByOperator(id, IncidentStatus.RESOLVED);
    return Map.of("status", IncidentStatus.RESOLVED.name());
  }

  @PostMapping("/{id}/ignore")
  Map<String, String> ignore(@PathVariable Long id) {
    incidents.changeByOperator(id, IncidentStatus.IGNORED);
    return Map.of("status", IncidentStatus.IGNORED.name());
  }
}
