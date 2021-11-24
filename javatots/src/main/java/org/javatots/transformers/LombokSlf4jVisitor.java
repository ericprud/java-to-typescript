package org.javatots.transformers;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * Remove `lombok.extern.slf4j.Slf4j` annotations.
 */
public class LombokSlf4jVisitor extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(final MarkerAnnotationExpr n, final Void arg) { rm(n, arg); return super.visit(n, arg); }

    @Override
    public Visitable visit(final SingleMemberAnnotationExpr n, final Void arg) { rm(n, arg); return super.visit(n, arg); }

    @Override
    public Visitable visit(final NormalAnnotationExpr n, final Void arg) { rm(n, arg); return super.visit(n, arg); }

    public void rm(final AnnotationExpr n, final Void arg) {
        if (n.getNameAsString().equals("Slf4j")) {
            n.remove();
        }
    }
}
