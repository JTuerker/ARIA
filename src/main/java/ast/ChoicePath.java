package ast;

import java.util.ArrayList;
import java.util.List;

public class ChoicePath extends Node{

    private final String rate;
    private final ActionNode action;
    private final Node nextStatement;

    public ChoicePath(String rate, ActionNode action, Node nextStatement){
        this.rate = rate;
        this.action = action;
        this.nextStatement = nextStatement;
    }

    public String getRate(){
        return rate;
    }

    public ActionNode getAction(){
        return action;
    }

    public Node getNextStatement(){
        return this.nextStatement;
    }

    @Override
    public String getGraphvizLabel() {
        StringBuilder sb = new StringBuilder("Choice: ");
        sb.append(rate);
        if(action != null){
            sb.append("\\nAction: " + action);
            }
            if(nextStatement != null){
                sb.append("\\nNext Statement: ").append(nextStatement.getClass().getSimpleName());
            }
        return sb.toString();
    }

    @Override
    public List<Node> getChildren() {
        return List.of(nextStatement);
    }
}
