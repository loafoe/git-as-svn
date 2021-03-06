/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.filter;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.pointer.Constants;
import ru.bozaro.gitlfs.pointer.Pointer;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.server.LfsServerEntry;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.repository.SvnForbiddenException;
import svnserver.repository.git.GitObject;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.filter.GitFilterHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Filter for Git LFS.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsFilter implements GitFilter {
  @NotNull
  private static final String NAME = "lfs";
  @Nullable
  private final LfsStorage storage;
  @NotNull
  private final Map<String, String> cacheMd5;

  public LfsFilter(@NotNull LocalContext context) throws IOException, SVNException {
    this.storage = LfsStorageFactory.tryCreateStorage(context);
    this.cacheMd5 = GitFilterHelper.getCacheMd5(this, context.getShared().getCacheDB());
    final LfsServer lfsServer = context.getShared().get(LfsServer.class);
    if (storage != null && lfsServer != null) {
      context.add(LfsServerEntry.class, new LfsServerEntry(lfsServer, context, storage));
    }
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public String getMd5(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    final ObjectLoader loader = objectId.openObject();
    final ObjectStream stream = loader.openStream();
    final byte[] header = new byte[Constants.POINTER_MAX_SIZE];
    int length = IOUtils.read(stream, header, 0, header.length);
    if (length < header.length) {
      final Map<String, String> pointer = Pointer.parsePointer(header, 0, length);
      if (pointer != null) {
        String md5 = getReader(pointer).getMd5();
        if (md5 != null) {
          return md5;
        }
      }
    }
    return GitFilterHelper.getMd5(this, cacheMd5, null, objectId);
  }

  @NotNull
  private LfsReader getReader(@NotNull Map<String, String> pointer) throws IOException {
    final LfsReader reader = getStorage().getReader(pointer.get(Constants.OID));
    if (reader == null) {
      throw new SvnForbiddenException();
    }
    return reader;
  }

  @NotNull
  private LfsStorage getStorage() {
    if (storage == null)
      throw new IllegalStateException("LFS is not configured");

    return storage;
  }

  @Override
  public long getSize(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    final ObjectLoader loader = objectId.openObject();
    final ObjectStream stream = loader.openStream();
    final byte[] header = new byte[Constants.POINTER_MAX_SIZE];
    int length = IOUtils.read(stream, header, 0, header.length);
    if (length < header.length) {
      final Map<String, String> pointer = Pointer.parsePointer(header, 0, length);
      if (pointer != null) {
        return getReader(pointer).getSize();
      }
    }
    return loader.getSize();
  }

  @NotNull
  @Override
  public InputStream inputStream(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    final ObjectLoader loader = objectId.openObject();
    final ObjectStream stream = loader.openStream();
    final byte[] header = new byte[Constants.POINTER_MAX_SIZE];
    int length = IOUtils.read(stream, header, 0, header.length);
    if (length < header.length) {
      final Map<String, String> pointer = Pointer.parsePointer(header, 0, length);
      if (pointer != null) {
        return getReader(pointer).openStream();
      }
    }
    return new TemporaryInputStream(header, length, stream);
  }

  @NotNull
  @Override
  public OutputStream outputStream(@NotNull OutputStream stream, @NotNull User user) throws IOException {
    return new TemporaryOutputStream(getStorage().getWriter(user), stream);
  }

  private static class TemporaryInputStream extends InputStream {
    @NotNull
    private final byte[] header;
    @NotNull
    private final InputStream stream;
    private final int length;
    private int offset = 0;

    private TemporaryInputStream(@NotNull byte[] header, int length, @NotNull InputStream stream) {
      this.header = header;
      this.length = length;
      this.stream = stream;
    }

    @Override
    public int read() throws IOException {
      if (offset < length) {
        //noinspection MagicNumber
        return header[offset++] & 0xff;
      }
      return stream.read();
    }

    @Override
    public int read(@NotNull byte[] buf, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      if (this.offset < length) {
        final int count = Math.min(len, length - this.offset);
        System.arraycopy(header, offset, buf, off, count);
        offset += count;
        return count;
      }
      return stream.read(buf, off, len);
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }

  private static class TemporaryOutputStream extends OutputStream {
    private final LfsWriter writer;
    private final OutputStream stream;
    private long size;

    private TemporaryOutputStream(LfsWriter writer, OutputStream stream) {
      this.writer = writer;
      this.stream = stream;
      size = 0;
    }

    @Override
    public void write(int b) throws IOException {
      writer.write(b);
      size++;
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
      writer.write(b, off, len);
      size += len;
    }

    @Override
    public void flush() throws IOException {
      writer.flush();
    }

    @Override
    public void close() throws IOException {
      final Map<String, String> pointer = Pointer.createPointer(writer.finish(null), size);
      writer.close();
      stream.write(Pointer.serializePointer(pointer));
      stream.close();
    }
  }
}
