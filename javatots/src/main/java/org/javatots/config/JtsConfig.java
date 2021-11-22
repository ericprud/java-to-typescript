package org.javatots.config;

import lombok.Getter;
import lombok.Setter;
import org.javatots.main.JavaToTypescript;

import java.util.Map;
import java.util.Optional;

/**
 * Java-to-typescript (javatots) configuration; probably loaded from YAML.
 */
@Getter
@Setter
public class JtsConfig {
    public String inputDirectory;
    public String outputDirectory;
    public Map<String, ModuleMap> moduleMaps;
    public String packageTemplate;
    public int indentation = 2;
    public String unknownImportTemplate;
    public boolean commentThrows;
    public String unknownAnnotations;

    @Override
    public String toString() {
        return "JtsConfig{\n" +
                "  inputDirectory: " + this.inputDirectory + "\n" +
                "  outputDirectory: " + this.outputDirectory + "\n" +
                '}';
    }

    public Optional<String> getMappedNameForPackage(final String packageName, final ModuleMap fromModuleMap) {
        for (ModuleMap m: this.moduleMaps.values()) {
            Optional<String> optName = m.getMapppedNameForPackageName(packageName);
            if (!optName.isEmpty()) {
                return Optional.of((m == fromModuleMap ? JavaToTypescript.DOT_SLASH : JavaToTypescript.javaImportify(m.tsModule) + ".") + optName.get());
            }
        }
        return Optional.empty();
    }
}
