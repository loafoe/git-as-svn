package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.token.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Интерфейс для чтения токенов из потока.
 * <p>
 * http://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
 *
   * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServerParser {
  private static final int DEFAULT_BUFFER_SIZE = 1024;
  @NotNull
  private byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
  @NotNull
  private final InputStream stream;
  private int position;

  public SvnServerParser(@NotNull InputStream stream) {
    this.stream = stream;
  }

  public int readNumber() throws IOException {
    return readToken(NumberToken.class).getNumber();
  }

  @NotNull
  public String readText() throws IOException {
    return readToken(TextToken.class).getText();
  }

  /**
   * Чтение элемента указанного типа из потока.
   *
   * @param tokenType Тип элемента.
   * @param <T>       Тип элемента.
   * @return Прочитанный элемент.
   */
  @NotNull
  public <T extends SvnServerToken> T readToken(@NotNull Class<T> tokenType) throws IOException {
    final SvnServerToken token = readToken();
    if (!tokenType.isInstance(token)) {
      throw new IOException("Unexpected token: " + token + " (expected: " + tokenType.getName() + ')');
    }
    //noinspection unchecked
    return (T) token;
  }

  /**
   * Чтение элемента списка из потока.
   *
   * @param tokenType Тип элемента.
   * @param <T>       Тип элемента.
   * @return Прочитанный элемент.
   */
  @Nullable
  public <T extends SvnServerToken> T readItem(@NotNull Class<T> tokenType) throws IOException {
    final SvnServerToken token = readToken();
    if (ListEndToken.instance.equals(token)) {
      return null;
    }
    if (!tokenType.isInstance(token)) {
      throw new IOException("Unexpected token: " + token + " (expected: " + tokenType.getName() + ')');
    }
    //noinspection unchecked
    return (T) token;
  }

  /**
   * Чтение элемента из потока.
   *
   * @return Возвращает элемент из потока. Если элемента нет - возвращает null.
   */
  @SuppressWarnings("OverlyComplexMethod")
  @Nullable
  public SvnServerToken readToken() throws IOException {
    position = 0;
    int read;
    do {
      read = stream.read();
      // Конец потока.
      if (read < 0) {
        return null;
      }
    } while (isSpace(read));
    if (read == '(') {
      return ListBeginToken.instance;
    }
    if (read == ')') {
      return ListEndToken.instance;
    }
    // Чтение чисел и строк.
    if (isDigit(read)) {
      int number = read - (int) '0';
      while (true) {
        read = stream.read();
        if (!isDigit(read)) {
          break;
        }
        number = number * 10 + (read - (int) '0');
      }
      if (isSpace(read)) {
        return new NumberToken(number);
      }
      if (read == ':') {
        return readString(number);
      }
      throw new IOException("Unexpected character in stream: " + read + " (need ' ', '\\n' or ':')");
    }
    // Обычная строчка.
    if (isAlpha(read)) {
      return readWord(read);
    }
    throw new IOException("Unexpected character in stream: " + read + " (need 'a'..'z', 'A'..'Z', '0'..'9', ' ' or '\n')");
  }

  private static boolean isSpace(int data) {
    return (data == ' ')
        || (data == '\n');
  }

  private static boolean isDigit(int data) {
    return (data >= '0' && data <= '9');
  }

  @NotNull
  private StringToken readString(int length) throws IOException {
    int need = length;
    while (need > 0) {
      // Если буфер мал - увеличиваем.
      if (buffer.length == position) {
        buffer = Arrays.copyOf(buffer, buffer.length * 2);
      }
      // Читаем.
      final int readed = stream.read(buffer, position, Math.min(need, buffer.length - position));
      if (readed < 0) {
        throw new IOException("Unexpected end of stream");
      }
      need -= readed;
    }
    return new StringToken(Arrays.copyOf(buffer, length));
  }

  private static boolean isAlpha(int data) {
    return (data >= 'a' && data <= 'z')
        || (data >= 'A' && data <= 'Z');
  }

  @NotNull
  private WordToken readWord(int first) throws IOException {
    buffer[position] = (byte) first;
    position++;
    while (true) {
      final int read = stream.read();
      if (read < 0) {
        throw new IOException("Unexpected end of stream");
      }
      if (isSpace(read)) {
        return new WordToken(new String(buffer, 0, position, StandardCharsets.US_ASCII));
      }
      if (!(isAlpha(read) || isDigit(read) || (read == '-'))) {
        throw new IOException("Unexpected character in stream: " + read + " (need 'a'..'z', 'A'..'Z', '0'..'9' or '-')");
      }
      buffer[position] = (byte) read;
      position++;
    }
  }

  @NotNull
  public String[] readStringList() throws IOException {
    final List<String> result = new ArrayList<>();
    readToken(ListBeginToken.class);
    while (true) {
      final TextToken token = readItem(TextToken.class);
      if (token == null) {
        break;
      }
      result.add(token.getText());
    }
    return result.toArray(new String[result.size()]);
  }

  public void skipItems() throws IOException {
    int depth = 0;
    while (depth >= 0) {
      final SvnServerToken token = readToken(SvnServerToken.class);
      if (ListBeginToken.instance.equals(token)) {
        depth++;
      }
      if (ListEndToken.instance.equals(token)) {
        depth--;
      }
    }
  }
}
