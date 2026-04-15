package ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IfThenElseNode extends Node {
    private final HashMap<String, ArrayList<String>> conditionsPerRole;
    private final Node thenStatement;
    private final Node elseStatement;

    public IfThenElseNode(HashMap<String, ArrayList<String>> conditionsPerRole, Node thenStatement, Node elseStatement){
        this.conditionsPerRole = conditionsPerRole.isEmpty()? new HashMap<>() : new HashMap<>(conditionsPerRole);
        this.thenStatement = thenStatement;
        this.elseStatement = elseStatement;
    }

    @Override
    public String getGraphvizLabel() {
        StringBuilder sb  = new StringBuilder("IfThenElse\n");
        for(Map.Entry<String, ArrayList<String>> entry : conditionsPerRole.entrySet()){
            String role = entry.getKey().trim();
            for(String s: entry.getValue()){
                sb.append(s.trim() + " && ");
            }
            sb.delete(sb.length() - 3, sb.length() - 1);
            sb.append(" @role " + role);
        }
        return sb.toString();
    }

    public HashMap<String, ArrayList<String>> getConditionsPerRole(){return this.conditionsPerRole;}

    public Node getThenStatement(){
        return this.thenStatement;
    }

    public Node getElseStatement(){
        return this.elseStatement;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> children = new ArrayList<>();

        if (this.thenStatement != null) {
            children.add(this.thenStatement);
        }

        if (this.elseStatement != null) {
            children.add(this.elseStatement);
        }

        return children;
    }
}