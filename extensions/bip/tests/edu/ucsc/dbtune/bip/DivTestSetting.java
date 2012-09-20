package edu.ucsc.dbtune.bip;

import static edu.ucsc.dbtune.DatabaseSystem.newDatabaseSystem;
import static edu.ucsc.dbtune.util.TestUtils.workload;

import static edu.ucsc.dbtune.workload.SQLCategory.INSERT;
import static edu.ucsc.dbtune.workload.SQLCategory.DELETE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.ucsc.dbtune.DatabaseSystem;
import edu.ucsc.dbtune.advisor.db2.DB2Advisor;
import edu.ucsc.dbtune.bip.div.DivBIP;
import edu.ucsc.dbtune.bip.div.DivConfiguration;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.optimizer.InumOptimizer;
import edu.ucsc.dbtune.optimizer.InumPreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.util.Environment;
import edu.ucsc.dbtune.util.Rt;
import edu.ucsc.dbtune.workload.SQLStatement;
import edu.ucsc.dbtune.workload.Workload;
 
import static edu.ucsc.dbtune.workload.SQLCategory.SELECT;
import static edu.ucsc.dbtune.workload.SQLCategory.UPDATE;
import static edu.ucsc.dbtune.bip.CandidateGeneratorFunctionalTest.readCandidateIndexes;

