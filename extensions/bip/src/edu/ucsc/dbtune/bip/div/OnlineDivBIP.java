package edu.ucsc.dbtune.bip.div;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.ucsc.dbtune.bip.core.AbstractBIPSolver;
import edu.ucsc.dbtune.bip.core.IndexTuningOutput;
import edu.ucsc.dbtune.bip.core.QueryPlanDesc;
import edu.ucsc.dbtune.bip.util.LogListener;
import edu.ucsc.dbtune.optimizer.InumPreparedSQLStatement;
import edu.ucsc.dbtune.util.Rt;
import edu.ucsc.dbtune.workload.SQLStatement;

public class OnlineDivBIP extends AbstractBIPSolver implements OnlineBIP
{
    protected int window;
    protected int startID;
    protected int endID;
    protected DivBIP divBIP;
    protected DivConfiguration initialConf; 
    // map the cost of statements w.r.t  initial configuration
    protected Map<Integer, Double> mapStmtInitialCost;
    protected double initialCost;
    protected boolean isReconfiguration;
    
    // The threshold to raise an alert
    protected double threshold = 0.5;
    // Number of consecutive times that 
    protected int maxNumOverThreshod = 4;
    
    // counter
    protected int numOverThreshold;
    
    
    
    @Override
    protected void buildBIP() 
    {
        throw new RuntimeException("This method is not applicable");
    }

    @Override
    protected IndexTuningOutput getOutput() throws Exception 
    {
        throw new RuntimeException("This method is not applicable");
    }
    
    @Override
    public void setWindowDuration(int w) 
    {
        window = w;
        startID = 0;
        endID = -1;
        
        numOverThreshold = 0;
    }
    
    /**
     * Populate the set of query plan descriptions
     * @throws Exception
     */
    @Override
    public void populateQueryPlanDescriptions() throws Exception
    {
        super.populatePlanDescriptionForStatements();
    }

    // TODO: check feasibility instead of compute
    // the actual improvement
    @Override
    public void next() throws Exception
    {
        logger.setStartTimer();
        
        // Advance one more statement
        endID++;
        if (endID >= workload.size())
            endID = workload.size() - 1;
        
        // advance startID in order to fit in the window
        if (endID - startID + 1 > window)
            startID++;
        
        List<QueryPlanDesc> descs = new ArrayList<QueryPlanDesc>();
        for (int id = startID; id <= endID; id++)
            descs.add(super.queryPlanDescs.get(id));        
        
        // Set the set of query plan description
        divBIP.setQueryPlanDesc(descs);
        divBIP.solve();
        logger.onLogEvent(LogListener.EVENT_SOLVING_BIP);
        
        computeInitialCost();
        double improvementRatio = 1 - getTotalCost() / initialCost;
        if (improvementRatio >= threshold)
            numOverThreshold++;
        else  // reset
            numOverThreshold = 0;
        Rt.p(" improvement ratio: " + improvementRatio
                + " num over treshold: " + numOverThreshold);
        if (numOverThreshold >= maxNumOverThreshod){
            // reconfiguration
            isReconfiguration = true;
            initialConf = (DivConfiguration) divBIP.getOutput();
            
            // reset
            mapStmtInitialCost.clear();
            numOverThreshold = 0;
        } else 
            isReconfiguration = false;
        
        // clear cache
        divBIP.clear();
    }

    /**
     * Retrieve list of query plan desc
     * @param startID
     * @param endID
     * @return
     */
    public List<QueryPlanDesc> getQueryPlanDescs(int startID, int endID)
    {
        List<QueryPlanDesc> descs = new ArrayList<QueryPlanDesc>();
        for (int id = startID; id <= endID; id++)
            descs.add(this.queryPlanDescs.get(id));
        
        return descs;
    }
    
    @Override
    public double getTotalCost() 
    {   
        return divBIP.getObjValue();
    }

    @Override
    public double getTotalCostInitialConfiguration() throws Exception
    {   
        return initialCost;
    }
    
    /**
     * Compute initial cost
     * @throws Exception
     */
    protected void computeInitialCost() throws Exception
    {
        logger.setStartTimer();
        initialCost = 0.0;
        double cost;
        for (int id = startID; id <= endID; id++){
            if (mapStmtInitialCost.containsKey(id))
                initialCost += mapStmtInitialCost.get(id);
            else  {
                cost = computeInitialCost(workload.get(id));
                mapStmtInitialCost.put(id, cost);
                initialCost += cost;
            }
        }
        logger.onLogEvent(LogListener.EVENT_POPULATING_INUM);
    }
    
    /**
     * Set the initial configuration
     * @param conf
     */
    public void setInitialConfiguration(DivConfiguration conf)
    {
        this.initialConf = conf;
        this.mapStmtInitialCost = new HashMap<Integer, Double>();
    }
    
    /**
     * Set an given DivBIP object to solve each window
     * 
     * @param divBIP
     */
    public void setDivBIP(DivBIP divBIP)
    {
        // TODO: might need to implement copy constructor
        // for DIVBIP
        this.divBIP = divBIP;
    }
    
    /**
     * Compute the cost of the given statement w.r.t the initial configuration
     * 
     * @param sql
     *      The given SQL statement
     * @return
     */
    protected double computeInitialCost(SQLStatement sql) throws Exception
    {
        double cost;
        List<Double> costs = new ArrayList<Double>();
        InumPreparedSQLStatement inumPrepared;
        
        for (int r = 0; r < initialConf.getNumberReplicas(); r++) {
            inumPrepared = (InumPreparedSQLStatement) this.inumOptimizer.prepareExplain(sql);
            cost = inumPrepared.explain(initialConf.indexesAtReplica(r)).getTotalCost();
            costs.add(cost);
        }
        
        // Get the top-k best costs
        Collections.sort(costs);
        cost = 0.0;
        for (int k = 0; k < initialConf.getLoadFactor(); k++)
            cost += costs.get(k);
        
        // get an average
        return (double) cost / initialConf.getLoadFactor();
    }

    @Override
    public boolean isNeedToReconfiguration()
    {
        return isReconfiguration;
    }
}