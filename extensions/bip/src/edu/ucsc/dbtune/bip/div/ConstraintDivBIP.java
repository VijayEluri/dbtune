package edu.ucsc.dbtune.bip.div;

import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_S;
import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_U;
import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_SUM_Y;
import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_COMBINE_X;
import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_COMBINE_Y;
import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_X;
import static edu.ucsc.dbtune.bip.div.DivVariablePool.VAR_Y;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloLinearNumExprIterator;
import ilog.concert.IloNumVar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import edu.ucsc.dbtune.bip.core.QueryPlanDesc;
import edu.ucsc.dbtune.bip.interactions.SortableIndexAcessCost;
import edu.ucsc.dbtune.metadata.Index;

public class ConstraintDivBIP extends DivBIP
{
    public static int IMBALANCE_REPLICA = 1001;
    public static int NODE_FAILURE      = 1002;
    
    protected boolean isApproximation;
    protected List<DivConstraint> constraints;
    
    public ConstraintDivBIP(List<DivConstraint> constraints)
    {
        isApproximation = true;
        this.constraints = constraints;
    }
    
    @Override
    protected void buildBIP() 
    {
        numConstraints = 0;
        
        try {
            // 1. Add variables into list
            constructVariables();
            createCplexVariable(poolVariables.variables());
            
            // 2. Construct the query cost at each replica
            super.totalCost();
            
            // 3. Atomic constraints
            super.atomicConstraints();      
            
            // 4. Top-m best cost 
            super.topMBestCostConstraints();
            
            // 5. Space constraints
            super.spaceConstraints();
            
            // 6. additional constraints
            for (DivConstraint c : constraints) {
                if (c.getType() == IMBALANCE_REPLICA)
                    imbalanceReplicaConstraints(c.getFactor());
                else if (c.getType() == NODE_FAILURE)
                    this.nodeFailures(c.getFactor());
            }
            
        }     
        catch (IloException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void constructVariables() throws IloException
    {   
        super.constructVariables();
        
        // variable for each query descriptions
        for (int r = 0; r < nReplicas; r++)
            for (QueryPlanDesc desc : queryPlanDescs) 
                constructConstraintVariables(r, desc.getStatementID(), desc);
        
    }
    
    /**
     * Construct variables for the query expression of the given query
     * 
     * @param r
     *      The replica ID
     * @param q
     *      The query ID
     * @param desc
     *      The query plan description.
     *      
     */
    protected void constructConstraintVariables(int r, int q, QueryPlanDesc desc)
    {       
        // U variables for the local optimal
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++)  
                for (Index index : desc.getIndexesAtSlot(i)) 
                    poolVariables.createAndStore(VAR_U, r, q, k, index.getId());
        
        // variable sum_q(r, q) = \sum_{k = 1}^{K_q} y^r_{qk}
        poolVariables.createAndStore(VAR_SUM_Y, r, q, 0, 0);
        
        // combined variables to handle node failures
        // that is, the variables corresponding to query q in replica r
        // w.r.t. the failure node r_f # r.
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) 
            for (int failID = 0; failID < nReplicas; failID++)
                if (failID != r)
                    poolVariables.createAndStore(VAR_COMBINE_Y, combineReplicaID(r, failID), 
                                                 q, k, 0);
            
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++)  
                for (Index index : desc.getIndexesAtSlot(i)) 
                    for (int failID = 0; failID < nReplicas; failID++)
                        if (failID != r)
                            poolVariables.createAndStore(VAR_COMBINE_X, combineReplicaID(r, failID),
                                                         q, k, index.getId());
        
    }
    
    /**
     * Impose the imbalance constraints among replicas
     */
    protected void imbalanceReplicaConstraints(double beta) throws IloException
    {   
        IloLinearNumExpr exprReplica;
        IloLinearNumExpr expr;
        
        List<IloLinearNumExpr> exprs = new ArrayList<IloLinearNumExpr>();
        
        // local optimal
        for (int r = 0; r < nReplicas; r++) {
           
            exprReplica = cplex.linearNumExpr(); 
            
            for (QueryPlanDesc desc : queryPlanDescs) {
                
                expr = super.queryExpr(r, desc.getStatementID(), desc);
                
                // constraint for the local optimal
                localOptimal(r, desc.getStatementID(), desc, expr);
                presentVariableLocalOptimal(r, desc.getStatementID(), desc);
                selectingIndexAtEachSlot(r, desc.getStatementID(), desc);
                // construct formula for the replica
                
                exprReplica.add(expr);
            
            }
            
            exprs.add(exprReplica);
        }
        
        // for each pair of replicas, impose the imbalance factor constraint 
        for (int r1 = 0; r1 < nReplicas - 1; r1++)
            for (int r2 = r1 + 1; r2 < nReplicas; r2++) 
                imbalanceConstraint(exprs.get(r1), exprs.get(r2), beta);           
    }
    
    /**
     * Add the constraint on the imbalance for the given two expressions 
     * (usually the load at two replicas)
     * 
     * @param expr1
     *      The expression of the first replica     
     * @param expr2
     *      The expresssion of the second replica
     * @param factor
     *      The imbalance factor.
     */
    protected void imbalanceConstraint(IloLinearNumExpr expr1, IloLinearNumExpr expr2, double beta)
                   throws IloException
    {
        IloLinearNumExpr expr;
        IloNumVar        var;
        double           coef;
        
        IloLinearNumExprIterator iter;
        
        // r1 - \beta x r2 <= 0
        expr = cplex.linearNumExpr();
        expr.add(expr1);
        
        // get iterator over exprs[r2]
        iter = expr2.linearIterator();
        while (iter.hasNext()) {
            var = iter.nextNumVar();
            coef = iter.getValue();
            expr.addTerm(var, - beta * coef);
        }
        cplex.addLe(expr, 0, "imbalance_replica_" + numConstraints);
        numConstraints++;
        
        // r2 - \beta x r1 <= 0
        expr = cplex.linearNumExpr();
        expr.add(expr2);
        
        // get iterator over exprs[r1]
        iter = expr1.linearIterator();
        while (iter.hasNext()) {
            var = iter.nextNumVar();
            coef = iter.getValue();
            expr.addTerm(var, - beta * coef);
        }
        cplex.addLe(expr, 0, "imbalance_replica_" + numConstraints);
        numConstraints++;
    }
    
    /**
     * Impose the imbalance constraints when any one of the replicas fail.
     * 
     * @param beta
     *      The imbalance factor,
     *      
     * @throws IloException
     */
    protected void nodeFailures(double beta) throws IloException
    {
        for (int r = 0; r < nReplicas; r++)
            for (QueryPlanDesc desc : queryPlanDescs)
                sumYConstraint(r, desc.getStatementID(), desc);
        
        for (int failR = 0; failR < nReplicas; failR++)
            nodeFailureConstraint(failR, beta);
        
    }
    
    /**
     * Constraint the variable of type {@code VAR_SUM_Y} to be the summation of corresponding variables
     * of type {@code VAR_Y}
     * 
     * @param r
     *      The replica ID
     * @param q
     *      The query ID
     * @param desc
     *      The query plan description
     *      
     * @throws IloException
     */
    protected void sumYConstraint(int r, int q, QueryPlanDesc desc) throws IloException
    {
        int idY;
        int idSumY;
        
        idSumY = poolVariables.get(VAR_SUM_Y, r, q, 0, 0).getId();
        IloLinearNumExpr expr = cplex.linearNumExpr();
        expr.addTerm(-1, cplexVar.get(idSumY));
        
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
            
            idY = poolVariables.get(VAR_Y, r, q, k, 0).getId();
            expr.addTerm(1, cplexVar.get(idY));
        }
        
        cplex.addEq(expr, 0, "sum_y" + numConstraints);
        numConstraints++;
    }
    /**
     * Construct the set of imbalance factor when the replica with the ID {@code failR} fails.
     * 
     * @param failR
     *      The fail replica ID.
     * @param beta
     *      The imbalance factor.
     *      
     * @throws IloException
     */
    protected void nodeFailureConstraint(int failR, double beta) throws IloException
    {
        IloLinearNumExpr expr;
        List<IloLinearNumExpr> exprs = new ArrayList<IloLinearNumExpr>();
        
        for (int r = 0; r < nReplicas; r++)
            if (r != failR) {
                
                expr = cplex.linearNumExpr();
                for (QueryPlanDesc desc : queryPlanDescs) {
                    expr.add(increadLoadQuery(r, desc.getStatementID(), desc, failR));
                }
                
                exprs.add(expr);
            }
        
        for (int r1 = 0; r1 < exprs.size() - 1; r1++)
            for (int r2 = r1 + 1; r2 < exprs.size(); r2++)
                imbalanceConstraint(exprs.get(r1), exprs.get(r2), beta);
    }
    
    /**
     * Compute the increase in the load of processing query {@code d} at the given replica {@code r}
     * assuming that replica with the identifier {@code failR} has been failed.
     *  
     * @param r
     *      The replica on which the statement is defined
     * @param q
     *      The query ID
     * @param desc
     *      The query plan description.
     * @param failR
     *      The ID of the replica that is assumed to fail
     *           
     * @return
     *      The increase load expression
     */
    protected IloLinearNumExpr increadLoadQuery(int r, int q, QueryPlanDesc desc, int failR)
              throws IloException
    {
        int idX;
        int idY;
        int idSumY;
        int idCombine;
        
        // we need to impose constraints for the combined variables
        idSumY = poolVariables.get(VAR_SUM_Y, failR, q, 0, 0).getId();
        
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {
            
            idCombine = poolVariables.get(VAR_COMBINE_Y, combineReplicaID(r, failR), q, k, 0).getId();
            idY       = poolVariables.get(VAR_Y, r, q, k, 0).getId();
            constraintCombineVariable(idCombine, idSumY, idY);
        
        }
        
        // Index access cost                            
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++)
                for (Index index : desc.getIndexesAtSlot(i)) {
                    
                    idCombine = poolVariables.get(VAR_COMBINE_X, combineReplicaID(r, failR), 
                                           q, k, index.getId()).getId();
                    idX       = poolVariables.get(VAR_X, r, q, k, index.getId()).getId();
                    constraintCombineVariable(idCombine, idSumY, idX);
                    
                }
        
        
        // the increase cost
        IloLinearNumExpr expr = cplex.linearNumExpr();
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++) {                    
            idCombine = poolVariables.get(VAR_COMBINE_Y, combineReplicaID(r, failR), q, k, 0).getId();
            expr.addTerm(desc.getInternalPlanCost(k), cplexVar.get(idCombine));                    
        }                
                    
        // Index access cost                            
        for (int k = 0; k < desc.getNumberOfTemplatePlans(); k++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++)
                for (Index index : desc.getIndexesAtSlot(i)) {
                    idCombine = poolVariables.get(VAR_COMBINE_X, combineReplicaID(r, failR), 
                                           q, k, index.getId()).getId();
                    expr.addTerm(desc.getAccessCost(k, index), cplexVar.get(idCombine));
                }    
        
        return expr;
    }
    
    /**
     * Impose the set of constraints when replacing the produce of two binary variables 
     * {@code idFirst} and {@code idSecond} by {@code idCombine}.
     *  
     * @param idCombine
     *      The ID of the combined varible
     * @param idFirst
     *      The ID of the first variable
     * @param idSecond
     *      The ID of the second variable
     *      
     * @throws IloException
     */
    private void constraintCombineVariable(int idCombine, int idFirst, int idSecond)
                throws IloException
    {
        IloLinearNumExpr expr;
        
        // combine <= idFirst
        expr = cplex.linearNumExpr();
        expr.addTerm(1, cplexVar.get(idCombine));            
        expr.addTerm(-1, cplexVar.get(idFirst));
        cplex.addLe(expr, 0, "combine_" + numConstraints);
        numConstraints++;
        
        // combine <= idSecond
        expr = cplex.linearNumExpr();
        expr.addTerm(1, cplexVar.get(idCombine));            
        expr.addTerm(-1, cplexVar.get(idSecond));
        cplex.addLe(expr, 0, "combine_" + numConstraints);
        numConstraints++;
        
        // combine >= idFirst + idSecond - 1
        expr = cplex.linearNumExpr();
        expr.addTerm(1, cplexVar.get(idCombine));            
        expr.addTerm(-1, cplexVar.get(idFirst));
        expr.addTerm(-1, cplexVar.get(idSecond));
        cplex.addGe(expr, -1, "combine_" + numConstraints);
        numConstraints++;
    }
    
    /**
     * Construct a ``pseudo'' ID of a replica that is a combination of a replica with the identifier
     * {@code id} and a replica {@code failID} that is failed.
     * 
     * @param id
     *      The replica that is alive
     * @param failID
     *      The replica that fails.
     *      
     * @return
     *      The 'pseudo'' ID 
     */
    protected int combineReplicaID(int id, int failID)
    {
        return (id * 1000 + failID);
    }
    
    /**
     * This set of constraints ensure {@code cost(q, X} is not greater than the local optimal cost 
     * of using any template plan.
     *   
     * @throws IloException 
     * 
     */
    protected void localOptimal(int r, int q, QueryPlanDesc desc, IloLinearNumExpr exprQuery)
                   throws IloException
    {   
        IloLinearNumExpr expr;        
        int idU;
        double approxCoef;        
        
        if (isApproximation)
            approxCoef = 1.1;
        else 
            approxCoef = 1.0;
        
        // local optimal
        for (int t = 0; t < desc.getNumberOfTemplatePlans(); t++) {
            
            expr = cplex.linearNumExpr();
            expr.add(exprQuery);    
                        
            for (int i = 0; i < desc.getNumberOfSlots(); i++) 
                for (Index index : desc.getIndexesAtSlot(i)) {
                    idU = poolVariables.get(VAR_U, r, q, t, index.getId()).getId();
                    expr.addTerm(-approxCoef * desc.getAccessCost(t, index), cplexVar.get(idU));
                }
            
            cplex.addLe(expr, approxCoef * desc.getInternalPlanCost(t), "local_" + numConstraints);
            numConstraints++;
        }
        
        // atomic constraint
        for (int t = 0; t < desc.getNumberOfTemplatePlans(); t++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++) {
                expr = cplex.linearNumExpr();
                for (Index index : desc.getIndexesAtSlot(i)) {
                    idU = poolVariables.get(VAR_U, r, q, t, index.getId()).getId();
                    expr.addTerm(1, cplexVar.get(idU));           
                }
                
                cplex.addEq(expr, 1, "atomic_U" + numConstraints);
                numConstraints++;
            }
        
    }
            
    
    
    /**
     * Constraint on the present of {@code VAR_U} variables.
     * 
     * For example, a variable corresponding to some index {@code a} must be {@code 0}
     * if {@code a} is not recommended.
     * 
     * @throws IloException 
     * 
     */
    protected void presentVariableLocalOptimal(int r, int q, QueryPlanDesc desc) throws IloException
    {   
        IloLinearNumExpr expr;
        int idU;
        int idS;
            
        for (int t = 0; t < desc.getNumberOfTemplatePlans(); t++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++) {
                // not constraint FTS variables
                for (Index index : desc.getIndexesWithoutFTSAtSlot(i)) {
                    idU = poolVariables.get(VAR_U, r, q, t, index.getId()).getId();
                    idS = poolVariables.get(VAR_S, r, 0, 0, index.getId()).getId();
                    expr = cplex.linearNumExpr();
                    expr.addTerm(1, cplexVar.get(idU));
                    expr.addTerm(-1, cplexVar.get(idS));            
                    cplex.addLe(expr, 0, "U_present_" + numConstraints);
                    numConstraints++;
                                        
                }         
            }
    }
    
    /**
     * 
     * The constraints ensure the index with the small index access cost is used
     * to compute {@code cost(q, r)} 
     * 
     * @throws IloException 
     * 
     */
    protected void selectingIndexAtEachSlot(int r, int q, QueryPlanDesc desc) throws IloException
    {  
        
        for (int t = 0; t < desc.getNumberOfTemplatePlans(); t++)
            for (int i = 0; i < desc.getNumberOfSlots(); i++) 
                selectingIndexAtEachSlot(r, q, t, i, desc);
    }
    
    protected void selectingIndexAtEachSlot(int r, int q, int t, int i, QueryPlanDesc desc) 
                   throws IloException
    {
        int idU;
        int idS;        
        int idFTS, numIndex;
     
        // Sort index access cost
        List<SortableIndexAcessCost> listSortedIndex  = new ArrayList<SortableIndexAcessCost>();
                
        for (Index index : desc.getIndexesAtSlot(i)) {
            SortableIndexAcessCost sac = new SortableIndexAcessCost 
                                            (desc.getAccessCost(t, index), index);
            listSortedIndex.add(sac);                       
        }                   
            
        numIndex = desc.getIndexesAtSlot(i).size();
        idFTS = desc.getIndexesAtSlot(i).get(numIndex - 1).getId();
            
        // sort in the increasing order of the index access cost
        Collections.sort(listSortedIndex);
                               
        List<Integer> varIDs = new ArrayList<Integer>();
        
        for (SortableIndexAcessCost sac : listSortedIndex) {  
            
            Index index = sac.getIndex();
            idU = poolVariables.get(VAR_U, r, q, t, index.getId()).getId();
            varIDs.add(idU);
                    
            if (index.getId() == idFTS) {
                IloLinearNumExpr exprInternal = cplex.linearNumExpr();
                for (int varID : varIDs)
                    exprInternal.addTerm(1, cplexVar.get(varID));
                
                cplex.addEq(exprInternal, 1, "FTS_" + numConstraints);
                numConstraints++;
                break; // the remaining variables will be assigned value 0 
            } else {                                    
                IloLinearNumExpr exprInternal = cplex.linearNumExpr();
                for (int varID : varIDs)
                    exprInternal.addTerm(1, cplexVar.get(varID));
                    
                idS = poolVariables.get(VAR_S, r, 0, 0, index.getId()).getId();
                exprInternal.addTerm(-1, cplexVar.get(idS));
                cplex.addGe(exprInternal, 0, "select_index_" + numConstraints); 
                numConstraints++;
            }   
        }   
    }
    
    
}
        