package edu.ucsc.dbtune.bip;

import static edu.ucsc.dbtune.DatabaseSystem.newDatabaseSystem;
import static edu.ucsc.dbtune.util.TestUtils.workload;

import static edu.ucsc.dbtune.workload.SQLCategory.INSERT;
import static edu.ucsc.dbtune.workload.SQLCategory.DELETE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;



import edu.ucsc.dbtune.DatabaseSystem;
import edu.ucsc.dbtune.bip.div.DivBIP;
import edu.ucsc.dbtune.bip.div.DivConfiguration;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.optimizer.InumOptimizer;
import edu.ucsc.dbtune.optimizer.InumPreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.util.Environment;
import edu.ucsc.dbtune.workload.SQLStatement;
import edu.ucsc.dbtune.workload.Workload;
 

/**
 * The common setting parameters for DIVBIP test
 * 
 * @author Quoc Trung Tran
 *
 */
public class DivTestSetting 
{
    protected static boolean isLoadEnvironmentParameter = false;
    protected static DatabaseSystem db;
    protected static Environment    en;
    protected static Optimizer io;
    protected static Workload workload;
    
    protected static int nReplicas;
    protected static int loadfactor;
    protected static double B;    
    
    protected static List<Double> listBudgets;
    protected static List<Integer> listNumberReplicas;
    
    protected static DivBIP div;
    protected static DivConfiguration divConf;
    
    protected static Set<Index> candidates;
    protected static double fQuery;
    protected static double fUpdate;
    
    
    protected static String folder;
    
    
    // for Debugging purpose only
    protected static boolean isExportToFile;
    protected static boolean isTestCost;
    protected static boolean isShowRecommendation;
    protected static boolean isDB2Cost;
    protected static long totalIndexSize;
    protected static boolean isGetAverage;
    
    /**
     * Retrieve the environment parameters set in {@code dbtune.cfg} file
     * 
     * @throws Exception
     */
    protected static void getEnvironmentParameters() throws Exception
    {
        if (isLoadEnvironmentParameter)
            return;
        
        en = Environment.getInstance();
        db = newDatabaseSystem(en);        
        io = db.getOptimizer();
        
        if (!(io instanceof InumOptimizer))
            return;

        //folder = en.getWorkloadsFoldername() + "/tpch-inum";
        folder = en.getWorkloadsFoldername() + "/tpch-benchmark-mix";
        //folder = en.getWorkloadsFoldername() + "/tpch-mix-div";
        workload = workload(folder);
        
        isLoadEnvironmentParameter = true;
    }
    
    
    /**
     * Set common parameter (e.g., workload, number of replicas, etc. )
     * 
     * @throws Exception
     */
    protected static void setParameters() throws Exception
    {  
        fQuery = 1;
        fUpdate = 1500;
        
        for (SQLStatement sql : workload)
            if (sql.getSQLCategory().isSame(INSERT)) {
                
                // for TPCH workload
                if (sql.getSQL().contains("orders")) 
                    sql.setStatementWeight(fUpdate);
                else if (sql.getSQL().contains("lineitem"))
                    sql.setStatementWeight(fUpdate * 3.5);
            }   
            else if (sql.getSQLCategory().isSame(DELETE))
                sql.setStatementWeight(fUpdate);            
            else 
                sql.setStatementWeight(fQuery);
        
        // debugging purpose
        isExportToFile = false;
        isTestCost = false;
        isShowRecommendation = false;        
        isGetAverage = true;
        isDB2Cost = false;
        
        div = new DivBIP();
        
        // space budget
        double oneMB = Math.pow(2, 20);
        listBudgets = new ArrayList<Double>();
        for (Integer b : en.getListSpaceBudgets()) 
            listBudgets.add(b * oneMB);
        
        // number of replicas
        listNumberReplicas = new ArrayList<Integer>(en.getListNumberOfReplicas());
        
        
        // default value of B, nreplica, and loadfactor
        B = listBudgets.get(0);
        nReplicas = listNumberReplicas.get(0);
        loadfactor = (int) Math.ceil( (double) nReplicas / 2);
    }
    
    
    /**
     * Compute the query execution cost for statements in the given workload
     * 
     * @param conf
     *      A configuration
     *      
     * @throws Exception
     */
    protected static double computeWorkloadCostDB2(Workload workload, Set<Index> conf) 
                            throws Exception
    {   
        double db2Cost;
        double cost;
        
        db2Cost = 0.0;

        for (SQLStatement sql : workload) {
            cost = io.getDelegate().explain(sql, conf).getTotalCost();
            db2Cost += cost * sql.getStatementWeight();
        }
        
        return db2Cost;
    }

    
    /**
     * Compute the query execution cost for statements in the given workload
     * 
     * @param conf
     *      A configuration
     *      
     * @throws Exception
     */
    protected static List<Double> computeQueryCostsDB2(SQLStatement sql,
                                                       DivConfiguration divConf) 
                                                       throws Exception
    {
        double cost;
        List<Double> costs = new ArrayList<Double>();
        
        for (int r = 0; r < nReplicas; r++) {   
            cost = io.getDelegate().explain(sql, divConf.indexesAtReplica(r)).getTotalCost();
            costs.add(cost);
        }
        
        return costs;
    }
    
    /**
     * Compute the query execution cost for statements in the given workload
     * 
     * @param conf
     *      A configuration
     *      
     * @throws Exception
     */
    protected static List<Double> computeQueryCostsInum(Set<Index> conf) throws Exception
    {
        InumPreparedSQLStatement inumPrepared;        
        double inumCost;
        List<Double> costs = new ArrayList<Double>();
        
        for (SQLStatement sql : workload)  {
            
            inumPrepared = (InumPreparedSQLStatement) io.prepareExplain(sql);
            inumCost = inumPrepared.explain(conf).getTotalCost();
            costs.add(inumCost);
            
        }
        
        return costs;
    }
    
    /**
     * Compute the query execution cost for statements in the given workload
     * 
     * @param conf
     *      A configuration
     *      
     * @throws Exception
     */
    protected static void computeQueryCosts(Set<Index> conf) throws Exception
    {
        InumPreparedSQLStatement inumPrepared;
        double db2cost;
        double inumcost;
        
        System.out.println("==============================================");
        System.out.println("Candidate: " + conf.size()
                            + " Workload size: " + workload.size());
        for (Index index : conf)
            System.out.print(index.getId() + " ");
        System.out.println("\n ID  TYPE DB2   INUM   DB2/ INUM");
        int id = 0;
        
        for (SQLStatement sql : workload) {
            
            db2cost = io.getDelegate().explain(sql, conf).getTotalCost();
            inumPrepared = (InumPreparedSQLStatement) io.prepareExplain(sql);
            inumcost = inumPrepared.explain(conf).getTotalCost();
            
            System.out.println(id + " " + sql.getSQLCategory() + " " + db2cost + " " + inumcost + " " 
                                + (double) db2cost / inumcost);
            
            id++;
        }
    }

}
