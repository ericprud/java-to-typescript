package org.javatots.main;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.ArrayList;
import java.util.Iterator;

class DelombokVisitor extends ModifierVisitor<Void> {
    boolean getters;
    boolean setters;
    boolean noArgsCtor;
    boolean allArgsCtor;
    ArrayList<MemberDeclarations> memberDeclarations;

    @Override
    public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) { //  MethodDeclaration
        this.getters = false;
        this.setters = false;
        this.noArgsCtor = false;
        this.allArgsCtor = false;
        memberDeclarations = new ArrayList<>();

        // Strip out Lombok directives.
        NodeList<AnnotationExpr> annotations = n.getAnnotations();
        Iterator iAnnot = annotations.iterator();
        NodeList<AnnotationExpr> remaining = new NodeList<>();
        while (iAnnot.hasNext()) {
            AnnotationExpr annot = (AnnotationExpr) iAnnot.next();
            switch (annot.getName().asString()) {
                case "Getter":
                    getters = true;
                    break;
                case "Setter":
                    setters = true;
                    break;
                case "NoArgsConstructor":
                    noArgsCtor = true;
                    break;
                case "AllArgsConstructor":
                    allArgsCtor = true;
                    break;
                default:
                    AnnotationExpr newAnnotExpr = (AnnotationExpr) annot.accept(this, arg);
                    remaining.add(newAnnotExpr);
//                    throw new IllegalStateException("Unexpected value: " + annot.getName());
            }
        }
        n.setAnnotations(remaining); // cleared out list

        // Walk member list.
        NodeList<BodyDeclaration<?>> members = n.getMembers();
        Iterator iMember = members.iterator();
        while (iMember.hasNext()) {
            BodyDeclaration member = (BodyDeclaration) iMember.next();
            if (member instanceof FieldDeclaration) { //  && ((FieldDeclaration) member).getModifiers().indexOf(Modifier.keyword.private) ?
                FieldDeclaration asFieldDecl = (FieldDeclaration) member;
                NodeList<VariableDeclarator> varDecls = asFieldDecl.getVariables();
                Iterator iVarDecl = varDecls.iterator();
                while (iVarDecl.hasNext()) {
                    VariableDeclarator var = (VariableDeclarator) iVarDecl.next();
                    Type t = var.getType();
                    MemberDeclarations memberDeclarations = new MemberDeclarations(var);
                    this.memberDeclarations.add(memberDeclarations);
                }
            }
        }

        // Generate constructors
        if (noArgsCtor) {
            ConstructorDeclaration ctor = n.addConstructor(Modifier.Keyword.PUBLIC);
            ctor.addModifier(Modifier.Keyword.PUBLIC);
        }

        if (allArgsCtor) {
            ConstructorDeclaration ctor = n.addConstructor(Modifier.Keyword.PUBLIC);
            ctor.addModifier(Modifier.Keyword.PUBLIC);
            // Add parameters
            for (MemberDeclarations memberDeclaration : memberDeclarations) {
                ctor.addAndGetParameter(memberDeclaration.t.getClass(), memberDeclaration.name);
            }
            BlockStmt block = new BlockStmt();
            ctor.setBody(block);
            // Add assignments
            for (MemberDeclarations memberDeclaration : memberDeclarations) {
                block.addStatement(memberDeclaration.makeAssignment());
            }
        }

        // Generate getters and setters.
        for (MemberDeclarations memberDeclaration : memberDeclarations) {
            if (setters) {
                MethodDeclaration setter = n.addMethod("set" + memberDeclaration.capitolizedName, Modifier.Keyword.PUBLIC);
                setter.addAndGetParameter(memberDeclaration.t.getClass(), memberDeclaration.name);
                BlockStmt block = new BlockStmt();
                setter.setBody(block);
                block.addStatement(memberDeclaration.makeAssignment());
//                                NameExpr clazz = new NameExpr("System");
//                                FieldAccessExpr field = new FieldAccessExpr(clazz, "out");
//                                MethodCallExpr call = new MethodCallExpr(field, "println");
//                                call.addArgument(new StringLiteralExpr("Hello World!"));
//                                block.addStatement(call);
            }
            if (getters) {
                MethodDeclaration getter = n.addMethod("get" + memberDeclaration.capitolizedName, Modifier.Keyword.PUBLIC);
                getter.setType(memberDeclaration.t.clone());
                BlockStmt block = new BlockStmt();
                getter.setBody(block);
                block.addStatement(new ReturnStmt(new FieldAccessExpr(new ThisExpr(), memberDeclaration.name)));
            }
        }

        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(FieldDeclaration n, Void arg) { // FieldDeclaration MethodDeclaration ClassOrInterfaceDeclaration
        return super.visit(n, arg);
    }
/*
        @Override
        public Visitable visit(IfStmt n, Void arg) { // FieldDeclaration MethodDeclaration
            // Figure out what to get and what to cast simply by looking at the AST in a debugger!
            n.getCondition().ifBinaryExpr(binaryExpr -> {
                if (binaryExpr.getOperator() == BinaryExpr.Operator.NOT_EQUALS && n.getElseStmt().isPresent()) {
                    /* It's a good idea to clone nodes that you move around.
                        JavaParser (or you) might get confused about who their parent is!
                    *\/
                    Statement thenStmt = n.getThenStmt().clone();
                    Statement elseStmt = n.getElseStmt().get().clone();
                    n.setThenStmt(elseStmt);
                    n.setElseStmt(thenStmt);
                    binaryExpr.setOperator(BinaryExpr.Operator.EQUALS);
                }
            });
            return super.visit(n, arg);
        }
        */
}
