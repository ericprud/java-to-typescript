package org.javatots.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class JtsConfig {
    public String inputDirectory;
    public String outputDirectory;
    public Map<String, ModuleMap> moduleMaps;
    public String packageTemplate;
    public int indentation = 2;
    public String unknownImportTemplate;

    @Override
    public String toString() {
        return "JtsConfig{\n" +
                "  inputDirectory: " + inputDirectory + "\n" +
                "  outputDirectory: " + outputDirectory + "\n" +
                '}';
    }

}
