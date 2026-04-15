package ast;

import java.util.ArrayList;
import java.util.List;


public class InternalActionNode extends Node{

    private final String rate;
    private final String role;
    private final List<String> messages;
    private final Node nextStatement;

    public InternalActionNode(String rate, String role, List<String> messages, Node statement){
        this.rate = rate;
        this.role = role;
        this.messages = messages.isEmpty()?  new ArrayList<>() : new ArrayList<>(messages);
        this.nextStatement = statement;
    }


    public String getRate() {
        return rate;
    }

    public String getRole(){
        return role;
    }

    public List<String> getMessages(){
        return this.messages;
    }
    public Node getNextStatement() {
        return nextStatement;
    }

    @Override
    public String getGraphvizLabel() {
        StringBuilder sb = new StringBuilder("InternalActionNode\n");
        sb.append("Rate: " + rate + "\n");
        sb.append("Variable Updates: ");
        sb.append(role + " ");
        for(String s : messages){
            sb.append(s + " ");
        }
        return sb.toString();
    }

    @Override
    public List<? extends Node> getChildren() {
        return List.of(nextStatement);
    }
}
