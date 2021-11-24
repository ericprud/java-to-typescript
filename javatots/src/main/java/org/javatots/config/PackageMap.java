package org.javatots.config;

import org.javatots.main.JavaToTypescript;

import java.nio.file.Path;
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

    public String getFileName(final String javaFilePath) {
        return JavaToTypescript.setExtension(String.valueOf(Path.of(
                this.destPath == null
                ? ""
                : this.destPath, javaFilePath.substring(getPkgPath().length())
        )), JavaToTypescript.TYPESCRIPT_FILE_EXTENSION);
    }

    public String getPackageName(final String javaPackagepPath) {
        String cls = javaPackagepPath.substring(getPkgPath().length() + 1);
        return this.destPath == null
        ? cls
        : this.destPath.replaceAll("/", "\\.") + '.' + cls;
    }
}
