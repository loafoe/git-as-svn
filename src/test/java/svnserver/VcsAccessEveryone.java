/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.auth.User;
import svnserver.repository.VcsAccess;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class VcsAccessEveryone implements VcsAccess {
  @Override
  public void checkRead(@NotNull User user, @Nullable String path) {
  }

  @Override
  public void checkWrite(@NotNull User user, @Nullable String path) {
  }
}
