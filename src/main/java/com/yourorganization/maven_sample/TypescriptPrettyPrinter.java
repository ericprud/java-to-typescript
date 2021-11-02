//package com.github.javaparser.printer;
package com.yourorganization.maven_sample;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.ConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.utils.Log;
import com.github.javaparser.utils.PositionUtils;
import com.github.javaparser.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class TypescriptPrettyPrinter extends DefaultPrettyPrinterVisitor {
    final Optional<PackageDeclaration> packageDeclaration;
    private boolean inMethod = true;
    private Optional<Type> curType = null;

    public TypescriptPrettyPrinter(final PrinterConfiguration configuration, final Optional<PackageDeclaration> packageDeclaration) {
        super(configuration/*, new SourcePrinter(configuration)*/);
        this.packageDeclaration = packageDeclaration;
    }

    @Override
    public void visit(final MethodDeclaration n, final Void arg) {
        this.inMethod = true;
        Log.info("    " + (this.packageDeclaration.isPresent() ? packageDeclaration.get().getName() : "<no package>") + "." + n.getName());
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printMemberAnnotations(n.getAnnotations(), arg);
        this.printModifiers(n.getModifiers());
        this.printTypeParameters(n.getTypeParameters(), arg);
        if (!Utils.isNullOrEmpty(n.getTypeParameters())) {
            this.printer.print(" ");
        }

        n.getName().accept(this, arg);
        this.printer.print("(");
        n.getReceiverParameter().ifPresent((rp) -> {
            rp.accept(this, arg);
            if (!Utils.isNullOrEmpty(n.getParameters())) {
                this.printer.print(", ");
            }

        });
        Iterator i;
        if (!Utils.isNullOrEmpty(n.getParameters())) {
            i = n.getParameters().iterator();

            while (i.hasNext()) {
                Parameter p = (Parameter) i.next();
                p.accept(this, arg);
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        this.printer.print(")");
        this.printer.print(": ");
        n.getType().accept(this, arg);
        if (!Utils.isNullOrEmpty(n.getThrownExceptions())) {
            this.printer.print(" throws ");
            i = n.getThrownExceptions().iterator();

            while (i.hasNext()) {
                ReferenceType name = (ReferenceType) i.next();
                name.accept(this, arg);
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        if (!n.getBody().isPresent()) {
            this.printer.print(";");
        } else {
            this.printer.print(" ");
            ((BlockStmt) n.getBody().get()).accept(this, arg);
        }
        this.inMethod = false;
    }

    protected void printMembers(final NodeList<BodyDeclaration<?>> members, final Void arg) {
        Iterator var3 = members.iterator();
        while(var3.hasNext()) {
            BodyDeclaration<?> member = (BodyDeclaration)var3.next();
            this.printer.println();
            member.accept(this, arg);
            this.printer.println();
        }
    }

    public void visit(final FieldDeclaration n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printMemberAnnotations(n.getAnnotations(), arg);
        this.printer.print(" ");
        Iterator i = n.getVariables().iterator();

        while(i.hasNext()) {
            this.printModifiers(n.getModifiers()); // TODO: I don't know if there's TS analog to `const foo = {}, bar = [];` so I moved the modifiers in the loop.
            this.curType = n.getMaximumCommonType();
            ((VariableDeclarator)i.next()).accept(this, arg);
            this.curType = null;
//            if (i.hasNext()) { // same TODO
//                this.printer.print(", ");
//            }
        }

        this.printer.print(";");
    }

    public void visit(final VariableDeclarationExpr n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        Optional var10000 = n.getParentNode();
        Objects.requireNonNull(ExpressionStmt.class);
        if ((Boolean)var10000.map(ExpressionStmt.class::isInstance).orElse(false)) {
            this.printMemberAnnotations(n.getAnnotations(), arg);
        } else {
            this.printAnnotations(n.getAnnotations(), false, arg);
        }

        Iterator i = n.getVariables().iterator();

        while(i.hasNext()) {
            this.printModifiers(n.getModifiers());
            this.curType = n.getMaximumCommonType();
            ((VariableDeclarator)i.next()).accept(this, arg);
            this.curType = null;
//            if (i.hasNext()) {
//                this.printer.print(", ");
//            }
        }

    }

    protected void printModifiers(final NodeList<Modifier> modifiers) {
        if (modifiers.size() > 0) {
            this.printer.print((String)modifiers
                    .stream()
                    .map(Modifier::getKeyword)
                    .map(Modifier.Keyword::asString)
                    .filter(
                        s -> this.inMethod || !s.equals("final") // https://www.typescriptlang.org/play told me "A class member cannot have the 'const' keyword."
                    )
                    .map(
                        s -> s.equals("final") ? "const" : s
                    )
                    .collect(Collectors.joining(" "))
                    + " "
            );
        }

    }

    public void visit(final VariableDeclarator n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        n.getName().accept(this, arg);
        this.printer.print(": ");
        if (this.curType == null) {
            throw new IllegalStateException("curType is null at " + this.printer.toString());
        }
        this.curType.ifPresent((t) -> {
            t.accept(this, arg);
        });
        if (!this.curType.isPresent()) {
            this.printer.print("???");
        }
        n.findAncestor(NodeWithVariables.class).ifPresent((ancestor) -> {
            ancestor.getMaximumCommonType().ifPresent((commonType) -> {
                Type type = n.getType();
                ArrayType arrayType = null;

                for(int i = ((Type)commonType).getArrayLevel(); i < type.getArrayLevel(); ++i) {
                    if (arrayType == null) {
                        arrayType = (ArrayType)type;
                    } else {
                        arrayType = (ArrayType)arrayType.getComponentType();
                    }

                    this.printAnnotations(arrayType.getAnnotations(), true, arg);
                    this.printer.print("[]");
                }

            });
        });
        if (n.getInitializer().isPresent()) {
            this.printer.print(" = ");
            ((Expression)n.getInitializer().get()).accept(this, arg);
        }

    }

    public void visit(final Parameter n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printAnnotations(n.getAnnotations(), false, arg);
        this.printModifiers(n.getModifiers());
        if (n.isVarArgs()) {
            this.printAnnotations(n.getVarArgsAnnotations(), false, arg);
            this.printer.print("...");
        }

        n.getName().accept(this, arg);

        if (!(n.getType() instanceof UnknownType)) {
            this.printer.print(": ");
        }
        n.getType().accept(this, arg);
    }

    public void visit(final ConstructorDeclaration n, final Void arg) {
        this.inMethod = true;
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printMemberAnnotations(n.getAnnotations(), arg);
        this.printModifiers(n.getModifiers());
        this.printTypeParameters(n.getTypeParameters(), arg);
        if (n.isGeneric()) {
            this.printer.print(" ");
        }

        n.getName().accept(this, arg);
        this.printer.print("(");
        Iterator i;
        if (!n.getParameters().isEmpty()) {
            i = n.getParameters().iterator();

            while(i.hasNext()) {
                Parameter p = (Parameter)i.next();
                p.accept(this, arg);
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        this.printer.print(")");
        if (!Utils.isNullOrEmpty(n.getThrownExceptions())) {
            this.printer.print(" throws ");
            i = n.getThrownExceptions().iterator();

            while(i.hasNext()) {
                ReferenceType name = (ReferenceType)i.next();
                name.accept(this, arg);
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        this.printer.print(" ");
        n.getBody().accept(this, arg);
        this.inMethod = false;
    }

    public void visit999(final VariableDeclarator n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        n.getName().accept(this, arg);
        n.findAncestor(NodeWithVariables.class).ifPresent((ancestor) -> {
            ancestor.getMaximumCommonType().ifPresent((commonType) -> {
                Type type = n.getType();
                ArrayType arrayType = null;

                for(int i = ((Type)commonType).getArrayLevel(); i < type.getArrayLevel(); ++i) {
                    if (arrayType == null) {
                        arrayType = (ArrayType)type;
                    } else {
                        arrayType = (ArrayType)arrayType.getComponentType();
                    }

                    this.printAnnotations(arrayType.getAnnotations(), true, arg);
                    this.printer.print("[]");
                }

            });
        });
        if (n.getInitializer().isPresent()) {
            this.printer.print(" = ");
            ((Expression)n.getInitializer().get()).accept(this, arg);
        }

    }

    private void printOrphanCommentsBeforeThisChildNode(final Node node) {
        if (this.getOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS).isPresent()) {
            if (!(node instanceof Comment)) {
                Node parent = (Node) node.getParentNode().orElse((Node) null);
                if (parent != null) {
                    List<Node> everything = new ArrayList(parent.getChildNodes());
                    PositionUtils.sortByBeginPosition(everything);
                    int positionOfTheChild = -1;

                    int positionOfPreviousChild;
                    for (positionOfPreviousChild = 0; positionOfPreviousChild < everything.size(); ++positionOfPreviousChild) {
                        if (everything.get(positionOfPreviousChild) == node) {
                            positionOfTheChild = positionOfPreviousChild;
                            break;
                        }
                    }

                    if (positionOfTheChild == -1) {
                        throw new AssertionError("I am not a child of my parent.");
                    } else {
                        positionOfPreviousChild = -1;

                        int i;
                        for (i = positionOfTheChild - 1; i >= 0 && positionOfPreviousChild == -1; --i) {
                            if (!(everything.get(i) instanceof Comment)) {
                                positionOfPreviousChild = i;
                            }
                        }

                        for (i = positionOfPreviousChild + 1; i < positionOfTheChild; ++i) {
                            Node nodeToPrint = (Node) everything.get(i);
                            if (!(nodeToPrint instanceof Comment)) {
                                throw new RuntimeException("Expected comment, instead " + nodeToPrint.getClass() + ". Position of previous child: " + positionOfPreviousChild + ", position of child " + positionOfTheChild);
                            }

                            nodeToPrint.accept((VoidVisitor)this, (Object)null);
                        }

                    }
                }
            }
        }
    }

    private Optional<ConfigurationOption> getOption(DefaultPrinterConfiguration.ConfigOption cOption) {
        return this.configuration.get(new DefaultConfigurationOption(cOption));
    }
}
