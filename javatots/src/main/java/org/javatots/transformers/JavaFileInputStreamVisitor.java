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
 * e.g. `InputStream inputStream = new FileInputStream(yamlFilePath);`
 *   -> `inputStream: Readable = Fs.createReadStream(yamlFilePath);`
 * VariableDeclarator.type is `InputStream`
 * VariableDeclarator.initializer is `new FileInputStream(yamlFilePath)`
 */
public class JavaFileInputStreamVisitor extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(final ObjectCreationExpr n, final Void arg) {
        if (n.getType().asString().equals("FileInputStream")) {
            return new MethodCallExpr(n.getScope().orElse(null), "Fs.createReadStream", n.getArguments());
        } else {
            return super.visit(n, arg);
        }
    }

    @Override
    public Visitable visit(final ClassOrInterfaceType n, final Void arg) {
        if (n.asString().equals("InputStream")) {
            n.setName("Readable");
        }
        return super.visit(n, arg);
    }
}
