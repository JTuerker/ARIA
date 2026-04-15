package ast;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreambleNode extends Node {
	//TODO: parse variable declarations
	private final boolean isCtmc ;

	private final Map<String, String> constants;
	
	public PreambleNode(boolean isCtmc, Map<String, String> constants) {

		this.isCtmc = isCtmc;
		this.constants = constants.isEmpty() ? new HashMap<>() : new HashMap<>(constants);
	}
	
	public boolean isCtmc() {
		return this.isCtmc;
	}

	public String getModelType(){
		return isCtmc() ? "ctmc" : "dtmc";
	}

	public Map<String, String> getConstants(){
		return this.constants;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("isCtmc? " + this.isCtmc() + "\n");
		for(Map.Entry<String, String> entry : constants.entrySet()){
			sb.append(entry.getKey() + " = " + entry.getValue() + "\n");
		}
		return sb.toString();
	}


	@Override
	public String getGraphvizLabel() {
		StringBuilder label = new StringBuilder();
        label.append("Preamble: " + (isCtmc ? "CTMC" : "DTMC")).append("\n");
        for(Map.Entry entry : constants.entrySet()){
            label.append(entry.getKey() + " = ").append(entry.getValue() + "\n");
        }
        return label.toString();
	}

	@Override
	public List<? extends Node> getChildren() {
		return Collections.emptyList();
	}
}
