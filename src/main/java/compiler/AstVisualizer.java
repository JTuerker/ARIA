package compiler;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import ast.Node;

public class AstVisualizer {

    private final AstToGraphviz dotGenerator; // The class that converts AST to DOT string

    /**
     * Constructor for AstVisualizer.
     */
    public AstVisualizer() {
        this.dotGenerator = new AstToGraphviz(); // Initialize the DOT generator
    }

    /**
     * Generates a DOT file from the AST and optionally tries to create an image using Graphviz.
     *
     * @param astRoot         The root node of the Abstract Syntax Tree.
     * @param baseOutputName  The base name for the output files (e.g., "src/main/inputFiles/ast").
     *                        .dot and image extensions will be appended.
     * @param imageFormat     The desired image format (e.g., "png", "svg").
     * @param tryExecuteDot   If true, attempt to execute the 'dot' command to generate the image.
     */
    public void visualizeAst(Node astRoot, String baseOutputName, String imageFormat, boolean tryExecuteDot) {
        if (astRoot == null) {
            System.err.println("AST root is null. Visualization skipped.");
            return;
        }

        System.out.println("\n--- Generating Graphviz DOT for AST ---");
        String dotOutput = dotGenerator.generateDot(astRoot);

        String dotFileName = baseOutputName + ".dot";
        String imageFileName = baseOutputName + "." + imageFormat;

        // Write DOT output to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dotFileName))) {
            writer.write(dotOutput);
            System.out.println("Graphviz DOT file written to: " + dotFileName);

            if (tryExecuteDot) {
                System.out.println("\nTo generate the AST image manually, run (if Graphviz is installed):");
                System.out.println("dot -T" + imageFormat + " " + dotFileName + " -o " + imageFileName);
                executeDotCommand(dotFileName, imageFileName, imageFormat);
            }
        } catch (IOException e) {
            System.err.println("Error writing DOT file '" + dotFileName + "': " + e.getMessage());
            // e.printStackTrace(); // Uncomment for more detailed stack trace
        }
    }

    /**
     * Attempts to execute the Graphviz 'dot' command to create an image from a DOT file.
     *
     * @param dotFile     The path to the input .dot file.
     * @param outputFile  The path to the output image file.
     * @param format      The image format for the 'dot' command (e.g., "png", "svg").
     */
    private void executeDotCommand(String dotFile, String outputFile, String format) {
        ProcessBuilder pb = new ProcessBuilder("dot", "-T" + format, dotFile, "-o", outputFile);
        System.out.println("Attempting to execute: " + String.join(" ", pb.command()));
        try {
            Process process = pb.start();

            // Capture and print error stream for debugging 'dot' command issues
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append(System.lineSeparator());
                }
            }

            int exitCode = process.waitFor(); // Wait for the command to complete

            if (exitCode == 0) {
                System.out.println("Graphviz image successfully generated: " + outputFile);
            } else {
                System.err.println("Graphviz 'dot' command failed with exit code " + exitCode + ".");
                System.err.println("Please ensure Graphviz is installed and the 'dot' command is in your system's PATH.");
                if (errorOutput.length() > 0) {
                    System.err.println("Graphviz error output:\n" + errorOutput);
                }
            }
        } catch (IOException e) {
            System.err.println("IOException while trying to execute 'dot' command. Is Graphviz installed and in PATH?");
            System.err.println("Error details: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("'dot' command execution was interrupted.");
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    // Overloaded method for convenience, defaulting to PNG and trying to execute dot
    public void visualizeAst(Node astRoot, String baseOutputName) {
        visualizeAst(astRoot, baseOutputName, "png", true);
    }
}
