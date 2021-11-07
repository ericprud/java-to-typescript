package org.javatots.config;

import java.util.Optional;

public class PackageMap {
    public String pkg;
    public Optional<String> path = Optional.empty();
    public String destPath;

    public String getPkgPath() {
        if (path.isEmpty()) {
            path = Optional.of(pkg.replaceAll("\\.", "/"));
        }
        return path.get();
    }

    @Override
    public String toString() {
        return "PackageMap{" +
                "pkg='" + pkg + '\'' +
                ", destPath='" + destPath + '\'' +
                '}';
    }
}
