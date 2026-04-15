package ast;

import java.util.List;

public abstract class Node {

    public abstract String getGraphvizLabel();

    public abstract List<? extends Node> getChildren();



}
