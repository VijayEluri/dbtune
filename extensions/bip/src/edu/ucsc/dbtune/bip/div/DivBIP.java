package edu.ucsc.dbtune.bip.div;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


import edu.ucsc.dbtune.bip.core.AbstractBIPSolver;
import edu.ucsc.dbtune.bip.core.IndexTuningOutput;
import edu.ucsc.dbtune.bip.core.QueryPlanDesc;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.util.Strings;

public class DivBIP extends AbstractBIPSolver
{  
    protected int Nreplicas, loadfactor;    
    protected double B;
    protected List<String> listCrq;
    protected DivVariablePool poolVariables;
    // Map variable of type CREATE or DROP to the indexes
    protected Map<String,Index> mapVarSToIndex;
    
    public DivBIP(int Nreplicas, int loadfactor, double B)
    {
        this.Nreplicas = Nreplicas;
        this.loadfactor = loadfactor;
        this.B = B;
    }
    
    
    /**
     * The method reads the value of variables corresponding to the presence of indexes
     * at each replica and returns this list of indexes to materialize at each replica
     * 
     * @return
     *      List of indexes to be materialized at each replica
     */
    @Override
    protected IndexTuningOutput getOutput() 
    {
        DivRecommendedConfiguration conf = new DivRecommendedConfiguration(this.Nreplicas);
        
        // Iterate over variables s_{i,w}
        // Iterate over variables create_{i,w} and drop_{i,w}
        for (Entry<String, Integer> pairVarVal : super.mapVariableValue.entrySet()) {
            if (pairVarVal.getValue() == 1) {
                String name = pairVarVal.getKey();
                DivVariable divVar = (DivVariable) this.poolVariables.get(name);
                
                if (divVar.getType() == DivVariablePool.VAR_S){
                    Index index = this.mapVarSToIndex.get(name);
                    // TODO: only record the normal indexes
                    conf.addMaterializedIndexAtReplica(divVar.getReplica(), index);
                }
            }
        }
        return conf;
    }

    
   
    /**
     * The function builds the BIP Divergent Index Tuning Problem:
     * <p>
     * <ol>
     *  <li> Atomic constraints </li>
     *  <li> Top-m best cost constraints </li>
     *  <li> Space constraints </li>
     * </ol>
     * </p>   
     * 
     */
    @Override
    protected void buildBIP() 
    {
        numConstraints = 0;
        
        // 1. Add variables into list
        constructVariables();
        
        // 2. Construct the query cost at each replica
        buildQueryCostReplica();
        
        // 3. Atomic constraints
        buildAtomicInternalPlanConstraints();
        buildAtomicIndexAcessCostConstraints();      
        
        // 4. Top-m best cost 
        buildTopmBestCostConstraints();
        
        // 5. Space constraints
        buildSpaceConstraints();
        
        // 6. Optimal constraint
        buildObjectiveFunction();
        
        // 7. binary variables
        binaryVariableConstraints();
        
        try {
            buf.writeToLpFile();
        } catch (IOException e) {
            throw new RuntimeException("Cannot concantenate text files that store BIP.");
        }
        
    }
    
    /**
     * Add all variables into the pool of variables of this BIP formulation
     *  
     */
    protected void constructVariables()
    {   
        this.poolVariables = new DivVariablePool();
        this.mapVarSToIndex = new HashMap<String, Index>();
        
        // for TYPE_Y, TYPE_X
        for (QueryPlanDesc desc : listQueryPlanDescs){
            int q = desc.getStatementID();
            for (int r = 0; r < Nreplicas; r++) {
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
                    poolVariables.createAndStoreBIPVariable(DivVariablePool.VAR_Y, r, q, k, 0, 0);
                }    
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {              
                    for (int i = 0; i < desc.getNumberOfSlots(); i++) {  
                        for (Index index : desc.getListIndexesAtSlot(i)) {
                            poolVariables.createAndStoreBIPVariable(DivVariablePool.VAR_X, r, q, k, i, index.getId());
                        }
                    }
                }       
            }
        }
        
