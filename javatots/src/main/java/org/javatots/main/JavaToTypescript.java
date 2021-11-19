package org.javatots.main;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
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
import org.javatots.transformers.*;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Read config file, walk java modules there-in, convert them to Typescript.
 */
public class JavaToTypescript {
    // Default configuration to read if none specified on command line.
    protected final static String TEST_CONFIG_PATH = "javatots/src/main/resources/config.yaml";
    // Path from execution root (probably the javatots module directory) to the repo root.
    protected final static String PATH_TO_REPO_ROOT = "../";
    protected final static String TYPESCRIPT_FILE_EXTENSION = "ts";
    protected final String DOT_SLASH = "DOT_SLASHmarkerNoPackageShouldMatch"; // ugly hack to add relative imports to AST.

    // Config controls where to look for Java source and what Typescript src hierarchy to map it to
    protected final JtsConfig config;

    JavaToTypescript(JtsConfig config) {
        this.config = config;
    }

    /**
     * main defaults to the TEST_CONFIG_PATH if you don't specify one.
     * @param args usual java argv structure
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());
        final String configPath = args.length > 0 ? args[0] : TEST_CONFIG_PATH;
        Log.info("Reading YAML configuration from: " + configPath);
        final JtsConfig config = loadConfig(configPath);
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(JavaToTypescript.class).resolve(PATH_TO_REPO_ROOT));
        new JavaToTypescript(config).walkModules(sourceRoot);
    }

    public static JtsConfig loadConfig(final String yamlFilePath) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(yamlFilePath);
        return yaml.loadAs(inputStream, JtsConfig.class);
    }

    /**
     * Walk the modules specified in the config, parse the source, convert to typescript, write to new location.
     * @param sourceRoot a Javaparser SourceRoot, which may be shared with other projects.
     * @throws IOException
     */
    public void walkModules (final SourceRoot sourceRoot) throws IOException {
        for (var moduleMapEntry : this.config.moduleMaps.entrySet()) {
            String javaModuleName = moduleMapEntry.getKey();
            ModuleMap moduleMap = moduleMapEntry.getValue();
            Path javaSrcRootPath = Path.of(this.config.inputDirectory, javaModuleName, moduleMap.srcRoot);
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
                // Find package siblings in case we need to explicitly import them.
                final int pathLength = p.getNameCount();
                final Path dir = p.subpath(0, pathLength - 1);
                final Set<String> siblings = Arrays.stream(files).filter(neighbor ->
                        !neighbor.equals(p)
                        && neighbor.getNameCount() == pathLength
                        && neighbor.subpath(0, pathLength - 1).equals(dir)
                ).map(neighbor -> {
                    String name = String.valueOf(neighbor.getName(pathLength - 1));
                    int dot = name.lastIndexOf('.');
                    return dot == -1 ? name : name.substring(0, dot);
                }).collect(Collectors.toSet());

                // Calculate java and typescript paths.
                final String javaFilepath = String.valueOf(javaSrcRootPath.relativize(p));
                String tsFileName = setExtension(String.valueOf(javaFilepath), TYPESCRIPT_FILE_EXTENSION);

                // Find the first match in PackageMap
                for (PackageMap packageMap: moduleMap.packageMaps) {
                    final String pkgPath = packageMap.getPkgPath();
                    if (javaFilepath.startsWith(pkgPath)) {
                        String destPath = packageMap.destPath == null
                                ? ""
                                : packageMap.destPath;
                        tsFileName = setExtension(String.valueOf(Path.of(destPath, javaFilepath.substring(packageMap.getPkgPath().length()))), TYPESCRIPT_FILE_EXTENSION);
                        break;
                    }
                }

                // TS-ify file
                Path tsFilePath = Path.of(this.config.outputDirectory,moduleMap.outputPath, tsFileName);
                Log.info("-- "  + javaFilepath + " -> " + tsFilePath);
                String transformed = this.transformFile(sourceRoot, String.valueOf(Path.of(String.valueOf(javaSrcRootPath), javaFilepath)), siblings);

                // Write result
                Files.createDirectories(Path.of(new File(String.valueOf(tsFilePath)).getParent()));
                Writer writer = new PrintWriter(String.valueOf(tsFilePath));
                writer.write(transformed);
                writer.close();
            }
        }
    }

    /**
     * Given a filename, change the extension to ext.
     * @param filename
     * @param ext
     * @return
     */
    private static String setExtension(final String filename, final String ext) {
        int idx = filename.lastIndexOf('.');
        return filename.substring(0, idx) + '.' + ext;
    }

    /**
     * Parse `filename`, convert Java source file to Typescript
     * @param sourceRoot
     * @param filename
         * @param siblings
     * @return Typescript-conformant (ideally) file contents.
     */
    public String transformFile(final SourceRoot sourceRoot, final String filename, final Set<String> siblings) {

        // apparently relative to Maven module root
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
            if (pkg.equals(DOT_SLASH)) {
                printer.println("import { " + cls + " } from '" + "./" + cls + "';");
//            } else if (path.startsWith("com.janeirodigital.shapetrees.core")) {
//                printer.println("import {  " + cls + " } from " + pkg + ";");
            } else if (this.config.unknownImportTemplate != null) {
                // check importDecl.isStatic()
                if (importDecl.isAsterisk()) {
                    printer.println("import * as " + cls + "  from '" + pkg + "';");
                } else {
                    printer.println("import { " + cls + " } from '" + pkg + "';");
                }
//                printer.println(String.format(this.config.unknownImportTemplate, cls, pkg));
            }
        };

        final BiConsumer<SourcePrinter, NodeList<ReferenceType>> handleThrows = (final SourcePrinter printer, final NodeList<ReferenceType> throwsList) -> {
            if (this.config.commentThrows) {
                printer.print(" /* throws ");
                Iterator<ReferenceType> i = throwsList.iterator();

                while (i.hasNext()) {
                    ReferenceType name = i.next();
                    name.accept((VoidVisitor) visitor, null);
                    if (i.hasNext()) {
                        printer.print(", ");
                    }
                }
                printer.print(" */");
            }
        };

        final BiConsumer<SourcePrinter, NodeList<AnnotationExpr>> handleMethodAnnotations = (final SourcePrinter printer, final NodeList<AnnotationExpr> annotations) -> {
            String annotationsStr = annotations.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            if ("comment".equals(this.config.unknownAnnotations)) {
                printer.println("// " + annotationsStr);
            } else if ("ignore".equals(this.config.unknownAnnotations)) {
            } else {
                throw new IllegalStateException("Unknown method annotations: " + annotationsStr);
            }
            // could call this on each to get overloaded form: annotation.accept(this, null);
        };

        ArrayList<ModifierVisitor<Void>> preProcessors = new ArrayList<>();
        Set<String> preprocessorNames = new HashSet<String>();
        preProcessors.add(new JavaCoreTypesVisitor());
        preprocessorNames.add("java core");

        Set<String> referencedSiblings = new HashSet<String>();
        cu.accept(new VoidVisitorAdapter<Set<String>>() {
            @Override
            public void visit(final ClassOrInterfaceType n, final Set<String> collector) {
                final String className = n.getName().asString();
                if (siblings.contains(className)) {
                    collector.add(className);
                }
                super.visit(n, collector);
            }
        }, referencedSiblings);

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(final CompilationUnit n, final Void arg) {
                NodeList<ImportDeclaration> imports = new NodeList<>();
                final Iterator<ImportDeclaration> i = n.getImports().iterator();
                while (i.hasNext()) {
                    ImportDeclaration importDecl = i.next();
                    final String path = importDecl.getName().asString();
                    int iName = path.lastIndexOf('.');
                    final String pkg = path.substring(0, iName);
                    final String cls = path.substring(iName + 1);
                    if (pkg.equals("lombok")) {
                        if (!preprocessorNames.contains(pkg)) {
                            preProcessors.add(new DelombokVisitor());
                            preprocessorNames.add(pkg);
                        }
                    } else if (pkg.equals("java.util")) {
                        if (cls.equals("List")) {
                            if (!preprocessorNames.contains("java.util.List")) {
                                preProcessors.add(new JavaListToArrayVisitor());
                                preprocessorNames.add("java.util.List");
                            }
                        } else if (cls.equals("Map")) {
                            ; // do nothing 'cause Map is native to TS.
                        }
                    } else if (pkg.equals("java.io")) {
                        if (cls.equals("FileInputStream")) {
                            if (!preprocessorNames.contains("java.io.FileInputStream")) {
                                preProcessors.add(new JavaFileInputStreamVisitor());
                                preprocessorNames.add("java.io.FileInputStream");
                                imports.add(new ImportDeclaration("fs.Fs", false, true));
                            }
                        } else if (cls.equals("StringWriter")) {
                            if (!preprocessorNames.contains("java.io.StringWriter")) {
                                preProcessors.add(new JavaStringStreamVisitor());
                                preprocessorNames.add("java.io.StringWriter");
                                imports.add(new ImportDeclaration("stream.Writable", false, false));
                            }
                        }
                    } else {
                        imports.add((ImportDeclaration) importDecl.accept(this, arg));
                    }
                }
                for (String s : referencedSiblings) {
                    imports.add(new ImportDeclaration(DOT_SLASH + "." + s, false, false));
                }
                n.setImports(imports);
                return super.visit(n, arg);
            }
        }, null);


        for (ModifierVisitor<Void> preProcessor : preProcessors) {
            cu.accept(preProcessor, null);
        }

        visitor.setOnPackageDeclaration(handlePackage);
        visitor.setOnImportDeclaration(handleImport);
        visitor.setOnThrows(handleThrows);
        visitor.setOnMethodAnnotations(handleMethodAnnotations);

        cu.accept((VoidVisitor) visitor, null);
        return visitor.toString();
    }

    /**
     * Use config and hard-coded values to configure the translator's output.
     * @return
     */
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
