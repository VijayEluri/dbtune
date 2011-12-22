package edu.ucsc.dbtune.bip.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucsc.dbtune.inum.InumSpace;
import edu.ucsc.dbtune.inum.InumStatementPlan;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.Table;
import edu.ucsc.dbtune.workload.SQLStatement;

public class QueryPlanDesc 
{	
	/**
	 * The number of template plans for this query 
	 */
	protected int Kq; 
	/**
	 * The number of relations (also referred to as slots) used in the query
	 */
	protected int n; 
	/**
	 * The total number of candidate indexes 
	 */
	protected int numIndexes; 
	/**
	 * The number of indexes in each slot
	 */
	protected List<Integer> S; 
	/**
	 * The cost of internal plans
	 */
	protected List<Double> beta; 
	/**
	 * The index access costs
	 */
	protected List< List< List <Double> > > gamma;	 
	/**
	 * List of indexes in each slot
	 */
	protected List< List< Index> > listIndexesEachSlot;
	
	/**
	 * List of tables in the schema
	 */
	protected List<Table> listSchemaTables;
	
	
	/**
	 * List of position of slots referenced by the query
	 */
	protected Map<Integer, Integer> mapReferencedSlotID;
	
	protected InumSpace inum;
	
	/**
	 * Number of template plans	 
	 */
	public int getNumPlans()
	{
		return Kq;
	}
	public void setNumPlans(int Kq)
	{
		this.Kq = Kq;
	}
	
	/**
	 * Number of relations in the query schema 
	 */
	public int getNumSlots()
	{
		return n;
	}
	
	public void setNumSlots(int n)
	{
		this.n = n;
	}
	
	/**
	 *  Number of candidate indexes
	 */
	public int getNumCandidateIndexes()
	{
		return numIndexes;
	}
	public void setNumCandidateIndexes(int numIndex)
	{
		this.numIndexes = numIndex;
	}
	
	/**
	 * Number of candidate indexes at each slot 
	 */
	public int getNumIndexesEachSlot(int i)
	{
		return S.get(i);
	}
	public void setNumIndexesEachSlot(List<Integer> S)
	{
		this.S = S;		
	}
	
	/**
	 * The internal plan cost 
	 */
	public double getInternalPlanCost(int i)
	{
		return beta.get(i);
	}
	public void setInternalPlanCost(ArrayList<Double> beta)
	{
		this.beta = beta;		
	}
	
	/**
	 * Index access cost
	 */
	public double getIndexAccessCost(int k, int i, int a)
	{
		return gamma.get(k).get(i).get(a);
	}
	
	public void setIndexAccessCost(List< List< List <Double> > > gamma)
	{
		this.gamma = gamma;
	}
	
	/**
	 * Retrieve the index in the corresponding slot
	 * @param i 
	 * 		The position of the relation
	 * @param a
	 * 		The position of this index in the list of indexes belonging to this relation
	 * @return
	 * 		Index
	 */
	public Index getIndex(int i, int a)
	{
		return listIndexesEachSlot.get(i).get(a);
	}
	
	public void setCandidateIndexes(List<List<Index>> candidateIndexes)
	{
		listIndexesEachSlot = candidateIndexes;
	}

	/**
	 * Populate query plan description  number of template plans, internal cost, 
	 * index access cost, etc. )
	 * 
	 * @param agent
	 * 		The class to communicate with INUM to get InumSpace
	 * @param stmt
     *      A SQL statement
	 * @param globaCandidateIndexes
	 * 		The given list of candidate indexes (globally)	
	 * 
	 * {\b Note}: 
	 *     - There does not contain the empty index (table scan) in {@code globalCandidateIndexes}
	 *     - The index full table scan is always assigned the last position in the list of indexes
	 *     at each slot
	 * @throws SQLException 
	 */	
	public void generateQueryPlanDesc(BIPAgentPerSchema agent, SQLStatement stmt, 
	                                  List<Index> globalCandidateIndexes) throws SQLException
	{
		S = new ArrayList<Integer>();
		beta = new ArrayList<Double>();
		gamma = new ArrayList<List<List<Double>>>(); 
		listIndexesEachSlot = new ArrayList<List<Index>>();
		
		this.inum = agent.populateInumSpace(stmt);
		List<IndexFullTableScan> listFullTableScanIndexes = agent.getListFullTableScanIndexes();  
		listSchemaTables = agent.getListSchemaTables();
		Set<InumStatementPlan> templatePlans = inum.getTemplatePlans();
					
		// TODO: replace with the new interface ----------------------
		// Note that list tables is derived from the schema
		// @listSchemaTables and @listReferencedTable is different		
		List<Table> listReferencedTables = new ArrayList<Table>();   
		for (InumStatementPlan plan : templatePlans) {
			listReferencedTables = plan.getReferencedTables();
			break;
		}
		// ------------------------------------------------------------
		
		// 1. Set up the number of slots & number of indexes in each slot
		n = 0;
		numIndexes = 0;		
		
		for (Table table : listSchemaTables) {			
			int numIndexEachSlot = 0;
			List<Index> listIndex = new ArrayList<Index>();			
			
			for (Index index : globalCandidateIndexes) {
			    if (index.getTable().equals(table)){				
					numIndexEachSlot++;
					numIndexes++;
					listIndex.add(index);
				}
			}
			
			// find the full table scan index corresponding to the slot
			for (IndexFullTableScan scanIdx : listFullTableScanIndexes) {
			    if (scanIdx.getTable().equals(table) == true) {
			        numIndexEachSlot++;
                    numIndexes++;
                    listIndex.add(scanIdx);
                    break;
			    }
			}
			
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
		for (InumStatementPlan plan : templatePlans) {
			beta.add(new Double(plan.getInternalCost()));
			List<List<Double>> gammaPlan = new ArrayList<List<Double>>();
			
			for (int i = 0; i < n; i++) {
				List<Double> gammaRel = new ArrayList<Double>(); 
				
				// If the table is not reference then assigned gamma = 0
				Object found = mapReferenceTable.get(listSchemaTables.get(i));
				if (found == null){
				    for (int a = 0; a < getNumIndexesEachSlot(i); a++) {
				        gammaRel.add(new Double(0.0));
				    }
				} else {
				    Table table = (Table) found;
    				for (int a = 0; a < getNumIndexesEachSlot(i); a++) {
    					Index index = getIndex(i, a);
    					// Full table scan index 
    					if (a == getNumIndexesEachSlot(i) - 1){						
    						gammaRel.add(new Double(plan.getFullTableScanCost(table)));
    					} else {
    						gammaRel.add(new Double(plan.getAccessCost(index)));
    					}
    				}		
				}
				gammaPlan.add(gammaRel);							
			}
			
			gamma.add(gammaPlan);
			Kq++;
		}
	}
	
	/**
	 * Check if @idSlot is referenced by the query
	 * 
	 * @param idSlot
	 *     The ID of the slot
	 * @return
	 *     {@code boolean}: true if idSlot is referenced by the query
	 */
	public boolean isReferenced(int idSlot)
	{
	    Object found = this.mapReferencedSlotID.get(new Integer(idSlot));
	    if (found == null){
	        return false;
	    } else {
	        return true;
	    }
	}
}
