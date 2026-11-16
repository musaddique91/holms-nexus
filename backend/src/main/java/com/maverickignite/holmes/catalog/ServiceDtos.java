package com.maverickignite.holmes.catalog;

import com.maverickignite.holmes.common.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ServiceDtos {

  private ServiceDtos() {}

  public record ServiceRequest(
      @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._-]+") String name,
      @Size(max = 200) String displayName,
      List<String> aliases,
      @Size(max = 60) String environment,
      @Size(max = 500) String repoUrl,
      @NotNull GitProviderType gitProvider,
      @Size(max = 120) String defaultBranch,
      @Size(max = 300) String codePath,
      String runbook,
      boolean autoFix,
      @Size(max = 120) String slackChannel,
      List<String> emailRecipients,
      @Size(max = 60) String telegramChatId,
      /* Git personal access token: null = keep the saved one, "" = remove it, else replace. */
      @Size(max = 500) String gitToken) {}

  public record ServiceResponse(
      Long id,
      String name,
      String displayName,
      List<String> aliases,
      String environment,
      String repoUrl,
      GitProviderType gitProvider,
      String defaultBranch,
      String codePath,
      String runbook,
      boolean autoFix,
      String slackChannel,
      List<String> emailRecipients,
      String telegramChatId,
      boolean gitTokenSet,
      String gitTokenHint,
      Instant createdAt,
      Instant updatedAt) {

    static ServiceResponse from(MonitoredService s) {
      return new ServiceResponse(
          s.getId(),
          s.getName(),
          s.getDisplayName(),
          s.aliasList(),
          s.getEnvironment(),
          s.getRepoUrl(),
          s.getGitProvider(),
          s.getDefaultBranch(),
          s.getCodePath(),
          s.getRunbook(),
          s.isAutoFix(),
          s.getSlackChannel(),
          Texts.splitCsv(s.getEmailRecipients()),
          s.getTelegramChatId(),
          s.getGitTokenEncrypted() != null,
          s.getGitTokenHint(),
          s.getCreatedAt(),
          s.getUpdatedAt());
    }
  }
}
