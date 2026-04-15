import ast.LanguageVisitorImpl;
import ast.ProgramNode;
import lib.ModelExporter;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import parser.LanguageLexer;
import parser.LanguageParser;

import compiler.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length == 0 ? "src/main/inputFiles/example4" : args[0];
        FileInputStream is = new FileInputStream(fileName + ".language");
        CharStream input = CharStreams.fromStream(is);
        LanguageLexer lexer = new LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LanguageParser parser = new LanguageParser(tokens);
        ParseTree t = parser.protocol();
        LanguageVisitorImpl visitor = new LanguageVisitorImpl();
        ProgramNode root = (ProgramNode) visitor.visit(t);


        // --- AST Visualisierung ---
        if (root != null) {
            AstVisualizer visualizer = new AstVisualizer();
            String astOutputBaseName = args.length == 0 ? "src/main/inputFiles/ast_visualization/example4" : args[1];
            // visualizer.visualizeAst(root, astOutputBaseName); // Defaults to PNG, tries to execute dot
            visualizer.visualizeAst(root, astOutputBaseName, "png", true); // Example: SVG, execute dot
        } else {
            System.err.println("AST root is null after parsing. Visualization and model building skipped.");
        }


          if (root != null) {
              MatrixBuilderOnTheFlyWithStateVectors lastBuilder = null;
                int measurementRuns = 1;
                List<Double> durationMeasurement = new ArrayList<>();
              System.out.println(".................................................Starting Measurement..........");
            for(int i = 0; i < measurementRuns; i++) {
                long startTime = System.nanoTime();

                lastBuilder = new MatrixBuilderOnTheFlyWithStateVectors(root);
                lastBuilder.build(true,fileName + ".out");
                long endTime = System.nanoTime();
                long durationInNanos = endTime - startTime;
                double durationInSeconds = durationInNanos / 1000000000.0;
                durationMeasurement.add(durationInSeconds);
                System.out.printf("Time taken: %.4f seconds%n", durationInSeconds);
            }
                double average = durationMeasurement.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName + "Time.out"))) {
                    bw.write("Time taken (avg): " + average + " seconds\n");
                    bw.write("States found: " + lastBuilder.getNumStates() + "\n");
                    bw.write("Number of Transitions: " + lastBuilder.getTransitionCount() + "\n");
                    bw.write("\nDetailed Runs:\n");
                    for(int i = 0; i < durationMeasurement.size(); i++){
                        bw.write(String.format("Run %d: %.6f seconds\n", i + 1, durationMeasurement.get(i)));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println("Writing .tra and .sta files");
              ModelExporter.writePrismStyle(fileName, lastBuilder.getNumStates(), lastBuilder.getTransitionCount(),lastBuilder.getStateToIdMap(), lastBuilder.getProcessNames(), lastBuilder.getVariableIndexMap());
            } else {
                System.err.println("AST konnte nicht erstellt werden.");
            }
        }
    }








