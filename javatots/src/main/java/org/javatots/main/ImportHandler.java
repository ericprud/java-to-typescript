package org.javatots.main;

public class ImportHandler {
    public String packageName;
    public String className;
    public String transformerClass;
    public TypescriptImport[] typescriptImports;

    public ImportHandler(String packageName, String className, String transformerClass, TypescriptImport[] typescriptImports) {
        this.packageName = packageName;
        this.className = className;
        this.transformerClass = transformerClass;
        this.typescriptImports = typescriptImports;
    }
}
