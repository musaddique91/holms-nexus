package com.maverickignite.holmes.catalog;

import com.maverickignite.holmes.catalog.ServiceDtos.ServiceRequest;
import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.common.NotFoundException;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.scm.GitTokens;
import com.maverickignite.holmes.settings.SecretCipher;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registry of monitored services. Resolving a log's service name (often a container name such as
 * {@code orders-backend-staging}) to its canonical service happens on every ingested event, so
 * the alias index is cached and rebuilt after each write.
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalog {

  private final MonitoredServiceRepository repository;
  private final SecretCipher cipher;
  private final AtomicReference<Map<String, String>> aliasIndex = new AtomicReference<>();

  @Transactional(readOnly = true)
  public List<MonitoredService> findAll() {
    return repository.findAllByOrderByNameAsc();
  }

  @Transactional(readOnly = true)
  public MonitoredService get(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Service", id));
  }

  @Transactional(readOnly = true)
  public Optional<MonitoredService> findByName(String name) {
    return name == null ? Optional.empty() : repository.findByName(name);
  }

  /** Canonical service name for a raw name seen in logs; unknown names pass through unchanged. */
  public String canonicalName(String raw) {
    if (Texts.isBlank(raw)) {
      return "unknown";
    }
    String trimmed = raw.trim();
    return index().getOrDefault(trimmed.toLowerCase(Locale.ROOT), trimmed);
  }

  @Transactional
  public MonitoredService create(ServiceRequest request) {
    if (repository.existsByName(request.name())) {
      throw new BadRequestException("Service '" + request.name() + "' already exists");
    }
    MonitoredService service = new MonitoredService();
    apply(service, request);
    MonitoredService saved = repository.save(service);
    invalidate();
    return saved;
  }

  @Transactional
  public MonitoredService update(Long id, ServiceRequest request) {
    MonitoredService service = get(id);
    if (!service.getName().equals(request.name()) && repository.existsByName(request.name())) {
      throw new BadRequestException("Service '" + request.name() + "' already exists");
    }
    apply(service, request);
    invalidate();
    return service;
  }

  @Transactional
  public void remove(Long id) {
    repository.delete(get(id));
    invalidate();
  }

  @Transactional
  public void saveIfAbsent(MonitoredService service) {
    if (!repository.existsByName(service.getName())) {
      repository.save(service);
      invalidate();
    }
  }

  private void apply(MonitoredService s, ServiceRequest r) {
    s.setName(r.name().trim());
    s.setDisplayName(r.displayName());
    s.setAliases(Texts.joinCsv(r.aliases()));
    s.setEnvironment(r.environment());
    s.setRepoUrl(Texts.isBlank(r.repoUrl()) ? null : r.repoUrl().trim());
    s.setGitProvider(r.gitProvider());
    s.setDefaultBranch(Texts.isBlank(r.defaultBranch()) ? "main" : r.defaultBranch().trim());
    s.setCodePath(r.codePath());
    s.setRunbook(r.runbook());
    s.setAutoFix(r.autoFix());
    s.setSlackChannel(r.slackChannel());
    s.setEmailRecipients(Texts.joinCsv(r.emailRecipients()));
    s.setTelegramChatId(r.telegramChatId());
    if (r.gitToken() != null) {
      String token = r.gitToken().trim();
      s.setGitTokenEncrypted(token.isEmpty() ? null : cipher.encrypt(token));
      s.setGitTokenHint(token.isEmpty() ? null : GitTokens.hint(token));
    }
  }

  private Map<String, String> index() {
    Map<String, String> current = aliasIndex.get();
    if (current != null) {
      return current;
    }
    Map<String, String> built = new HashMap<>();
    for (MonitoredService s : repository.findAll()) {
      built.put(s.getName().toLowerCase(Locale.ROOT), s.getName());
      for (String alias : s.aliasList()) {
        built.put(alias.toLowerCase(Locale.ROOT), s.getName());
      }
    }
    Map<String, String> frozen = Map.copyOf(built);
    aliasIndex.compareAndSet(null, frozen);
    return frozen;
  }

  private void invalidate() {
    aliasIndex.set(null);
    // A reader may rebuild the index from pre-commit data in between; clear it again once the
    // change is visible.
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isSynchronizationActive()) {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .registerSynchronization(
              new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                  aliasIndex.set(null);
                }
              });
    }
  }
}
