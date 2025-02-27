package org.javatots.main;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.SourcePrinter;
import com.github.javaparser.printer.configuration.ConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.utils.PositionUtils;
import com.github.javaparser.utils.Utils;


import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Override the serialization of parts of the Java AST to make it Typescript-y
 */
public class TypescriptPrettyPrinter extends DefaultPrettyPrinterVisitor {
    final Optional<PackageDeclaration> packageDeclaration;
    private boolean inMethod = true;
    private boolean inMethodParameter = false;
    private Optional<Type> curType = Optional.empty();
    BiConsumer<SourcePrinter, Name> onPackageDeclaration = null;
    BiConsumer<SourcePrinter, NodeList<ImportDeclaration>> onImportDeclarations = null;
    BiConsumer<SourcePrinter, ImportDeclaration> onImportDeclaration = null;
    BiConsumer<SourcePrinter, NodeList<ReferenceType>> onThrows;
    BiConsumer<SourcePrinter, NodeList<AnnotationExpr>> onMethodAnnotations;

    public TypescriptPrettyPrinter(final PrinterConfiguration configuration, final Optional<PackageDeclaration> packageDeclaration) {
        super(configuration);
        this.packageDeclaration = packageDeclaration;
    }

    public void setOnPackageDeclaration(final BiConsumer<SourcePrinter, Name> f) {
        this.onPackageDeclaration = f;
    }

    public void setOnImportDeclarations(final BiConsumer<SourcePrinter, NodeList<ImportDeclaration>> f) {
        this.onImportDeclarations = f;
    }

    public void setOnImportDeclaration(final BiConsumer<SourcePrinter, ImportDeclaration> f) {
        this.onImportDeclaration = f;
    }

    public void setOnThrows(final BiConsumer<SourcePrinter, NodeList<ReferenceType>> throwsList) {
        this.onThrows = throwsList;
    }

    public void setOnMethodAnnotations(final BiConsumer<SourcePrinter, NodeList<AnnotationExpr>> onMethodAnnotations) {
        this.onMethodAnnotations = onMethodAnnotations;
    }

    @Override
    public void visit(final CompilationUnit n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);

