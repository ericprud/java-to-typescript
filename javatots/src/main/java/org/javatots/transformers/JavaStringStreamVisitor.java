package org.javatots.transformers;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * Change java.io.FileInputStream to typescript Readable.
 * e.g. `StringWriter writer = new StringWriter();`
 *   -> `writer: Writable = new Writable();`
 */
public class JavaStringStreamVisitor extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(final ClassOrInterfaceType n, final Void arg) {
        if (n.asString().equals("StringStream")) {
            n.setName("Writable");
        }
        return super.visit(n, arg);
    }
}