        // for TYPE_S
        for (Index index : candidateIndexes) {           
            for (int r = 0; r < Nreplicas; r++) {
                DivVariable var = poolVariables.createAndStoreBIPVariable(DivVariablePool.VAR_S, r, index.getId(), 0, 0, 0);
                this.mapVarSToIndex.put(var.getName(), index);
            }
        }
    }
    
    /**
     * Build cost function of each query in each window w
     * Cqr = \sum_{k \in [1, Kq]} \beta_{qk} y(r,q,k) + 
     *      + \sum_{ k \in [1, Kq] \\ i \in [1, n] \\ a \in S_i \cup I_{\emptyset} x(r,q,k,i,a) \gamma_{q,k,i,a}
     * {\b Note}: Add variables of type Y, X, S into the list of variables     
     */
    protected void buildQueryCostReplica()
    {         
        this.listCrq = new ArrayList<String>();
        for (QueryPlanDesc desc : listQueryPlanDescs){
            int q = desc.getStatementID();
            
            for (int r = 0; r < Nreplicas; r++) {
                // Internal plan
                List<String> linList = new ArrayList<String>();            
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
                    String var = poolVariables.getDivVariable(DivVariablePool.VAR_Y, r, q, k, 0, 0).getName();
                    linList.add(Double.toString(desc.getInternalPlanCost(k)) + var); 
                }
                String Cwq  = Strings.concatenate(" + ", linList);          
                        
                // Index access cost
                linList.clear();            
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {              
                    for (int i = 0; i < desc.getNumberOfSlots(); i++) {  
                        for (Index index : desc.getListIndexesAtSlot(i)) {
                            String var = poolVariables.getDivVariable(DivVariablePool.VAR_X, r, q, k, i, index.getId()).getName();
                            linList.add(Double.toString(desc.getAccessCost(k, index)) + var);     
                        }
                    }
                }       
                Cwq = Cwq + " + " + Strings.concatenate(" + ", linList);
                listCrq.add(Cwq);
            }
        }
    }
    
    
    
    /**
     * Constraints on internal plans: different from INUM
     */
    protected void buildAtomicInternalPlanConstraints()
    {   
        for (QueryPlanDesc desc : listQueryPlanDescs){
            int q = desc.getStatementID();
        
            for (int r = 0; r < Nreplicas; r++) {
                List<String> linList = new ArrayList<String>();
                // \sum_{k \in [1, Kq]}y^{r}_{qk} <= 1
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
                    linList.add(poolVariables.getDivVariable(DivVariablePool.VAR_Y, r, q, k, 0, 0).getName());
                }
                buf.getCons().add("atomic_2a_" + numConstraints + ": " + Strings.concatenate(" + ", linList) + " <= 1");
                numConstraints++;
            }
        }
    }
    
    /**
     * 
     * Index access cost and the presence of an index constraint
     * 
     */
    protected void buildAtomicIndexAcessCostConstraints()
    {   
        for (QueryPlanDesc desc : listQueryPlanDescs){
            int q = desc.getStatementID();
        
            for (int r = 0; r < Nreplicas; r++) {
                List<String> linList = new ArrayList<String>();
                
                // \sum_{a \in S_i} x(r, q, k, i, a) = y(r, q, k)
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
                    String var_y = poolVariables.getDivVariable(DivVariablePool.VAR_Y, r, q, k, 0, 0).getName();
                    
                    for (int i = 0; i < desc.getNumberOfSlots(); i++) {            
                        linList.clear();
                        for (Index index : desc.getListIndexesAtSlot(i)) { 
                            String var_x = poolVariables.getDivVariable(DivVariablePool.VAR_X, r, q, k, i, index.getId()).getName();
                            linList.add(var_x);
                            String var_s = poolVariables.getDivVariable(DivVariablePool.VAR_S, r, index.getId(), 0, 0, 0).getName();
                            
                            // (3) s_a^{r} \geq x_{qkia}^{r}
                            buf.getCons().add("atomic_2c_" + numConstraints + ":" 
                                                + var_x + " - " 
                                                + var_s
                                                + " <= 0 ");
                            numConstraints++;
                        }
                        buf.getCons().add("atomic_2b_" + numConstraints  
                                            + ": " + Strings.concatenate(" + ", linList) 
                                            + " - " + var_y
                                            + " = 0");
                        numConstraints++;
                    }
                }       
            }
        }
    }
    
    /**
     * Top-m best cost constraints
     * 
     */
    protected void buildTopmBestCostConstraints()
    {   
        for (QueryPlanDesc desc : listQueryPlanDescs){
            int q = desc.getStatementID();
            List<String> linList = new ArrayList<String>();
            for (int r = 0; r < Nreplicas; r++) {
                for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
                    linList.add(poolVariables.getDivVariable(DivVariablePool.VAR_Y, r, q, k, 0, 0).getName());
                }
            }
            buf.getCons().add("topm_3a_" + numConstraints + ": " + Strings.concatenate(" + ", linList) + " = " + loadfactor);
            numConstraints++;
        }
    }
    
    /**
     * Impose space constraint on the materialized indexes at all window times
     * 
     */
    protected void buildSpaceConstraints()
    {   
        for (int r = 0; r < Nreplicas; r++) {
            List<String> linList = new ArrayList<String>();
            for (Index index : candidateIndexes) {               
                String var_create = poolVariables.getDivVariable(DivVariablePool.VAR_S, r, index.getId(), 0, 0, 0).getName();
                double sizeindx = index.getBytes();
                linList.add(Double.toString(sizeindx) + var_create);
            }
            buf.getCons().add("space_constraint_4" + numConstraints  
                    + " : " + Strings.concatenate(" + ", linList)                    
                    + " <= " + B);
            numConstraints++;               
        }
    }
    
    /**
     * The accumulated total cost function
     */
    protected void buildObjectiveFunction()
    {
        buf.getObj().add(Strings.concatenate(" + ", listCrq));
    }
    
    /**
     * Constraints all variables to be binary ones
     * 
     */
    protected void binaryVariableConstraints()
    {
        int NUM_VAR_PER_LINE = 10;
        String strListVars = poolVariables.enumerateList(NUM_VAR_PER_LINE);
        buf.getBin().add(strListVars);     
    }    
}
