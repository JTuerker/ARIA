package compiler;

import ast.ChoicePath;
import ast.Node;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AstToGraphviz {
    private final StringBuilder dotBuilder;
    private final AtomicInteger nodeIdCounter; // Für eindeutige Knoten-IDs in Graphviz

    public AstToGraphviz() {
        this.dotBuilder = new StringBuilder();
        this.nodeIdCounter = new AtomicInteger(0); // Zähler für eindeutige Knoten-IDs
    }

    /**
     * Generiert eine DOT-Sprachrepräsentation des gegebenen ASTs.
     *
     * @param rootNode Der Wurzelknoten des ASTs.
     * @return Ein String, der die DOT-Beschreibung des ASTs enthält.
     */
    public String generateDot(Node rootNode) {
        dotBuilder.setLength(0); // Inhalt von vorherigen Aufrufen löschen
        nodeIdCounter.set(0);    // Zähler zurücksetzen

        dotBuilder.append("digraph AST {\n");
        // Standard-Styling für Knoten (optional, kann angepasst werden)
        dotBuilder.append("  node [shape=box, style=\"rounded,filled\", fillcolor=\"white\"];\n");
        // Standard-Styling für Kanten (optional)
        // dotBuilder.append("  edge [arrowhead=vee];\n");

        // Starte die rekursive Traversierung des Baumes
        traverse(rootNode, -1); // -1 bedeutet, dass der Wurzelknoten keinen Elternknoten hat

        dotBuilder.append("}\n");
        return dotBuilder.toString();
    }

    /**
     * Rekursive Methode, um den AST zu traversieren und DOT-Definitionen zu generieren.
     *
     * @param node         Der aktuell zu besuchende Knoten.
     * @param parentNodeId Die ID des Elternknotens in der DOT-Datei (-1, wenn es keinen gibt).
     * @return Die ID des aktuell verarbeiteten Knotens in der DOT-Datei.
     */
    private int traverse(Node node, int parentNodeId) {
        if (node == null) {
            // Optional: einen "null" Knoten darstellen oder einfach ignorieren
            // int nullNodeId = nodeIdCounter.getAndIncrement();
            // dotBuilder.append(String.format("  node%d [label=\"NULL\", shape=ellipse, fillcolor=\"lightgrey\"];\n", nullNodeId));
            // if (parentNodeId != -1) {
            //     dotBuilder.append(String.format("  node%d -> node%d [style=dotted];\n", parentNodeId, nullNodeId));
            // }
            // return nullNodeId;
            return -1; // Oder einfach ignorieren
        }
        if (node instanceof ChoicePath) {
            // Erzeuge keinen eigenen Knoten für ChoicePath.
            // Stattdessen, besuche die Kinder und verbinde sie direkt mit dem Elternteil (parentNodeId).
            List<Node> children = (List<Node>) node.getChildren();
            if (children != null) {
                for (Node child : children) {
                    // Wichtig: Gib die 'parentNodeId' des ChoicePath-Knotens an seine Kinder weiter.
                    traverse(child, parentNodeId);
                }
            }
            // Da kein neuer Knoten erstellt wurde, geben wir die ID des Elternteils zurück.
            // Dieser Rückgabewert wird in diesem Zweig nicht weiter verwendet.
            return parentNodeId;

        } else {

            int currentNodeId = nodeIdCounter.getAndIncrement();

            // Hole das Label vom Knoten. Wichtig: Anführungszeichen im Label müssen escaped werden.
            String label = node.getGraphvizLabel();
            if (label == null) {
                label = "Error: Null Label"; // Fallback, falls getGraphvizLabel() null zurückgibt
            }
            label = label.replace("\"", "\\\""); // Escaped doppelte Anführungszeichen

            // Definiere den aktuellen Knoten im DOT-Format
            dotBuilder.append(String.format("  node%d [label=\"%s\"];\n", currentNodeId, label));

            // Wenn es nicht der Wurzelknoten ist, erstelle eine Kante vom Elternknoten zum aktuellen Knoten
            if (parentNodeId != -1) {
                dotBuilder.append(String.format("  node%d -> node%d;\n", parentNodeId, currentNodeId));
            }


            // Besuche rekursiv alle Kinder
            List<Node> children = (List<Node>) node.getChildren();
            if (children != null) {
                for (Node child : children) {
                    // Wichtig: Überprüfe auf null-Kinder
                    // Optional: explizit einen "null" Kindknoten darstellen (siehe oben)
                    traverse(child, currentNodeId);
                }
            }
            return currentNodeId;
        }
    }
}