        if (n.getParsed() == Node.Parsedness.UNPARSABLE) {
            this.printer.println("???");
        } else {
            if (n.getPackageDeclaration().isPresent() && this.onPackageDeclaration != null) {
                final Name packageName = n.getPackageDeclaration().get().getName();
                this.onPackageDeclaration.accept(this.printer, packageName);
            }

            if (this.onImportDeclarations != null) {
                this.onImportDeclarations.accept(this.printer, n.getImports());
            } else {
                n.getImports().accept(this, arg);
            }
            if (!n.getImports().isEmpty()) {
                this.printer.println();
            }

            Iterator i = n.getTypes().iterator();

            while(i.hasNext()) {
                ((TypeDeclaration)i.next()).accept(this, arg);
                this.printer.println();
                if (i.hasNext()) {
                    this.printer.println();
                }
            }

            n.getModule().ifPresent((m) -> m.accept(this, arg));
            this.printOrphanCommentsEnding(n);
        }
    }

    @Override
    public void visit(final ImportDeclaration n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        if (this.onImportDeclaration != null) {
            this.onImportDeclaration.accept(this.printer, n);
        }
        this.printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(final ClassOrInterfaceDeclaration n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printMemberAnnotations(n.getAnnotations(), arg);

        final NodeList<Modifier> modifiers = n.getModifiers();
        final NodeList<Modifier> remainingModifiers = new NodeList<>();
        Iterator i = modifiers.iterator();
        while(i.hasNext()) {
            Modifier m = (Modifier) i.next();
            if (m.getKeyword() == Modifier.Keyword.PUBLIC) {
                this.printer.print("export ");
            } else {
                remainingModifiers.add(m);
            }
        }
        this.printModifiers(remainingModifiers, TypescriptFinalKeyword.CONST);
        if (n.isInterface()) {
            this.printer.print("interface ");
        } else {
            this.printer.print("class ");
        }

        n.getName().accept(this, arg);
        this.printTypeParameters(n.getTypeParameters(), arg);
        ClassOrInterfaceType c;
        if (!n.getExtendedTypes().isEmpty()) {
            this.printer.print(" extends ");
            i = n.getExtendedTypes().iterator();

            while(i.hasNext()) {
                c = (ClassOrInterfaceType)i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        if (!n.getImplementedTypes().isEmpty()) {
            this.printer.print(" implements ");
            i = n.getImplementedTypes().iterator();

            while(i.hasNext()) {
                c = (ClassOrInterfaceType)i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        this.printer.println(" {");
        this.printer.indent();
        if (!Utils.isNullOrEmpty(n.getMembers())) {
            this.printMembers(n.getMembers(), arg);
        }

        this.printOrphanCommentsEnding(n);
        this.printer.unindent();
        this.printer.print("}");
    }

    @Override
    public void visit(final ClassOrInterfaceType n, final Void arg) {
        if (n.getNameAsString().equals(JavaToTypescript.OR_NULL)) {
            Type typeArg = n.getTypeArguments()
                    .map(n2 -> n2.get(0))
                    .orElseThrow(
                            () -> new IllegalArgumentException("expected nested type for Optional: " + n.asString())
                    );
            typeArg.accept(this, arg);
            this.printer.print(" | null");
        } else {
            super.visit(n, arg);
        }
    }

    @Override
    public void visit(final MethodDeclaration n, final Void arg) {
        this.inMethod = true;
        boolean override = false;
//        Log.info("    " + (this.packageDeclaration.isPresent() ? packageDeclaration.get().getName() : "<no package>") + "." + n.getName());
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);

        //        this.printMemberAnnotations(n.getAnnotations(), arg);
        final NodeList<AnnotationExpr> annotations = n.getAnnotations();
        if (!annotations.isEmpty()) {
            NodeList<AnnotationExpr> remaining = new NodeList<>();
            Iterator var3 = annotations.iterator();

            while(var3.hasNext()) {
                AnnotationExpr a = (AnnotationExpr)var3.next();
                if (a.getName().asString().equals("Override")) {
                    override = true;
                } else {
                    remaining.add(a);
                }
            }
            if (!remaining.isEmpty()) {
                if (this.onMethodAnnotations != null) {
                    this.onMethodAnnotations.accept(this.printer, remaining);
                } else {
                    throw new IllegalStateException("Unknown method annotations: " + remaining.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(", ")));
                }
            }
        }

        if (override && !n.getName().asString().equals("toString")) {
            this.printer.print("override ");
        }
        this.printModifiers(n.getModifiers(), TypescriptFinalKeyword.CONST);
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
                this.inMethodParameter = true;
                p.accept(this, arg);
                this.inMethodParameter = false;
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        this.printer.print(")");
        this.printer.print(": ");
        n.getType().accept(this, arg);
        if (!Utils.isNullOrEmpty(n.getThrownExceptions()) && this.onThrows != null) {
            this.onThrows.accept(this.printer, n.getThrownExceptions());
        }

        if (!n.getBody().isPresent()) {
            this.printer.print(";");
        } else {
            this.printer.print(" ");
            n.getBody().get().accept(this, arg);
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

    @Override
    public void visit(final FieldDeclaration n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printMemberAnnotations(n.getAnnotations(), arg);
        this.printer.print(" ");
        Iterator i = n.getVariables().iterator();

        while(i.hasNext()) {
            this.printModifiers(n.getModifiers(), TypescriptFinalKeyword.READONLY); // TODO: I don't know if there's TS analog to `const foo = {}, bar = [];` so I moved the modifiers in the loop.
            this.curType = n.getMaximumCommonType();
            ((VariableDeclarator)i.next()).accept(this, arg);
            this.curType = Optional.empty();
        }

        this.printer.print(";");
    }

    @Override
    public void visit(final VariableDeclarationExpr n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        Objects.requireNonNull(ExpressionStmt.class);
        if ((Boolean) ((Optional) n.getParentNode()).map(ExpressionStmt.class::isInstance).orElse(false)) {
            this.printMemberAnnotations(n.getAnnotations(), arg);
        } else {
            this.printAnnotations(n.getAnnotations(), false, arg);
        }
        boolean isFinal = n.getModifiers().stream().anyMatch(m -> m.getKeyword().equals(Modifier.Keyword.FINAL));

        Iterator i = n.getVariables().iterator();

        while(i.hasNext()) {
            if (!isFinal) {
                this.printer.print("let ");
            }
            this.printModifiers(n.getModifiers(), TypescriptFinalKeyword.CONST);
            this.curType = n.getMaximumCommonType();
            ((VariableDeclarator)i.next()).accept(this, arg);
            this.curType = Optional.empty();
        }

    }

    public enum TypescriptFinalKeyword {
        CONST("const"),
        READONLY("readonly"),
        COMMENT("/*const*/");

        public String getValue() {
            return this.value;
        }

        private final String value;

        TypescriptFinalKeyword(String value) {
            this.value = value;
        }
    }

    protected void printModifiers(final NodeList<Modifier> modifiers, final TypescriptFinalKeyword typescriptFinalKeyword) {
        if (modifiers.size() > 0) {
            String[] tsModifiers = {null, null, null}; // where each modifier will appear.
            for (Modifier modifier: modifiers) {
                final Modifier.Keyword keyword = modifier.getKeyword();
                final String representation = keyword == Modifier.Keyword.FINAL // The `final` concept in Java has
                        ? typescriptFinalKeyword.getValue() // context-dependent representations in Typescript.
                        : modifier.getKeyword().asString(); // Otherwise, both languages share common keywords
                final int slot = TYPESCRIPT_MODIFIER_SLOTS.get(keyword);
                tsModifiers[slot] = representation;
            }
            String tsText = Arrays.stream(tsModifiers)
                    .filter(s -> s != null)
                    .collect(Collectors.joining(" "));
            if (tsText.length() > 0)
                printer.print( tsText + " ");
        }

    }

    static final Map<Modifier.Keyword, Integer> TYPESCRIPT_MODIFIER_SLOTS = Map.of(
//            Modifier.Keyword.DEFAULT, 1,
            Modifier.Keyword.PUBLIC, 0,
            Modifier.Keyword.PROTECTED, 0,
            Modifier.Keyword.PRIVATE, 0,
            Modifier.Keyword.ABSTRACT, 1,
            Modifier.Keyword.STATIC, 1,
            Modifier.Keyword.FINAL, 2/*,
            Modifier.Keyword.TRANSIENT, 1,
            Modifier.Keyword.VOLATILE, 1,
            Modifier.Keyword.SYNCHRONIZED, 1,
            Modifier.Keyword.NATIVE, 1,
            Modifier.Keyword.STRICTFP, 1,
            Modifier.Keyword.TRANSITIVE, 1*/
    );

    @Override
    public void visit(final VariableDeclarator n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        n.getName().accept(this, arg);
        this.printer.print(": ");
        if (this.curType.isEmpty()) {
            throw new IllegalStateException("curType is not set at " + this.printer);
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
            n.getInitializer().get().accept(this, arg);
        }

    }

    @Override
    public void visit(final Parameter n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printAnnotations(n.getAnnotations(), false, arg);
        this.printModifiers(n.getModifiers(), TypescriptFinalKeyword.COMMENT);
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

    @Override
    public void visit(final ConstructorDeclaration n, final Void arg) {
        this.inMethod = true;
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printMemberAnnotations(n.getAnnotations(), arg);
        this.printModifiers(n.getModifiers(), TypescriptFinalKeyword.CONST);
        this.printTypeParameters(n.getTypeParameters(), arg);
        if (n.isGeneric()) {
            this.printer.print(" ");
        }

        this.printer.print("constructor"); // n.getName().accept(this, arg);
        this.printer.print("(");
        Iterator i;
        if (!n.getParameters().isEmpty()) {
            i = n.getParameters().iterator();

            while(i.hasNext()) {
                Parameter p = (Parameter)i.next();
                this.inMethodParameter = true;
                p.accept(this, arg);
                this.inMethodParameter = false;
                if (i.hasNext()) {
                    this.printer.print(", ");
                }
            }
        }

        this.printer.print(")");
        if (!Utils.isNullOrEmpty(n.getThrownExceptions()) && this.onThrows != null) {
            this.onThrows.accept(this.printer, n.getThrownExceptions());
        }

        this.printer.print(" ");
        n.getBody().accept(this, arg);
        this.inMethod = false;
    }

    @Override
    public void visit(final BinaryExpr n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        n.getLeft().accept(this, arg);
        if (this.getOption(DefaultPrinterConfiguration.ConfigOption.SPACE_AROUND_OPERATORS).isPresent()) {
            this.printer.print(" ");
        }

        if (n.getOperator().equals(BinaryExpr.Operator.EQUALS)) {
            this.printer.print("===");
        } else {
            this.printer.print(n.getOperator().asString());
        }
        if (this.getOption(DefaultPrinterConfiguration.ConfigOption.SPACE_AROUND_OPERATORS).isPresent()) {
            this.printer.print(" ");
        }

        n.getRight().accept(this, arg);
    }

    /*
     * from: `for (Address address: Addresses)`
     * to: `for (const address of Addresses)`
     */
    @Override
    public void visit(final ForEachStmt n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printer.print("for (const ");
        final VariableDeclarator entryVariable = n.getVariable().getVariable(0); // expect exactly one variable in a ForEachStmt
        entryVariable.getName().accept(this, arg);
        this.printer.print(" of ");
        n.getIterable().accept(this, arg);
        this.printer.print(") ");
        n.getBody().accept(this, arg);
    }

    public void visit(final TryStmt n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printer.print("try ");
        Iterator resources;
        if (!n.getResources().isEmpty()) {
            this.printer.print("(");
            resources = n.getResources().iterator();

            for(boolean first = true; resources.hasNext(); first = false) {
                ((Expression)resources.next()).accept(this, arg);
                if (resources.hasNext()) {
                    this.printer.print(";");
                    this.printer.println();
                    if (first) {
                        this.printer.indent();
                    }
                }
            }

            if (n.getResources().size() > 1) {
                this.printer.unindent();
            }

            this.printer.print(") ");
        }

        n.getTryBlock().accept(this, arg);

        this.printer.indent();
        this.printer.reindentWithAlignToCursor();
        printer.print(" catch (ex) {\n");
        resources = n.getCatchClauses().iterator();
        boolean isFirstParmeter = true;
        while(resources.hasNext()) {
            CatchClause c = (CatchClause)resources.next();
            Parameter parm = c.getParameter();
            if (isFirstParmeter) {
                isFirstParmeter = false;
            } else {
                printer.print(" else");
            }
            printer.print(" if (");
            Type type = parm.getType();
            if (type.isUnionType()) {
                NodeList<ReferenceType> elements = type.asUnionType().getElements();
                Iterator<ReferenceType> it = elements.iterator();
                boolean firstElement = true;
                while(it.hasNext()) {
                    if (firstElement) {
                        firstElement = false;
                    } else {
                        printer.print(" || ");
                    }
                    printer.print("ex instanceof ");
                    ReferenceType element = it.next();
                    element.accept(this, arg);
                }
            } else {
                printer.print("ex instanceof ");
                type.accept(this, arg);
            }
            printer.print(") ");
            c.getBody().accept(this, arg);
        }
        this.printer.unindent();

        if (n.getFinallyBlock().isPresent()) {
            this.printer.print(" finally ");
            ((BlockStmt)n.getFinallyBlock().get()).accept(this, arg);
        }

    }

/*
    public void visit(final CatchClause n, final Void arg) {
        this.printOrphanCommentsBeforeThisChildNode(n);
        this.printComment(n.getComment(), arg);
        this.printer.print(" catch (");
        n.getParameter().accept(this, arg);
        this.printer.print(") ");
        n.getBody().accept(this, arg);
    }
*/

    private void printOrphanCommentsBeforeThisChildNode(final Node node) {
        if (this.getOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS).isPresent()) {
            if (!(node instanceof Comment)) {
                Node parent = node.getParentNode().orElse(null);
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
                            Node nodeToPrint = everything.get(i);
                            if (!(nodeToPrint instanceof Comment)) {
                                throw new RuntimeException("Expected comment, instead " + nodeToPrint.getClass() + ". Position of previous child: " + positionOfPreviousChild + ", position of child " + positionOfTheChild);
                            }

                            nodeToPrint.accept((VoidVisitor)this, null);
                        }

                    }
                }
            }
        }
    }

    private void printOrphanCommentsEnding(final Node node) {
        if (this.getOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS).isPresent()) {
            List<Node> everything = new ArrayList(node.getChildNodes());
            PositionUtils.sortByBeginPosition(everything);
            if (!everything.isEmpty()) {
                int commentsAtEnd = 0;
                boolean findingComments = true;

                while(findingComments && commentsAtEnd < everything.size()) {
                    Node last = everything.get(everything.size() - 1 - commentsAtEnd);
                    findingComments = last instanceof Comment;
                    if (findingComments) {
                        ++commentsAtEnd;
                    }
                }

                for(int i = 0; i < commentsAtEnd; ++i) {
                    everything.get(everything.size() - commentsAtEnd + i).accept((VoidVisitor)this, null);
                }

            }
        }
    }

    private Optional<ConfigurationOption> getOption(DefaultPrinterConfiguration.ConfigOption cOption) {
        return this.configuration.get(new DefaultConfigurationOption(cOption));
    }
}
