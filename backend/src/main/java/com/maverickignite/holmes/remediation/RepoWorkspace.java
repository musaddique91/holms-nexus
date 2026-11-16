package com.maverickignite.holmes.remediation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;

/** A throw-away shallow clone. Closing it deletes the directory. */
@Slf4j
public final class RepoWorkspace implements AutoCloseable {

  private final Git git;
  private final Path root;
  private final CredentialsProvider credentials;
  private final String baseBranch;

  RepoWorkspace(Git git, Path root, CredentialsProvider credentials, String baseBranch) {
    this.git = git;
    this.root = root;
    this.credentials = credentials;
    this.baseBranch = baseBranch;
  }

  public Path root() {
    return root;
  }

  public String baseBranch() {
    return baseBranch;
  }

  /** Paths (repo-relative) added, changed, removed or untracked since the clone. */
  public Set<String> changedFiles() throws GitAPIException {
    Status s = git.status().call();
    Set<String> files = new TreeSet<>();
    files.addAll(s.getModified());
    files.addAll(s.getChanged());
    files.addAll(s.getAdded());
    files.addAll(s.getUntracked());
    files.addAll(s.getRemoved());
    files.addAll(s.getMissing());
    return files;
  }

  /** Stages everything and returns the unified diff against HEAD. */
  public String stageAll() throws GitAPIException {
    git.add().addFilepattern(".").call();
    git.add().addFilepattern(".").setUpdate(true).call();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    git.diff().setCached(true).setOutputStream(out).call();
    return out.toString(StandardCharsets.UTF_8);
  }

  public void commitAndPush(String branch, String message, PersonIdent author)
      throws GitAPIException {
    git.checkout().setCreateBranch(true).setName(branch).call();
    git.commit().setMessage(message).setAuthor(author).setCommitter(author).call();
    Iterable<PushResult> results =
        git.push()
            .setRemote("origin")
            .setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch))
            .setCredentialsProvider(credentials)
            .call();
    for (PushResult result : results) {
      for (RemoteRefUpdate update : result.getRemoteUpdates()) {
        if (update.getStatus() != RemoteRefUpdate.Status.OK
            && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
          throw new IllegalStateException(
              "Push of " + branch + " rejected: " + update.getStatus() + " " + update.getMessage());
        }
      }
    }
  }

  @Override
  public void close() {
    git.close();
    try (Stream<Path> walk = Files.walk(root)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path p : paths) {
        Files.deleteIfExists(p);
      }
    } catch (IOException e) {
      log.warn("Could not clean workspace {}: {}", root, e.getMessage());
    }
  }
}
