package org.javatots.transformers;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.javatots.main.JavaToTypescript;

/**
 * Mark java.util.`Optional<X>` for transformation to `X | null`.
 */
public class JavaUtilOptionalVisitor extends ModifierVisitor<Void> {
    /**
     * Map Java built-in boxed types to Typescript
     * @param n AST class/interface type node
     * @param arg ignored
     * @return replacement node
     */
    @Override
    public Visitable visit(final ClassOrInterfaceType n, final Void arg) {
        switch (n.getName().asString()) {
            case "Optional": n.setName(JavaToTypescript.OR_NULL); break;
        }
        return super.visit(n, arg);
    }
}
