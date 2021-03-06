/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.LocalContext;
import svnserver.repository.git.GitRepository;

import java.io.IOException;

/**
 * GitLab project information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabProject implements AutoCloseable {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitLabProject.class);
  @NotNull
  private final GitRepository repository;
  @NotNull
  private final LocalContext context;
  private final int projectId;

  private volatile boolean ready = false;

  GitLabProject(@NotNull LocalContext context, @NotNull GitRepository repository, int projectId) {
    this.context = context;
    this.repository = repository;
    this.projectId = projectId;
  }

  void initRevisions() throws IOException, SVNException {
    if (!ready) {
      log.info("[{}]: initing...", context.getName());
      repository.updateRevisions();
      ready = true;
    }
  }

  @NotNull
  public LocalContext getContext() {
    return context;
  }

  int getProjectId() {
    return projectId;
  }

  @NotNull
  public GitRepository getRepository() {
    return repository;
  }

  boolean isReady() {
    return ready;
  }

  @Override
  public void close() {
    try {
      context.close();
    } catch (Exception e) {
      log.error("Can't close context for repository: " + context.getName(), e);
    }
  }
}
