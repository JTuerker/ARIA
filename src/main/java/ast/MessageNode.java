package ast;

import java.util.Arrays;
import java.util.List;

public class MessageNode extends Node {

    private final ActionNode action;
    private final List<Node> parts;


    public MessageNode(ActionNode action, List<Node> parts) {
        this.action = action;
        this.parts = parts;
    }

    public ActionNode getActions(){
        return action;
    }

    public List<Node> getParts() {
        return parts;
    }


    public String toString() {
        return Arrays.toString(parts.toArray());
    }

    @Override
    public String getGraphvizLabel() {
        return Arrays.toString(parts.toArray());
    }

    @Override
    public List<ActionNode> getChildren() {
        return List.of();
    }
}
