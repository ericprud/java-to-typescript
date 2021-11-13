package org.javatots.main;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;

public class MemberDeclarations {
    public String name;
    String capitolizedName;
    Type t;

    public MemberDeclarations(final VariableDeclarator var) {
        this.t = var.getType();
        this.name = var.getNameAsString();
        this.capitolizedName = Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1);
    }
}
