package com.maverickignite.holmes.chat;

import com.maverickignite.holmes.investigation.Investigation;
import com.maverickignite.holmes.investigation.InvestigationKind;
import com.maverickignite.holmes.investigation.InvestigationRepository;
import com.maverickignite.holmes.investigation.InvestigationService;
import com.maverickignite.holmes.investigation.ToolCallRepository;
import com.maverickignite.holmes.workflow.ViewsAccess;
import com.maverickignite.holmes.workflow.Views.InvestigationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * HolmesGPT's "ask" mode: free-form questions answered by the agent with the same toolsets.
 * Answers take a while, so the request returns an id and the UI polls for progress.
 */
@RestController
@RequestMapping("/api")
class ChatController {

  private static final int MAX_CONCURRENT_QUESTIONS = 3;

  private final InvestigationService service;
  private final InvestigationRepository repository;
  private final ToolCallRepository toolCalls;
  private final ExecutorService workers;
  private final ObjectMapper json;
  private final Semaphore permits = new Semaphore(MAX_CONCURRENT_QUESTIONS);

  ChatController(
      InvestigationService service,
      InvestigationRepository repository,
      ToolCallRepository toolCalls,
      ExecutorService holmesWorkers,
      ObjectMapper json) {
    this.service = service;
    this.repository = repository;
    this.toolCalls = toolCalls;
    this.workers = holmesWorkers;
    this.json = json;
  }

  record Question(@NotBlank @Size(max = 4_000) String question) {}

  @PostMapping("/chat")
  ResponseEntity<Map<String, Object>> ask(@Valid @RequestBody Question q) {
    if (!permits.tryAcquire()) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("detail", "Too many questions in progress; try again shortly"));
    }
    try {
      Investigation inv = service.startQuestion(q.question().strip());
      workers.submit(
          () -> {
            try {
              service.answerQuestion(inv);
            } catch (Throwable t) {
              org.slf4j.LoggerFactory.getLogger(ChatController.class)
                  .error("Question #{} failed", inv.getId(), t);
            } finally {
              permits.release();
            }
          });
      return ResponseEntity.accepted().body(Map.of("id", inv.getId()));
    } catch (RuntimeException e) {
      permits.release();
      throw e;
    }
  }

  @GetMapping("/chat")
  List<InvestigationView> history() {
    return repository
        .findByKindOrderByStartedAtDesc(InvestigationKind.CHAT, PageRequest.of(0, 30))
        .stream()
        .map(i -> ViewsAccess.investigation(i, List.of(), json))
        .toList();
  }

  @GetMapping("/investigations/{id}")
  InvestigationView investigation(@PathVariable Long id) {
    Investigation inv = service.get(id);
    return ViewsAccess.investigation(inv, toolCalls.findByInvestigationIdOrderBySequenceAsc(id), json);
  }
}
