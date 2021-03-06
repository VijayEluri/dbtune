package edu.ucsc.dbtune.bip.sim;

import edu.ucsc.dbtune.util.HashCodeUtil;


public class SimVariableIndicator
{
    private int typeVariable, window, q, k, a;
    private int fHashCode;
    
    /**
     * A {@code SimVarible} is defined based on the following
     * five parameters
     * 
     * @param _type
     *      The type of variable (e.g., VAR_CREATE, VAR_DROP)
     * @param _window
     *      The maintenance window
     * @param _q
     *      The statement ID
     * @param _k
     *      The query plan ID
     * @param _a
     *      The ID of the index
     */
    public SimVariableIndicator(int _type, int _window, int _q, int _k, int _a)
    {
        this.typeVariable = _type;
        this.window = _window;
        this.q = _q;
        this.k = _k;
        this.a = _a;
        fHashCode = 0;
    }
    

    @Override
    public boolean equals(Object obj) 
    {
        if (!(obj instanceof SimVariableIndicator))
            return false;

        SimVariableIndicator var = (SimVariableIndicator) obj;
        
        if ( (this.typeVariable != var.typeVariable) ||   
             (this.window != var.window) ||
             (this.q != var.q) ||  
             (this.k != var.k) ||
             (this.a != var.a) ) {
            return false;
        }
        
        return true;
    }

    @Override
    public int hashCode() 
    {
        if (fHashCode == 0) {
            int result = HashCodeUtil.SEED;
            result = HashCodeUtil.hash(result, this.typeVariable);
            result = HashCodeUtil.hash(result, this.window);
            result = HashCodeUtil.hash(result, this.q);
            result = HashCodeUtil.hash(result, this.k);
            result = HashCodeUtil.hash(result, this.a);
            fHashCode = result;
        }
        
        return fHashCode;
    }
}
