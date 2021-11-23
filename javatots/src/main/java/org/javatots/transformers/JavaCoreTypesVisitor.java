package org.javatots.transformers;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * Change java-native scalar types to corresponding typescript (scalar) types.
 */
public class JavaCoreTypesVisitor extends ModifierVisitor<Void> {
    /**
     * Map Java built-in boxed types to Typescript
     * @param n AST class/interface type node
     * @param arg ignored
     * @return replacement node
     */
    @Override
    public Visitable visit(final ClassOrInterfaceType n, final Void arg) {
        switch (n.getName().asString()) {
            case "String": n.setName("string"); break;
            case "Integer": n.setName("number"); break;
        }
        return super.visit(n, arg);
    }

    /**
     * Turn primitive Java ints to Typescript numbers
     * @param n AST primitie type node
     * @param arg ignored
     * @return replacement node
     */
    @Override
    public Visitable visit(final PrimitiveType n, final Void arg) {
        switch (n.getType()) {
            case INT:
                return new ClassOrInterfaceType("number"); // TODO: deprecated; use what instead?
            default:
                return super.visit(n, arg);
        }
    }

    /**
     * Map built-in Java calls to Typescript
     * e.g. `System.out.println(c);`
     *   -> `console.log(c);`
     * @param n Java method call node
     * @param arg ignored
     * @return replacement node
     */
    public Visitable visit(final MethodCallExpr n, final Void arg) {
        if (n.getNameAsString().equals("equals")) {
            if (!n.getScope().isEmpty()) {
                final BinaryExpr equalsOp = new BinaryExpr(n.getScope().get(), n.getArgument(0), BinaryExpr.Operator.EQUALS);
                return equalsOp;
            } else {
                System.out.println("no scope for " + n.toString());
                return super.visit(n, arg);
            }
        } else {
            n.getScope().ifPresent(functionScope -> {
                functionScope.ifFieldAccessExpr(output -> {
                    Expression system = output.getScope();
                    output.getScope().ifNameExpr(matchSystem -> {
                        if (matchSystem.getNameAsString().equals("System")) {
                            String consoleFunction = null;
                            String unixName = null;
                            switch (output.getName().asString()) {
                                case "out":
                                    consoleFunction = "log";
                                    unixName = "stdout";
                                    break;
                                case "error":
                                    consoleFunction = "err";
                                    unixName = "stderr";
                                    break;
                            }
                            if (unixName != null) {
                                switch (n.getNameAsString()) {
                                    case "println":
                                        // set a new scope
                                        n.setScope(new NameExpr("console"));
                                        n.setName(consoleFunction);
                                        break;
                                    case "print":
                                        // leverage the fact that we need the same scope depth
                                        matchSystem.setName("process");
                                        output.setName(unixName);
                                        n.setName("write");
                                        break;
                                }
                            }
                        }
                    });
                });
            });
            return super.visit(n, arg);
        }
    }
}
