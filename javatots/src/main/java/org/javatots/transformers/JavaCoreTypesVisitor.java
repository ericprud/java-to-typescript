package org.javatots.transformers;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * Change java-native scalar types to corresponding typescript (scalar) types.
 */
public class JavaCoreTypesVisitor extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(final ClassOrInterfaceType n, final Void arg) {
        switch (n.getName().asString()) {
            case "String": n.setName("string"); break;
            case "Integer": n.setName("number"); break;
            case "int": n.setName("number"); break;
        }
        return super.visit(n, arg);
    }
    @Override
    public Visitable visit(final PrimitiveType n, final Void arg) {
        switch (n.getType()) {
            case INT:
                return new ClassOrInterfaceType("number"); // TODO: deprecated; use what instead?
            default:
                return super.visit(n, arg);
        }
    }
}
