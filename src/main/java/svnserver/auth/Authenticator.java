/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

import java.io.IOException;

/**
 * Single authentication mechanism.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface Authenticator {

  @NotNull
  String getMethodName();

  @Nullable
  User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull String token) throws IOException, SVNException;
}
