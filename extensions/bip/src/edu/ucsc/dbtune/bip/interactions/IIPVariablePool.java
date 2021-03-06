package edu.ucsc.dbtune.bip.interactions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import edu.ucsc.dbtune.bip.core.AbstractBIPVariablePool;
import edu.ucsc.dbtune.bip.core.BIPVariable;
import edu.ucsc.dbtune.util.Strings;

public class IIPVariablePool extends AbstractBIPVariablePool 
{
    public static final int  VAR_Y = 0;
    public static final int  VAR_X = 1;
    public static final int  VAR_S = 2;
    public static final int  VAR_U = 3;
    public static final int  VAR_DEFAULT = 100;    
    private String[] strTheta = {"empty", "c", "d", "cd"};
    private String[] strHeaderVariable = {"y", "x", "s", "u"};    
    private Map<IIPVariableIndicator, BIPVariable> mapHighDimensionVar;
    
    public IIPVariablePool()
    {
        mapHighDimensionVar = new HashMap<IIPVariableIndicator, BIPVariable>();
    }
    
    /**
     * Clean all the data stored in this class. 
     * Reset the ID. 
     */
    public void clear()
    {
        mapHighDimensionVar.clear();
        BIPVariable.IN_MEMORY_ID = new AtomicInteger(0);
    }
    
    /**
     * 
     * Create and store a variable, whose name is in the form of
     * y(empty, k), x(c,k,a), s(d, a), or u(cd, k, a).
     *  
     * @param theta
     *      The value is in the set of {empty, c, d, cd}
     * @param typeVarible
     *      The type of variable, the value is in the set {y, x, u, s}
     * @param q
     *      The identifier of the statement     
     * @param k
     *      The identifier of the corresponding template plan 
     *      if {@code typeVariable} = VAR_Y, VAR_X, VAR_U 
     * @param a 
     *      The index ID when {@code typeVariable} = VAR_X, VAR_U, VAR_S
     * 
     * @return
     *      The variable name
     */
    public BIPVariable createAndStore(int theta, int typeVariable, int q, int k, int a)
    {
        StringBuilder varName = new StringBuilder();
        varName.append(strHeaderVariable[typeVariable]);
        varName.append("(");
        
        List<String> nameComponent = new ArrayList<String>();        
        nameComponent.add(strTheta[theta]);   
        nameComponent.add(Integer.toString(q));
        
        if (typeVariable == VAR_Y || typeVariable == VAR_X || typeVariable == VAR_U)
            nameComponent.add(Integer.toString(k));
        
        if (typeVariable == VAR_X || typeVariable == VAR_U || typeVariable == VAR_S) 
            nameComponent.add(Integer.toString(a));
        
        varName.append(Strings.concatenate(",", nameComponent));
        varName.append(")");
                
        BIPVariable var = new BIPVariable(varName.toString(), typeVariable);
        add(var); 
        
        // Create a mapping  
        IIPVariableIndicator iai = new IIPVariableIndicator(theta, typeVariable, q, k, a);
        mapHighDimensionVar.put(iai, var);
        
        return var;
    }
    
    /**
     * Get the corresponding variable
     * 
     * @param theta
     *      EMPTY, C,D, or CD
     * @param typeVariable
     *      either Y, X, S, or U
     * @param k
     *      template plan ID
     * @param a
     *      index ID
     * @return
     *      BIP Variable
     */
    public BIPVariable get(int theta, int typeVariable, int q, int k, int a)
    {
        // Create a mapping from 
        IIPVariableIndicator iai = new IIPVariableIndicator(theta, typeVariable, q, k, a);
        return mapHighDimensionVar.get(iai);
    }
}
