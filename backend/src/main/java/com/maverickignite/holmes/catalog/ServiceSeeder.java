package com.maverickignite.holmes.catalog;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates {@code holmes.seed-services} on first boot. Existing rows are never overwritten. */
@Slf4j
@Component
@RequiredArgsConstructor
class ServiceSeeder implements ApplicationRunner {

  private final HolmesProperties properties;
  private final ServiceCatalog catalog;

  @Override
  public void run(ApplicationArguments args) {
    for (HolmesProperties.SeedService seed : properties.getSeedServices()) {
      if (Texts.isBlank(seed.getName())) {
        continue;
      }
      MonitoredService s = new MonitoredService();
      s.setName(seed.getName());
      s.setDisplayName(seed.getDisplayName());
      s.setAliases(Texts.joinCsv(seed.getAliases()));
      s.setEnvironment(seed.getEnvironment());
      s.setRepoUrl(seed.getRepoUrl());
      s.setGitProvider(
          Texts.isBlank(seed.getRepoUrl())
              ? GitProviderType.NONE
              : GitProviderType.valueOf(seed.getGitProvider().toUpperCase(Locale.ROOT)));
      s.setDefaultBranch(seed.getDefaultBranch());
      s.setCodePath(seed.getCodePath());
      s.setRunbook(seed.getRunbook());
      s.setAutoFix(seed.isAutoFix());
      catalog.saveIfAbsent(s);
    }
    log.info("Service catalog ready ({} services)", catalog.findAll().size());
  }
}
