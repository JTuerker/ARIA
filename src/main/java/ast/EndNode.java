package ast;

import java.util.ArrayList;
import java.util.List;

public class EndNode extends Node {


    @Override
    public String getGraphvizLabel() {
        return "End";
    }

    @Override
    public List<? extends Node> getChildren() {
        return new ArrayList<>();
    }
}
