/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config.serializer;

import org.atteo.classindex.ClassIndex;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import svnserver.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Helper for parse/serialize configuration files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class ConfigSerializer {
  @NotNull
  private static final String TAG_PREFIX = "!";
  @NotNull
  private final Yaml yaml;

  public ConfigSerializer() {
    final DumperOptions options = new DumperOptions();
    options.setPrettyFlow(true);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    yaml = new Yaml(new ConfigConstructor(), new ConfigRepresenter(), options);
    yaml.setBeanAccess(BeanAccess.FIELD);
  }

  @NotNull
  public String dump(Config config) {
    return yaml.dump(config);
  }

  @NotNull
  private Config load(@NotNull InputStream stream) {
    return yaml.loadAs(stream, Config.class);
  }

  @NotNull
  public Config load(@NotNull File file) throws IOException {
    try (InputStream stream = new FileInputStream(file)) {
      return load(stream);
    }
  }

  @NotNull
  private static Map<String, Class<?>> configTypes() {
    final Map<String, Class<?>> result = new TreeMap<>();
    for (Class<?> type : ClassIndex.getAnnotated(ConfigType.class)) {
      ConfigType annotation = type.getAnnotation(ConfigType.class);
      final String name = annotation.value();
      if (result.put(TAG_PREFIX + name, type) != null) {
        throw new IllegalStateException("Found duplicate type name: " + name);
      }
    }
    return result;
  }

  private static class ConfigConstructor extends Constructor {
    private ConfigConstructor() {
      for (Map.Entry<String, Class<?>> entry : configTypes().entrySet()) {
        addTypeDescription(new TypeDescription(entry.getValue(), entry.getKey()));
      }
    }
  }

  private static class ConfigRepresenter extends Representer {
    private ConfigRepresenter() {
      for (Map.Entry<String, Class<?>> entry : configTypes().entrySet()) {
        addClassTag(entry.getValue(), new Tag(entry.getKey()));
      }
    }
  }
}
