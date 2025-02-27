package org.javatots.main;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.Type;

/**
 * Fiddly bits of mapping java names to Typescript.
 */
public class MemberDeclarations {
    public final String name;
    public final String capitalizedName;
    public final Type type;

    public MemberDeclarations(final VariableDeclarator var) {
        this.type = var.getType();
        this.name = var.getNameAsString();
        this.capitalizedName = Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1);
    }

    public ExpressionStmt makeAssignment() {
        return new ExpressionStmt(new AssignExpr(new FieldAccessExpr(new ThisExpr(), this.name), new NameExpr(this.name), AssignExpr.Operator.ASSIGN));
    }
}
