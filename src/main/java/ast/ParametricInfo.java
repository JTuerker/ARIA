package ast;

import java.util.Map;

public record ParametricInfo (String lowerBound, String upperBound, Map<String, String> constants){

public Integer parseUpperBound(String upperBound){
    if(constants.get(upperBound) != null){
        return Integer.parseInt(constants.get(upperBound));
    }
    else{
        System.err.println("Constant not defined");
        return null;
    }
}


}
