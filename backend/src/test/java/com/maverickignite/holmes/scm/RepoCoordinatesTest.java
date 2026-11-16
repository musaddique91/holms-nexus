package com.maverickignite.holmes.scm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepoCoordinatesTest {

  @Test
  void parsesHttpsAndSshUrls() {
    assertThat(RepoCoordinates.parse("https://github.com/acme/inventory-api.git").path())
        .isEqualTo("acme/inventory-api");
    assertThat(RepoCoordinates.parse("https://gitlab.com/group/sub/app").path()).isEqualTo("group/sub/app");
    assertThat(RepoCoordinates.parse("git@github.com:org/repo.git").path()).isEqualTo("org/repo");
  }
}
