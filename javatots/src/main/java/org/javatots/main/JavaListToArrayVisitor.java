package org.javatots.main;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * Change java.util.List to typescript-native Array.
 */
class JavaListToArrayVisitor extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(final ClassOrInterfaceType n, final Void arg) {
        if (n.getName().asString().equals("List")) {
            n.setName("Array");
        }
        return super.visit(n, arg);
    }
}
