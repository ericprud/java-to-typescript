package org.javatots.config;

import java.util.List;

public class ModuleMap {
    public String outputPath;
    public String srcRoot;

    @Override
    public String toString() {
        return "ModuleMap{" +
                "outputPath='" + outputPath + '\'' +
                ", srcRoot='" + srcRoot + '\'' +
                ", packageMaps=" + packageMaps +
                '}';
    }

    public List<PackageMap> packageMaps;
}
