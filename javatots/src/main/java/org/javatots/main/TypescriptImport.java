package org.javatots.main;

public class TypescriptImport {
    public String importName;
    public boolean importIsStatic;
    public boolean importIsAsterisk;

    public TypescriptImport(final String importName, final boolean importIsStatic, final boolean importIsAsterisk) {
        this.importName = importName;
        this.importIsStatic = importIsStatic;
        this.importIsAsterisk = importIsAsterisk;
    }
}
