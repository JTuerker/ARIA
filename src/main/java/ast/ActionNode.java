package ast;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActionNode extends Node{

    private RoleNode roleFrom;
    private RoleNode roleTo;
    private final ArrayList<String> actionA;
    private final ArrayList<String> actionB;
    private boolean isResolved = false;

    public ActionNode(ArrayList<String> actionA, ArrayList<String> actionB) {
        this.actionA = actionA.isEmpty() ? new ArrayList<>() : new ArrayList<>(actionA);
        this.actionB = actionB.isEmpty() ? new ArrayList<>() : new ArrayList<>(actionB);
    }

    public String toString(){
        StringBuilder sb = new StringBuilder("ActionA: ");
        for(String s : actionA){
           sb.append(s + ", ");
        }
        sb.deleteCharAt(sb.length() - 2);
        sb.append("\nActionB: ");
        for(String s : actionB){
            sb.append(s + ", ");
        }
        sb.deleteCharAt(sb.length() - 2);
        return sb.toString();
    }

    @Override
    public List<? extends Node> getChildren() {
        //ActionNode has no children
        return List.of();
    }

    public boolean isResolved() {
        return this.isResolved;
    }

    public void setResolved(boolean resolved) {
        this.isResolved = resolved;
    }

    public ArrayList<String> getAllActionsA() {
        return actionA;
    }

    public ArrayList<String> getAllActionB(){
        return actionB;
    }

    public ArrayList<String> getAllActionStrings(){
        return Stream.concat(actionA.stream(), actionB.stream()).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public String getGraphvizLabel() {
      StringBuilder label = new StringBuilder("ActionA:\n");
      if(!actionA.isEmpty()){
          for(String s: actionA){
              label.append(s + "\n");
          }
      }
        if (!actionB.isEmpty()) {
            label.append("ActionB\n");
            for(String s : actionB){
                label.append(s + "\n");
            }
        }
        return label.toString();
    }

}
