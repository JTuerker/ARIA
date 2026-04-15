package ast;

import java.util.List;

public class RecNode extends Node{

	private final String protocolName ;
	
	RecNode(String name){
		this.protocolName = name;
	}
	
	@Override
	public String toString() {
		return protocolName;
	}


    public String getName() {
		return protocolName;
    }

	@Override
	public String getGraphvizLabel() {
		return "Call " + protocolName;
	}

	@Override
	public List<? extends Node> getChildren() {
		return List.of();
	}
}
