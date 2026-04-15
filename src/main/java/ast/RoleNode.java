package ast;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleNode extends Node {
    private final String roleName;
    private final ParametricInfo parametricInfo;
    private final Map<String, String> variablesPerRole;

    public RoleNode(String roleName, ParametricInfo parametricInfo, HashMap<String, String> variablesPerRole){
        this.roleName = roleName;
        this.parametricInfo = parametricInfo;
        this.variablesPerRole = new HashMap<>(variablesPerRole);
    }

    public String getRoleName(){
        return roleName;
    }

   public ParametricInfo getParametricInfo(){
        return this.parametricInfo;
   }

    public Map<String, String> getVariablesPerRole(){
        return variablesPerRole;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("RoleName: " + roleName + "\n");
        if(variablesPerRole.isEmpty()) {
            sb.append("No variables defined in role " + roleName);
            return (sb.toString());
        }
        for(Map.Entry<String, String> entry : variablesPerRole.entrySet()) {
            sb.append("Key: ");
            sb.append(entry.getKey() + "\n");
            sb.append(" Value: ");
            sb.append(entry.getValue());

        }
        sb.append(parametricInfo.toString());
        return sb.toString();
    }

    @Override
    public String getGraphvizLabel() {
        StringBuilder label = new StringBuilder("Role: ");
        label.append(roleName.replace("\"", "\\\"")); // Escape quotes in role name

        if (variablesPerRole != null && !variablesPerRole.isEmpty()) {
            label.append("\\nVars: "); // Newline for variables
            boolean first = true;
            for (Map.Entry<String, String> entry : variablesPerRole.entrySet()) {
                if (!first) {
                    label.append(", ");
                }
                // Escape quotes in var names and values if they can occur
                String key = entry.getKey().replace("\"", "\\\"");
                String val = entry.getValue().replace("\"", "\\\"");
                label.append(key).append("=").append(val).append("\n");
                first = false;
            }
        }
       // label.append(parametricInfo.toString());
            return label.toString();
        }


    @Override
    public List<Node> getChildren() {
        return Collections.emptyList();
    }
}
