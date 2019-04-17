/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.LocalContext;
import svnserver.repository.git.GitRepository;

import java.io.IOException;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
public interface RepositoryConfig {
  @NotNull
  GitRepository create(@NotNull LocalContext context) throws IOException, SVNException;
}
