package ast;

import java.util.ArrayList;
import java.util.List;

public class LoopNode extends Node {
    private final char iteratorVariable; // Das 'index' (z.B. 'i')
    private final String operator;       // Das 'op' (z.B. '=', '<=')
    private final char upperBound;       // Das 'upperBound' (meistens eine Konstante wie 'N')
    private final String actionTemplate; // Der 'DOUBLE_STRING' (z.B. "v[i]'=v[i-1]")
    private final String targetRole;     // Die 'role' (z.B. "p[i]")

    public LoopNode(char iteratorVariable, String operator, char upperBound,
                    String actionTemplate, String targetRole) {
        this.iteratorVariable = iteratorVariable;
        this.operator = operator;
        this.upperBound = upperBound;
        this.actionTemplate = actionTemplate;
        this.targetRole = targetRole;
    }

    // Getters
    public char getIteratorVariable() {
        return iteratorVariable;
    }

    public String getOperator() {
        return operator;
    }

    public char getUpperBound() {
        return upperBound;
    }

    public String getActionTemplate() {
        return actionTemplate;
    }

    public String getTargetRole() {
        return targetRole;
    }

    @Override
    public String getGraphvizLabel() {
        return "Loop";
    }

    @Override
    public List<Node> getChildren() {
        // Ein LoopNode hat in dieser Grammatik meist keine AST-Kinder,
        // da er Teil einer 'message' ist.
        return new ArrayList<>();
    }

    @Override
    public String toString() {
        return String.format("foreach(%s %s %s) \"%s\" AT %s",
                iteratorVariable, operator, upperBound, actionTemplate, targetRole);
    }
}