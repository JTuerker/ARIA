package ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BranchNode extends Node {

    private final String sender;
    private final String receiver;
    private final List<ChoicePath> choices;

    public BranchNode(String from, String to, List<ChoicePath> choices){
        this.sender = from;
        this.receiver = to;
        this.choices = (choices != null) ? new ArrayList<>(choices) : new ArrayList<>();
    }


    public String toString(){
        StringBuilder sb = new StringBuilder();
        for (Node el : choices){
            sb.append(el.toString()).append(", ");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

   public boolean isSelfInteraction() {
        return receiver.equals(sender);
    }


    @Override
    public String getGraphvizLabel() {
        StringBuilder sb = new StringBuilder("BranchNode\n");
        sb.append("From: " + sender + "\nTo: ");
        sb.append(receiver);
        sb.append("\n");
        if (choices != null && !choices.isEmpty()) {
            if (choices.size() == 1) {
                sb.append("Communication Statement\n");
                for(ChoicePath choice : choices){
                    sb.append(choice.getRate());
                    sb.append("\n");
                    sb.append(choice.getAction().toString());
                    sb.append("\n");
                }
            } else if (choices.size() > 1) {
                sb.append("Branch Statement\n");
                for(ChoicePath choice : choices){
                    sb.append(choice.getRate()).append("\n");
                    sb.append(choice.getAction().toString()).append("\n");
                }
            }
        }
        return sb.toString();
    }


    @Override
    public List<ChoicePath> getChildren() {
        return Collections.unmodifiableList(choices);
    }

}