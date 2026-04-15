package ast;

import java.util.ArrayList;
import java.util.List;

public class ProtocolNode extends Node{

	private final String id;
	private final ArrayList<Node> statements ;
	
	ProtocolNode(String id, ArrayList<Node> statements){
		this.id = id;
		this.statements = new ArrayList<>(statements);
	}

	public String getName(){
		return id;
	}


	@Override
	public String toString() {
		return id;
	}

	@Override
	public String getGraphvizLabel() {
		return "Protocol " + id;
	}

	@Override
	public List<Node> getChildren() {
		return statements;
	}
}
