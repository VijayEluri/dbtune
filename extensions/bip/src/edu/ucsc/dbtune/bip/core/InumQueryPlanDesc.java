package edu.ucsc.dbtune.bip.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


import edu.ucsc.dbtune.bip.util.IndexInSlot;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.IndexFullTableScan;
import edu.ucsc.dbtune.metadata.Schema;
import edu.ucsc.dbtune.metadata.Table;
import edu.ucsc.dbtune.optimizer.InumOptimizer;
import edu.ucsc.dbtune.optimizer.InumPreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.plan.InumPlan;
import edu.ucsc.dbtune.workload.SQLStatement;


public class InumQueryPlanDesc implements QueryPlanDesc 
{	
	private int Kq;
	private int n; 
	private int numIndexes;
	private List<Integer> S;
	private List<Double> beta;
	private List< List< List <Double> > > gamma;
	private List< List< Index> > listIndexesEachSlot;
	private List<Table> listSchemaTables;
	private Map<Integer, Integer> mapReferencedSlotID;	
	private int stmtID;	
	private InumOptimizer inumOptimizer;
    static AtomicInteger STMT_ID = new AtomicInteger(0); 
    /** used to uniquely identify each instances of the class. */    
    
    public InumQueryPlanDesc (InumOptimizer optimizer)
    {
        this.inumOptimizer = optimizer;
    }
    
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#getNumberOfTemplatePlans()
     */
    @Override
	public int getNumberOfTemplatePlans()
	{
		return Kq;
	}
	
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#getNumberOfSlots()
     */
    @Override
	public int getNumberOfGlobalSlots()
	{
		return n;
	}		
	
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#getNumberOfIndexesEachSlot(int)
     */
    @Override
	public int getNumberOfIndexesEachSlot(int i)
	{
		return S.get(i);
	}
	
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#getInternalPlanCost(int)
     */
    @Override
	public double getInternalPlanCost(int i)
	{
		return beta.get(i);
	}
	
	
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#getIndexAccessCost(int, int, int)
     */
    @Override
	public double getIndexAccessCost(int k, int i, int a)
	{
		return gamma.get(k).get(i).get(a);
	}
	
	
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#getStatementID()
     */
    @Override
	public int getStatementID()
    {
        return this.stmtID;
    }
	
    @Override
	public Index getIndex(int i, int a)
	{
		return listIndexesEachSlot.get(i).get(a);
	}
	
	/* (non-Javadoc)
     * @see edu.ucsc.dbtune.bip.util.QueryPlanDesc#generateQueryPlanDesc(SQLStatement stmt, Schema schema, IndexPool poolIndexes)
     */ 
	@Override
    public void generateQueryPlanDesc(SQLStatement stmt, Schema schema, IndexPool poolIndexes) throws SQLException
    {
        S = new ArrayList<Integer>();
        beta = new ArrayList<Double>();
        gamma = new ArrayList<List<List<Double>>>(); 
        listIndexesEachSlot = new ArrayList<List<Index>>();
        
        this.stmtID = InumQueryPlanDesc.STMT_ID.getAndIncrement();
        InumPreparedSQLStatement preparedStmt = (InumPreparedSQLStatement) this.inumOptimizer.prepareExplain(stmt);
        preparedStmt.explain(new HashSet<Index>());
        Set<InumPlan> templatePlans = preparedStmt.getTemplatePlans();
        
        listSchemaTables = new ArrayList<Table>();
        List<Table> listReferencedTables = new ArrayList<Table>();
        for (Table table : schema.tables()) {
            listSchemaTables.add(table);
        }     
        for (InumPlan plan : templatePlans) {
            listReferencedTables = plan.getReferencedTables();
            break;
        }
        
        // 1. Set up the number of slots & number of indexes in each slot
        n = 0;
        numIndexes = 0;
        for (Table table : listSchemaTables) {          
            int numIndexEachSlot = 0;
            List<Index> listIndex = new ArrayList<Index>();         
            IndexFullTableScan scanIdx = new IndexFullTableScan(table);
            for (Index index : poolIndexes.indexes()) {
                // normal index (not the full table scan index)
                if (index.getTable().equals(table) && index.equals(scanIdx) == false){                
                    numIndexEachSlot++;
                    numIndexes++;
                    listIndex.add(index);
                }
            }
            // add the index full table scan at the last position in this slot
            numIndexEachSlot++;
            numIndexes++;
            listIndex.add(scanIdx);
            
            S.add(new Integer(numIndexEachSlot));
            listIndexesEachSlot.add(listIndex);
            n++;            
        }
        
        Map<Table, Table> mapReferenceTable = new HashMap<Table, Table>();
        for (Table referencedTable : listReferencedTables){
            mapReferenceTable.put(referencedTable, referencedTable);
        }
        
        mapReferencedSlotID = new HashMap<Integer, Integer>();
        for (int i = 0; i < listSchemaTables.size(); i++){
            Object found = mapReferenceTable.get(listSchemaTables.get(i));
            if (found != null){
                mapReferencedSlotID.put(new Integer(i), new Integer(1));
            }
        }
        
        Kq = 0;
        for (InumPlan plan : templatePlans) {
            beta.add(new Double(plan.getInternalCost()));
            List<List<Double>> gammaPlan = new ArrayList<List<Double>>();
            
            for (int i = 0; i < n; i++) {
                List<Double> gammaRel = new ArrayList<Double>(); 
                
                // If the table is not reference then assigned gamma = 0
                Object found = mapReferenceTable.get(listSchemaTables.get(i));
                if (found == null){
                    for (int a = 0; a < getNumberOfIndexesEachSlot(i); a++) {
                        gammaRel.add(new Double(0.0));
                    }
                } else {
                    for (int a = 0; a < getNumberOfIndexesEachSlot(i); a++) {
                        Index index = getIndex(i, a);
                        gammaRel.add(new Double(plan.plug(index)));
                    }       
                }
                gammaPlan.add(gammaRel);                            
            }
            gamma.add(gammaPlan);
            Kq++;
        }
    }
	
    
	@Override
    public void mapIndexInSlotToPoolID(IndexPool poolIndex)
    {
        int q = getStatementID();
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < getNumberOfIndexesEachSlot(i); a++) {
                Index index = getIndex(i, a);
                IndexInSlot iis = new IndexInSlot(q,i,a);
                poolIndex.mapIndexInSlot(iis, index);
            }
        }
    }
    
	
	@Override
	public boolean isSlotReferenced(int idSlot)
	{
	    Object found = this.mapReferencedSlotID.get(new Integer(idSlot));
	    if (found == null){
	        return false;
	    } else {
	        return true;
	    }
	}
}
