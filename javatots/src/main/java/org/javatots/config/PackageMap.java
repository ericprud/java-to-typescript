package org.javatots.config;

import java.util.Optional;

/**
 * How to map a package name in Java to a source hierarchy in Typescript.
 */
public class PackageMap {
    public String pkg;
    public Optional<String> path = Optional.empty();
    public String destPath;

    public String getPkgPath() {
        if (this.path.isEmpty()) {
            this.path = Optional.of(this.pkg.replaceAll("\\.", "/"));
        }
        return this.path.get();
    }

    @Override
    public String toString() {
        return "PackageMap{" +
                "pkg='" + this.pkg + '\'' +
                ", destPath='" + this.destPath + '\'' +
                '}';
    }
}
