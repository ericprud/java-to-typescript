package org.javatots.main;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.printer.SourcePrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.Indentation;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.Log;
import com.github.javaparser.utils.SourceRoot;

import org.javatots.config.JtsConfig;
import org.javatots.config.ModuleMap;
import org.javatots.config.PackageMap;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Some code that uses JavaParser.
 */
public class JavaToTypescript {
    protected final static String TEST_CONFIG_PATH = "javatots/src/main/resources/config.yaml";
    protected final JtsConfig config;

    JavaToTypescript(JtsConfig config) {
        this.config = config;
    }

    public static void main(String[] args) throws IOException {
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());
        final String configPath = args.length > 0 ? args[0] : TEST_CONFIG_PATH;
        Log.info("Reading YAML configuration from: " + configPath);
        final JtsConfig config = loadConfig(configPath);
        final JavaToTypescript jtos = new JavaToTypescript(config);
        jtos.walkModules();
    }

    public static JtsConfig loadConfig(final String yamlFilePath) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(yamlFilePath);
        return yaml.loadAs(inputStream, JtsConfig.class);
    }

    public void walkModules () throws IOException {
        for (var moduleMapEntry : config.moduleMaps.entrySet()) {
            String javaModuleName = moduleMapEntry.getKey();
            ModuleMap moduleMap = moduleMapEntry.getValue();
            Path javaSrcRootPath = Path.of(config.inputDirectory, javaModuleName, moduleMap.srcRoot);
            Log.info("\nMapping: " + javaSrcRootPath);

            // make a list of the java files in this package
            Path[] files = Files.find(
                        javaSrcRootPath,
                        Integer.MAX_VALUE,
                        (filePath, fileAttr) -> fileAttr.isRegularFile()
                    ).toArray(Path[]::new)
//                    .forEach(System.out::println)
            ;

            // iterate over found Java files
            for (Path p: files) {
                String javaFilepath = String.valueOf(javaSrcRootPath.relativize(p));
                String tsFileName = setExtension(String.valueOf(javaFilepath), "ts");

                // Find the first match PackageMap
                for (PackageMap packageMap: moduleMap.packageMaps) {
                    final String pkgPath = packageMap.getPkgPath();
                    if (javaFilepath.startsWith(pkgPath)) {
                        String destPath = packageMap.destPath == null
                                ? ""
                                : packageMap.destPath;
                        tsFileName = setExtension(String.valueOf(Path.of(destPath, javaFilepath.substring(packageMap.getPkgPath().length()))), "ts");
                        break;
                    }
                }

                Path tsFilePath = Path.of(config.outputDirectory,moduleMap.outputPath, tsFileName);
                Log.info("-- "  + javaFilepath + " -> " + tsFilePath);
                String transformed = this.transformFile("../" + javaSrcRootPath, javaFilepath);
                Files.createDirectories(Path.of(new File(String.valueOf(tsFilePath)).getParent()));
                Writer writer = new PrintWriter(String.valueOf(tsFilePath));
                writer.write(transformed);
                writer.close();
            }
        }
    }

    private static String setExtension(final String filename, final String ext) {
        int idx = filename.lastIndexOf('.');
        return filename.substring(0, idx) + '.' + ext;
    }

    public String transformFile(final String moduleDirectory, final String filename) {

        // apparently relative to Maven module root
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(JavaToTypescript.class).resolve(moduleDirectory));
        CompilationUnit cu = sourceRoot.parse("", filename);

        Log.info("Porting file " + filename + ":");
        TypescriptPrettyPrinter visitor = new TypescriptPrettyPrinter(getPrinterConfiguration(), cu.getPackageDeclaration());

        final BiConsumer<SourcePrinter, Name> handlePackage = (final SourcePrinter printer, final Name packageName) -> {
            if (this.config.packageTemplate != null) {
                printer.println(String.format(this.config.packageTemplate, packageName));
            }
        };

        final BiConsumer<SourcePrinter, ImportDeclaration> handleImport = (final SourcePrinter printer, final ImportDeclaration importDecl) -> {
            final Map<String, String> MY_MAP = Map.of("com.janeirodigital.shapetrees.core.enums", "../../core/enums", "com.janeirodigital.shapetrees.core", "../../core");

            final String path = importDecl.getName().asString();
            int iName = path.lastIndexOf('.');
            final String pkg = path.substring(0, iName);
            final String cls = path.substring(iName + 1);
            if (importDecl.isStatic()) {
//                printer.print("static ");
            } else if (importDecl.isAsterisk()) {
//                printer.print(".*");
            } else if (path.startsWith("com.janeirodigital.shapetrees.core")) {
                printer.println("import {  " + cls + " } from " + pkg + ";");
            } else if (this.config.unknownImportTemplate != null) {
                printer.println(String.format(this.config.unknownImportTemplate, cls, pkg));
            }
        };

        final BiConsumer<SourcePrinter, NodeList<ReferenceType>> handleThrows = (final SourcePrinter printer, final NodeList<ReferenceType> throwsList) -> {
            if (this.config.commentThrows) {
                printer.print(" /* throws ");
                Iterator<ReferenceType> i = throwsList.iterator();

                while (i.hasNext()) {
                    ReferenceType name = (ReferenceType) i.next();
                    name.accept((VoidVisitor) visitor, null);
                    if (i.hasNext()) {
                        printer.print(", ");
                    }
                }
                printer.print(" */");
            }
        };

        cu.accept(new ModifierVisitor<Void>() {
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

                NodeList<AnnotationExpr> annotations = n.getAnnotations();
                Iterator iAnnot = annotations.iterator();
                while(iAnnot.hasNext()) {
                    AnnotationExpr annot = (AnnotationExpr)iAnnot.next();
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
                            throw new IllegalStateException("Unexpected value: " + annot.getName());
                    }
//                    annotations.remove(annot);
//                    Node newNode = (Node)annot.accept(this, arg);
//                    changeList.add(new Pair(annot, newNode));
                }
                n.setAnnotations(annotations); // cleared out list

                NodeList<BodyDeclaration<?>> members = n.getMembers();
                Iterator iMember = members.iterator();
                while(iMember.hasNext()) {
                    BodyDeclaration member = (BodyDeclaration) iMember.next();
                    if (member instanceof FieldDeclaration) { //  && ((FieldDeclaration) member).getModifiers().indexOf(Modifier.keyword.private) ?
                        FieldDeclaration asFieldDecl = (FieldDeclaration) member;
                        NodeList<VariableDeclarator> varDecls = asFieldDecl.getVariables();
                        Iterator iVarDecl = varDecls.iterator();
                        while(iVarDecl.hasNext()) {
                            VariableDeclarator var = (VariableDeclarator) iVarDecl.next();
                            Type t = var.getType();
                            MemberDeclarations memberDeclarations = new MemberDeclarations(var);
                            this.memberDeclarations.add(memberDeclarations);
                        }
                    }
                }

                for (MemberDeclarations memberDeclarations : memberDeclarations) {

                    if (setters) {
                        MethodDeclaration setter = n.addMethod("set" + memberDeclarations.capitolizedName, Modifier.Keyword.PUBLIC);
                        setter.addAndGetParameter(String.class, memberDeclarations.name);
                        BlockStmt block = new BlockStmt();
                        setter.setBody(block);

                        // add a statement do the method body
                        ExpressionStmt set = new ExpressionStmt(new AssignExpr(new FieldAccessExpr(new ThisExpr(), memberDeclarations.name), new NameExpr(memberDeclarations.name), AssignExpr.Operator.ASSIGN));
                        block.addStatement(set);
//                                NameExpr clazz = new NameExpr("System");
//                                FieldAccessExpr field = new FieldAccessExpr(clazz, "out");
//                                MethodCallExpr call = new MethodCallExpr(field, "println");
//                                call.addArgument(new StringLiteralExpr("Hello World!"));
//                                block.addStatement(call);
                    }
                    if (getters) {
                        MethodDeclaration getter = n.addMethod("get" + memberDeclarations.capitolizedName, Modifier.Keyword.PUBLIC);
                        getter.setType(memberDeclarations.t.clone());
                        BlockStmt block = new BlockStmt();
                        getter.setBody(block);

                        // add a statement do the method body
                        FieldAccessExpr f = new FieldAccessExpr(new ThisExpr(), memberDeclarations.name);
                        ReturnStmt ret = new ReturnStmt(f);
                        block.addStatement(ret);
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
        }, null);

        visitor.setOnPackageDeclaration(handlePackage);
        visitor.setOnImportDeclaration(handleImport);
        visitor.setOnThrows(handleThrows);

        cu.accept((VoidVisitor) visitor, null);
        return visitor.toString();
/*
        // This saves all the files we just read to an output directory.
        sourceRoot.saveAll(
                // The path of the Maven module/project which contains the JavaToTypescript class.
                CodeGenerationUtils.mavenModuleRoot(JavaToTypescript.class)
                        // appended with a path to "output"
                        .resolve(Paths.get("output")));
*/
    }

    private PrinterConfiguration getPrinterConfiguration() {
        PrinterConfiguration configuration = new DefaultPrinterConfiguration();
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.SPACE_AROUND_OPERATORS, false));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_JAVADOC, true));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENTATION, new Indentation(Indentation.IndentType.SPACES, this.config.indentation)));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENT_CASE_IN_SWITCH, false));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY, 7));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.END_OF_LINE_CHARACTER, "\n"));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS, true));
        return configuration;
    }

}
