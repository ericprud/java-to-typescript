package org.javatots.config;

import java.util.List;

/**
 * Configuration for mapping subdirs in Java source to subdirs in Typescript source.
 */
public class ModuleMap {
    public String outputPath;
    public String srcRoot;
    public List<PackageMap> packageMaps;

    @Override
    public String toString() {
        return "ModuleMap{" +
                "outputPath='" + this.outputPath + '\'' +
                ", srcRoot='" + this.srcRoot + '\'' +
                ", packageMaps=" + this.packageMaps +
                '}';
    }
}
