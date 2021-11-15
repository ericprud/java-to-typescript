package org.javatots.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

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
                "  inputDirectory: " + inputDirectory + "\n" +
                "  outputDirectory: " + outputDirectory + "\n" +
                '}';
    }

}
