package org.javatots.config;

import lombok.Getter;
import lombok.Setter;
import org.javatots.main.JavaToTypescript;

import java.nio.file.Path;
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

    public Optional<String> getMappedNameForPackage(final String qualifiedClassName, final ModuleMap fromModuleMap, final String fromPackage) {
        for (ModuleMap m: this.moduleMaps.values()) {
            Optional<String> optName = m.getMapppedNameForPackageName(qualifiedClassName);
            if (!optName.isEmpty()) {
                final String packageNameString = optName.get();
                if (m == fromModuleMap) {
                    // compute a relative path
                    Path from = Path.of(fromPackage.replaceAll("\\.", "/"));
                    Path to = Path.of(qualifiedClassName.replaceAll("\\.", "/"));
                    String rel = String.valueOf(from.relativize(to));
                    final String rel1 = rel.startsWith(".") ? rel : "./" + rel;
                    return Optional.of(JavaToTypescript.javaImportify(rel1));
                } else {
                    // use the tsModule to reference it
                    return Optional.of(JavaToTypescript.javaImportify((m.tsModule + "." + packageNameString)));
                }
            }
        }
        return Optional.empty();
    }
}
