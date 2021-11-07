package org.javatots.main;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.printer.SourcePrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.Indentation;
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
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Some code that uses JavaParser.
 */
public class JavaToTypescript {
    private static final String JAVA_LIBRARY_NAME = "shapetrees-java";

    public static void main(String[] args) throws IOException {
        final JavaToTypescript jtos = new JavaToTypescript();
        final String defaultConfigPath = "javatots/src/main/resources/config.yaml";
        final JtsConfig config = jtos.loadConfig(defaultConfigPath);
        System.out.println("config: " + config);
        for (var moduleMapEntry : config.moduleMaps.entrySet()) {
            String javaModuleName = moduleMapEntry.getKey();
            ModuleMap moduleMap = moduleMapEntry.getValue();
            String javaSrcRootStr = config.inputDirectory + javaModuleName + "/" + moduleMap.srcRoot;
            System.out.println("\nMapping: " + javaSrcRootStr);

            // make a list of the java files in this package
            Path[] files = Files.find(
                        Paths.get(javaSrcRootStr),
                        Integer.MAX_VALUE,
                        (filePath, fileAttr) -> fileAttr.isRegularFile()
                    ).toArray(Path[]::new)
//                    .forEach(System.out::println)
            ;

            // iterate
            Path javaSrcRootPath = Paths.get(javaSrcRootStr);
            for (Path p: files) {
                String javaFilepath = String.valueOf(javaSrcRootPath.relativize(p));
                String tsFileName = String.valueOf(javaFilepath);

                // Find the first match PackageMap
                for (PackageMap packageMap: moduleMap.packageMaps) {
                    final String pkgPath = packageMap.getPkgPath();
                    if (javaFilepath.startsWith(pkgPath)) {
                        String destPath = packageMap.destPath == null
                                ? ""
                                : packageMap.destPath;
                        tsFileName = String.valueOf(Path.of(destPath, javaFilepath.substring(packageMap.getPkgPath().length())));
                        break;
                    }
                }

                Path tsFilePath = Path.of(config.outputDirectory,moduleMap.outputPath, tsFileName);
                System.out.println("-- "  + javaFilepath + " -> " + tsFilePath);
                String transformed = new JavaToTypescript().transformFile("../" + javaSrcRootStr, javaFilepath);
//                System.out.println(transformed);
            }
        }

        final String filename = "org/javatots/example/customerdb/Cli.java";
    }

    public JtsConfig loadConfig(final String yamlFilePath) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(yamlFilePath);
        return yaml.loadAs(inputStream, JtsConfig.class);
    }

    public String transformFile(final String moduleDirectory, final String filename) {
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());

        // apparently relative to Maven module root
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(JavaToTypescript.class).resolve(moduleDirectory));
        CompilationUnit cu = sourceRoot.parse("", filename);

        Log.info("Porting file " + filename + ":");

        DefaultPrinterConfiguration configuration = new DefaultPrinterConfiguration();
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.SPACE_AROUND_OPERATORS, false));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_JAVADOC, true));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENTATION, new Indentation(Indentation.IndentType.SPACES, 2)));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENT_CASE_IN_SWITCH, false));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY, 7));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.END_OF_LINE_CHARACTER, "\n"));
        configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS, true));

        final BiConsumer<SourcePrinter, Name> handlePackage = (final SourcePrinter printer, final Name packageName) -> {
            printer.println("// Corresponding " + JAVA_LIBRARY_NAME + " package: " + packageName);
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
            } else if (path.startsWith("com.janeirodigital.shapetrees.foo")) {
                printer.println("// Corresponding " + JAVA_LIBRARY_NAME + " package: " + importDecl);
            }
        };

        TypescriptPrettyPrinter visitor = new TypescriptPrettyPrinter(configuration, cu.getPackageDeclaration());
        visitor.setOnPackageDeclaration(handlePackage);
        visitor.setOnImportDeclaration(handleImport);

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
}
