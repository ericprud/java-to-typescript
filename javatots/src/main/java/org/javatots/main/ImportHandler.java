package org.javatots.main;

public class ImportHandler {
    public String packageName;
    public String className;
    public String transformerClass;
    public String importName;
    public boolean importIsStatic;
    public boolean importIsAsterisk;

    public ImportHandler(String packageName, String className, String transformerClass) {
        this.packageName = packageName;
        this.className = className;
        this.transformerClass = transformerClass;
        this.importName = null;
        this.importIsStatic = false;
        this.importIsAsterisk = false;
    }
    public ImportHandler(String packageName, String className, String transformerClass, String importName, boolean importIsStatic, boolean importIsAsterisk) {
        this(packageName, className, transformerClass);
        this.importName = importName;
        this.importIsStatic = importIsStatic;
        this.importIsAsterisk = importIsAsterisk;
    }
}
