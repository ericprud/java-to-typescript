package org.javatots.main;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JtsConfig {
    String inputDirectory;

    @Override
    public String toString() {
        return "JtsConfig{\n" +
                "  inputDirectory: " + inputDirectory + "\n" +
                "  outputDirectory: " + outputDirectory + "\n" +
                '}';
    }

    String outputDirectory;

}
