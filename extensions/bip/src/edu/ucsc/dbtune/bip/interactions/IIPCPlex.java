package edu.ucsc.dbtune.bip.interactions;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import ilog.concert.*;
import ilog.cplex.*;

import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.util.Environment; 
import edu.ucsc.dbtune.bip.util.CPlexBuffer;
import edu.ucsc.dbtune.bip.util.QueryPlanDesc;
import edu.ucsc.dbtune.bip.util.LogListener;


/** 
 * The class is responsible for solving the RestrictIIP problem: 
 * 		For a particular query q, the IIP problem is to find 
 * 		pairs of indexes (c,d) that interact w.r.t. q; i.e., doi_q(c,d) >= delta.
 * 		The pair of indexes c and d must be relevant to the given input query q (?)   
 */

public class IIPCPlex {
	private IloCplex cplex;
	private IloLPMatrix matrix;
	private IloNumVar [] vars;
	private IIPLinGenerator genIIP; 
	private Environment environment = Environment.getInstance();
	
	public static HashMap<String,Integer> cachedInteractIndexName = new HashMap<String, Integer>();	
	public static final Pattern patternIndexVariable = Pattern.compile("s*");
		
	
	/**
	 * Find pairs of indexes from the pool of candidate indexes that interact with each other
	 * 
	 * @param desc
	 *     Query plan description including (internal plan, access costs) derived from INUM	 * 	   
	 * 
	 * @return
	 * 		The set of pairs of indexes that interact 
	 */
	public ArrayList< ArrayList<Index> > run(QueryPlanDesc desc)  {
		
		LogListener listener = new LogListener() {
            public void onLogEvent(String component, String logEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        
        ArrayList< ArrayList<Index> > interactIndexes = new ArrayList< ArrayList<Index> >();        
		String workloadName = environment.getTempDir() + "/testwl";			
		String cplexFile = "", binFile = "", consFile = "", objFile = "";		
		int ic, pos_c, id, pos_d;
		double delta = 0.1;
		
		for (ic = 0; ic < desc.getNumRels(); ic++)
		{
			for (pos_c = 0; pos_c < desc.getNumIndexEachSlot(ic); pos_c++)
			{
				Index indexc = desc.getIndex(ic, pos_c);
				for (id = ic; id < desc.getNumRels(); id++)
				{
					for (pos_d = 0; pos_d < desc.getNumIndexEachSlot(id); pos_d++)
					{
						/*
						cont = (ic == 0 && pos_c == 0 && id == 1 && pos_d == 1);
						if (cont == false){
							continue;
						}
						*/
						// avoid repeating pair of indexes that have been investigated						
						if (ic == id && pos_c >= pos_d)
						{	
							continue;
						}
						 
						Index indexd = desc.getIndex(id, pos_d);
						
						// check if pair of interact indexes have been cached
						if (checkInCache(indexc, indexd) == true)
						{
							System.out.println("TTTTTTTTTTIN Cplex, in cache: " + indexc.getName()
												+ " and " + indexd.getName());
							continue;
						}
						
						System.out.println("*** Investigating pair of " + indexc.getName()
											+ " vs. " + indexd.getName() + "****");
						RestrictIIPParam restrictIIP = new RestrictIIPParam(delta, ic, id, pos_c, pos_d);				
						
						try {														
							genIIP = new IIPLinGenerator(restrictIIP, desc, workloadName);
							
							// Build BIP for a particular (c,d, @desc)
							genIIP.build(listener); 
							System.out.println("In CPlex, number of variables " + genIIP.getTotalVar()
												+ " number of constraints: " + genIIP.getTotalConstraints());
						
							cplexFile = workloadName + ".lp";
							binFile = workloadName + ".bin";
							consFile = workloadName + ".cons";
							objFile = workloadName + ".obj";
							
							CPlexBuffer.concat(cplexFile, objFile, consFile, binFile);							
				        }
						catch(IOException e){
				        	System.out.println("Error " + e);
				        }			
												
						//  Load the corresponding CPLEX problem from the corresponding text file
				        try {		        
				            cplex = new IloCplex(); 
				            //cplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Auto);
				                      
				            // Read model from file with name @cplexFile into cplex optimizer object
				            cplex.importModel(cplexFile); 
				            
				            // Get the matrix and variable
				            matrix = getMatrix(cplex);
				            vars = matrix.getNumVars();
				            
				            // Solve the model and record the solution into @candidateIndexes 
				            // if one was found
				            if (cplex.solve()) {				               
				               // Add pair of index interaction into the result
				               ArrayList<Index> pairIndex = new ArrayList<Index>();
				               pairIndex.add(indexc);
				               pairIndex.add(indexd);
				               interactIndexes.add(pairIndex);				               
				               addToCache(indexc, indexd);
				               
				               System.out.println(" INTERACT (the FIRST interaction constraint): "
				            		   + indexc.getName()
				            		   + " and " + indexd.getName());
				               
				               printDetailedInteraction();
				            }
				            else 
				            {   
				            	// Remove constraint the first constraint on index interaction (13)
				            	// Add the alternative constraint (12)				            		
				            	int last_row_id = matrix.getNrows() - 1;
				            	System.out.println(" number of rows in the constraint matrix: " + last_row_id + 1);
				            	matrix.removeRow(last_row_id);		            	
				            	  
				            	double[] objvals = alternativeConstraintIndexInteraction();				            	
				            	cplex.addLe(cplex.scalProd(objvals, vars), 0);				            	
				            	if (cplex.solve())
				            	{
				            		ArrayList<Index> pairIndex = new ArrayList<Index>();
						            pairIndex.add(indexc);
						            pairIndex.add(indexd);
						            interactIndexes.add(pairIndex);
						            addToCache(indexc, indexd); 
						            
						            System.out.println(" INTERACT (the SECOND interaction constraint): "
						            			+ indexc.getName()
						            		    + " and " + indexd.getName());
						               
						             printDetailedInteraction();
				            	} 
				            	else 
				            	{
				            		System.out.println("NO INTERACTION");
				            	}
				            	
				            }
				            cplex.end();
				         }
				         catch (IloException e) {
				            System.err.println("Concert exception caught: " + e);
				         }
				         
					}					
				}					
				
			}
		}
		
	    
		return interactIndexes;
	}
	
	
	/**
	 * Derive the coefficients for the last constraint (13)
	 * 
	 * @return  
	 * 		An array of coefficient corresponding variables of the BIP matrix
	 */
	private double[] alternativeConstraintIndexInteraction(){
		HashMap<String,Double> mapVarCoef = genIIP.buildIndexInteractionConstraint2();
		double[] listCoef = new double[vars.length];
		
		IloNumVar var;
		int i;
		Double coef;
		for (i = 0; i < vars.length; i++) {
            var = vars[i];
            coef = (Double) mapVarCoef.get(var.getName());           
            listCoef[i] = coef;
		}
		return listCoef;		
	}
	    
	/**
	 * Show variables values
	 */
	private void printDetailedInteraction()
	{
		int i, type;		
		HashMap<String, Double> mapVarVal = new HashMap<String, Double>(); 
		try
		{
			for (i = 0; i < vars.length; i++) 
			{
	            IloNumVar var = vars[i];
	            type = IIPLinGenerator.getVarType(var.getName());
	            
	            if (type == IIPLinGenerator.VAR_S || type == IIPLinGenerator.VAR_X
	            	|| type == IIPLinGenerator.VAR_Y || type == IIPLinGenerator.VAR_U)
	            {
	            	
	            	if (cplex.getValue(var) > 0)
	            	{
	            		/*
	            		System.out.println(" Variable " + var.getName() + " value: " 
	            				+ cplex.getValue(var));
	            		*/
	            	}
	            	
	            	mapVarVal.put(var.getName(), new Double(cplex.getValue(var)));
	            }
	            
	        }
		}
		catch (Exception e)
		{
			System.out.println(" CPLEX error: " + e.getMessage());
		}
		
		genIIP.computeC(mapVarVal);
		
	}
	
	/**
	 * Determine the cost and index variables (?)
	 * 
	 * @return 
	 * 		An array of IloNumVar representing index variables
	 */
	private IloNumVar[] getCostAndIndexVariables() throws IloException {
        ArrayList<IloNumVar> variables = new ArrayList<IloNumVar>();
        int i;
        // @vars: list of variables in the problem definitions
        // @matrix: the linear programming matrix
	    // Both @vars and @matrix have been extracted in @run method,
        // after the model from the file is loaded
        
        for (i = 0; i < vars.length; i++) 
        {
            IloNumVar var = vars[i];
            if(patternIndexVariable.matcher(var.getName()).matches()) {
                variables.add(var);
            }
        }

        return variables.toArray(new IloNumVar[variables.size()]);
    }

	/**
	 * Determine the matrix used in the BIP problem
	 * 
	 * @param cplex
	 * 		The model of the BIP problem
	 * @return
	 * 	    The matrix of @cplex	  
	 */	
	public IloLPMatrix getMatrix(IloCplex cplex) throws IloException {
        Iterator iter = cplex.getModel().iterator();
        while (iter.hasNext()) {
            Object o = iter.next();
            if (o instanceof IloLPMatrix) {
                IloLPMatrix matrix = (IloLPMatrix) o;
                return matrix;
            }
        }
        return null;
    }
	
	/**
	 * Checking whether pair of indexes (indexc, indexd) have been in the cache
	 * 
	 * @param indexc, indexd
	 * 		The two given indexes
	 * @param cachedIndexes
	 * 		The hash map containing the cache
	 * 
	 * @return {@code true/false}
	 */
	private boolean checkInCache(Index indexc, Index indexd)
	{
		String key = indexc.getName() + "+" + indexd.getName();
		if (IIPCPlex.cachedInteractIndexName.get(key) != null){
			return true;
		}
				
		return false;
	}
	
	/**
	 * Put pair of interacted indexes into the cache of CPlex class
	 * 
	 * @param indexc
	 * 		One of the pair of interacted indexes
	 * @param indexd
	 * 		One of the pair of interacted indexes
	 * 
	 */
	private void addToCache(Index indexc, Index indexd)
	{
		String combinedName = indexc.getName() + "+" + indexd.getName();		
		IIPCPlex.cachedInteractIndexName.put(combinedName, 1);
		combinedName = indexd.getName() + "+" + indexc.getName();		
		IIPCPlex.cachedInteractIndexName.put(combinedName, 1);
	}
		
}
