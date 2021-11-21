package org.javatots.config;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for mapping subdirs in Java source to subdirs in Typescript source.
 */
public class ModuleMap {
    public String outputPath;
    public String srcRoot;
    public String tsModule;

    @Override
    public String toString() {
        return "ModuleMap{" +
                "outputPath='" + outputPath + '\'' +
                ", srcRoot='" + srcRoot + '\'' +
                ", tsModule='" + tsModule + '\'' +
                ", packageMaps=" + packageMaps +
                '}';
    }

    public List<PackageMap> packageMaps;

    public PackageMap expectPackageMapForFile(final String javaModuleName, final String javaFilepath) {
        return this.packageMaps.stream().filter(pm1 ->
            javaFilepath.startsWith(pm1.getPkgPath())
        ).findFirst().orElseThrow(() ->
            new IllegalStateException("moduleMap " + javaModuleName + " has no packageMap for source path " + javaFilepath)
        );
    }

    public Optional<String> getMapppedNameForPackageName(final String packageName) {
        final String javaFilepath = packageName.replaceAll("\\.", "/");
        return this.packageMaps.stream().filter(pm1 -> {
            boolean ret = javaFilepath.startsWith(pm1.getPkgPath());
            return ret;
        }).map(pm1 -> {
            return pm1.getPackageName(packageName);
        }).findFirst();
    }
}
