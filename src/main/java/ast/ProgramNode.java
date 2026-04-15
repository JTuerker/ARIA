package ast;

import java.util.ArrayList;
import java.util.List;

public class ProgramNode extends Node {

    private final PreambleNode preamble;
    private final ArrayList<RoleNode> roles;
    private final ArrayList<ProtocolNode> protocolDefinitions;

    public ProgramNode(PreambleNode preamble, ArrayList<RoleNode> roles, ArrayList<ProtocolNode> protocolDefinitions) {
        this.preamble = preamble;
        this.roles = new ArrayList<>(roles);
        this.protocolDefinitions = protocolDefinitions != null ? new ArrayList<>(protocolDefinitions) : new ArrayList<>();
    }

    public PreambleNode getPreamble() {
        return preamble;
    }

    public ArrayList<ProtocolNode> getProtocols() {
        return this.protocolDefinitions;
    }

    public ArrayList<RoleNode> getRoles() {
        return this.roles;
    }

    @Override
    public String toString() {
        return "Preamble: " + preamble + "\n" +
                "protocols: " + protocolDefinitions +
                "roles: " + roles;
    }


    @Override
    public String getGraphvizLabel() {
        return "Program";
    }

    @Override
    public List<Node> getChildren() {
        List<Node> children = new ArrayList<>();
        if (preamble != null) {
            children.add(preamble);
        }
        if (roles != null) {
            children.addAll(roles);
        }
        children.addAll(protocolDefinitions);
        return children;
    }
}