import static edu.ucsc.dbtune.util.EnvironmentProperties.QUERY_IMBALANCE;
import static edu.ucsc.dbtune.util.EnvironmentProperties.NODE_IMBALANCE;
import static edu.ucsc.dbtune.util.EnvironmentProperties.FAILURE_IMBALANCE;


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
    protected static List<Double> nodeImbalances;
    protected static List<Double> queryImbalances;
    protected static List<Double> failureImbalances;
    protected static List<Integer> listNumberReplicas;
    
    protected static DivBIP div;
    protected static DivConfiguration divConf;
    
    protected static Set<Index> candidates;
    protected static double fQuery;    
    protected static double fUpdate;
    protected static double sf;
    protected static String folder;
    
    protected static DB2Advisor db2Advis;
    
    // for Debugging purpose only
    protected static double totalIndexSize;
    protected static boolean isExportToFile = false;
    protected static boolean isTestCost = false;
    protected static boolean isShowRecommendation = false;
    protected static boolean isDB2Cost = false;
    protected static boolean isGetAverage = false;
    protected static boolean isPostprocess = false; 
    protected static boolean isAllImbalanceConstraint = false;
    
    /**
     * Retrieve the environment parameters set in {@code dbtune.cfg} file
     * 
     * @throws Exception
     */
    public static void getEnvironmentParameters() throws Exception
    {
        if (isLoadEnvironmentParameter)
            return;
        
        en = Environment.getInstance();        
        db = newDatabaseSystem(en);        
        io = db.getOptimizer();
        
        if (!(io instanceof InumOptimizer))
            return;
        
        folder = en.getWorkloadsFoldername();
        
        // get workload and candidates
        workload = workload(folder);
        db2Advis = new DB2Advisor(db);
        candidates = readCandidateIndexes(db2Advis);
        
        Rt.p(" DivTestSetting: # statements in the workload: " + workload.size()
                + " # candidates in the workload: " + candidates.size()
                + " workload folder: " + folder);
        
        isLoadEnvironmentParameter = true;
        isPostprocess = false;
    }
    
    
    /**
     * Set common parameter (e.g., workload, number of replicas, etc. )
     * 
     * @throws Exception
     */
    public static void setParameters() throws Exception
    {  
        fUpdate = 1;
        fQuery = 1;
        sf = 15000;
        
        for (SQLStatement sql : workload)
            if (sql.getSQLCategory().isSame(INSERT)) {
                
                // for TPCH workload
                if (sql.getSQL().contains("orders")) 
                    sql.setStatementWeight(sf);
                else if (sql.getSQL().contains("lineitem"))
                    sql.setStatementWeight(sf * 3.5);
            }   
            else if (sql.getSQLCategory().isSame(DELETE))
                sql.setStatementWeight(sf);            
            else if (sql.getSQLCategory().isSame(SELECT))
                sql.setStatementWeight(fQuery);
            else if (sql.getSQLCategory().isSame(UPDATE))
                sql.setStatementWeight(fUpdate);
        
        // debugging purpose
        isExportToFile = true;
        isTestCost = false;
        isShowRecommendation = false;        
        isGetAverage = false;
        isDB2Cost = false;
        isAllImbalanceConstraint = false;
        
        div = new DivBIP();
        
        // space budget
        double oneMB = Math.pow(2, 20);
        listBudgets = new ArrayList<Double>();
        for (Integer b : en.getListSpaceBudgets()) 
            listBudgets.add(b * oneMB);
        
        // number of replicas
        listNumberReplicas = new ArrayList<Integer>(en.getListNumberOfReplicas());
        
        // default values of B, nreplica, and loadfactor
        B = listBudgets.get(0);
        nReplicas = listNumberReplicas.get(0);
        loadfactor = (int) Math.ceil( (double) nReplicas / 2);
        
        // imbalance factors
        for (String typeConstraint : en.getListImbalanceConstraints())                
            if (typeConstraint.equals(NODE_IMBALANCE))
                nodeImbalances = en.getListImbalanceFactors();
            else if (typeConstraint.equals(QUERY_IMBALANCE))
                queryImbalances = en.getListImbalanceFactors();
            else if (typeConstraint.equals(FAILURE_IMBALANCE))
                failureImbalances = en.getListImbalanceFactors();
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
        Rt.p(" # indexes: " + conf.size()
                        + " # workload: " + workload.size());
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
    protected static List<Double> computeQueryCostsInum(Workload workload, Set<Index> conf) 
                throws Exception
    {
        InumPreparedSQLStatement inumPrepared;        
        double inumCost;
        List<Double> costs = new ArrayList<Double>();
        
        int q = 0;
        
        for (SQLStatement sql : workload)  {
            
            inumPrepared = (InumPreparedSQLStatement) io.prepareExplain(sql);
            inumCost = inumPrepared.explain(conf).getTotalCost();
            costs.add(inumCost);
            
            q++;
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
    protected static List<Double> computeQueryCostsDB2(Workload workload, Set<Index> conf) throws Exception
    {           
        double db2Cost;
        List<Double> costs = new ArrayList<Double>();
        
        for (SQLStatement sql : workload)  {
            db2Cost = io.getDelegate().explain(sql, conf).getTotalCost();
            costs.add(db2Cost);
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
    protected static void compareDB2InumQueryCosts(Workload workload, Set<Index> conf) throws Exception
    {
        InumPreparedSQLStatement inumPrepared;
        double db2cost;
        double inumcost;
        
        Rt.p("==============================================");
        Rt.p("Candidate: " + conf.size()
              + " Workload size: " + workload.size());
        
        Rt.p("\n ID  TYPE DB2   INUM   DB2/ INUM");
        int id = 0;
        double totalDB2 = 0.0;
        double totalInum = 0.0;
        
        for (SQLStatement sql : workload) {
            
            db2cost = io.getDelegate().explain(sql, conf).getTotalCost();
            inumPrepared = (InumPreparedSQLStatement) io.prepareExplain(sql);
            inumcost = inumPrepared.explain(conf).getTotalCost();
            Rt.p(" stmt: " + sql.getSQL());
            if (inumcost < 0.0 || inumcost > Math.pow(10, 9)) 
                Rt.p("WATCH OUT -----------------");
            Rt.p(id + " " + sql.getSQLCategory() + " " + db2cost + " " + inumcost + " " 
                                + (double) db2cost / inumcost);
            totalDB2 += db2cost;
            totalInum += inumcost;
            id++;
        }
        
        Rt.p(" total INUM = " + totalInum
                + " total DB2 = " + totalDB2
                + " DB2 / INUM = " + (totalDB2 / totalInum));
    }

    /**
     * Compute the query execution cost for statements in the given workload
     * 
     * @param conf
     *      A configuration
     *      
     * @throws Exception
     */
    protected static void showComputeQueryCostsInum(int q, Set<Index> conf) throws Exception
    {
        InumPreparedSQLStatement inumPrepared;        
        SQLStatement sql=workload.get(q);
        inumPrepared = (InumPreparedSQLStatement) io.prepareExplain(sql);
        Rt.p(" INUM plan: " + inumPrepared.explain(conf)
                + " cost: " + inumPrepared.explain(conf).getTotalCost());
    }
    
}
 