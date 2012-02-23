package interaction;

import interaction.cand.Generation;
import interaction.cand.Generation.Strategy;
import interaction.db.*;
import interaction.ibg.*;
import static interaction.ibg.AnalysisMode.SERIAL;
import interaction.ibg.log.BasicLog;
import interaction.ibg.log.InteractionLogger;
import interaction.ibg.log.BasicLog.InteractionPair;
import interaction.ibg.parallel.*;
import interaction.ibg.serial.*;
import interaction.util.Files;
import interaction.workload.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.ucsc.dbtune.bip.InteractionComparisonFunctionalTest;


/*
 * Builds the IBGs for the queries in a workload and discovers  
 * the interactions that exist. This file can be used to
 * invoke the serial analysis or parallel analysis. When the
 * parallel analysis runs, the IBG building and analysis are 
 * actually run simultaneously. The serial mode does the steps
 * one at a time, but usually a very large percentage of time is 
 * spent building the IBG, and only a small amount of time is
 * needed for analysis.
 */
public class AnalysisMain {
	public static void main(String[] args) {
		// process arguments
		if (args.length > 0) {
			System.out.println("No arguments are accepted");
		}
		
		// do the steps
		try {
			runSteps();
		} catch (Throwable t) {	
			t.printStackTrace();
		} finally { 	
			// ensures that all threads exit
			System.exit(0);
		}
	}

	public static void runSteps() throws SQLException, IOException, ClassNotFoundException {
		// Connect to database
		DBConnection conn = Main.openConnection();
		try {
			SQLWorkload workload = Main.getWorkload();
			
			for (Generation.Strategy s : InteractionComparisonFunctionalTest.strategies)
			    analyze(conn, workload, s);

		} finally {
			conn.commit();
			conn.close();
		}
	}
	
	private static void analyze(DBConnection conn, SQLWorkload workload, Generation.Strategy strategy) 
	                throws IOException, ClassNotFoundException, SQLException {
		
		long start = System.currentTimeMillis();		
		InteractionLogger logger;
		SerialIndexBenefitGraph[] ibgs;
		File candidateFile = Configuration.candidateFile(strategy);
		DB2IndexSet candidateSet = (DB2IndexSet) Files.readObjectFromFile(candidateFile);
		
		
		// Vary the size of candidateSet
		if (candidateSet.size() > InteractionComparisonFunctionalTest.MAX_NUM_INDEX) {
    		
    		List<Integer>                 ids = new ArrayList<Integer>();
    		Map<Integer, DB2Index> mapIDIndex = new HashMap<Integer, DB2Index>();
    		
    		for (Iterator<DB2Index> iter = candidateSet.iterator(); iter.hasNext(); ) {
    		    DB2Index index = iter.next();    		    
    		    ids.add(index.getId());
    		    mapIDIndex.put(index.getId(), index);
    		}
    		
    		Collections.sort(ids);
    		DB2IndexSet temp = new DB2IndexSet();
    		for (int count = 0; count < InteractionComparisonFunctionalTest.MAX_NUM_INDEX; 
    		     count++)
    		    temp.add(mapIDIndex.get(ids.get(count)));
    		
    		candidateSet = temp;
		}
		
		System.out.println(" L107 (Karl, Analysis), candidate set: " + candidateSet.size());
		
		conn.fixCandidates(candidateSet);
		logger = new InteractionLogger(conn, candidateSet);	
		
		// reset logger and analyze serially
		// then write out the ibgs and analysis
		logger.reset();
		ibgs = analyzeSerial(conn, workload, candidateSet, logger);
		writeAnalysis(strategy, SERIAL, logger.getBasicLog());
		writeIBGs(ibgs, strategy);
		
		long time = System.currentTimeMillis() - start;
		System.out.println("L120 (Karl, Analysis Main), the running time: " + time);
		
		double[] thresholds = new double[] {
         //       0.01,
         //       0.1,
                1.0
        };

		BasicLog serial1 = (BasicLog)Files.readObjectFromFile(
		                            Configuration.analysisFile(strategy, SERIAL));
		
		for (double t : thresholds) {
		    serial1.getAnalysisLog(t);
		    writeInteraction(serial1.interactions(), strategy, SERIAL, t);
		}
		
		// test
		/*
		PrintWriter out = new PrintWriter(System.out);
		try {
			System.out.println("Serial log:");
			BasicLog serial1 = (BasicLog)Files.readObjectFromFile(Configuration.analysisFile(strategy, SERIAL));
			AnalysisLog serial2 = serial1.getAnalysisLog(0.1);
			serial2.output(out);
			
			System.out.println("L84 (Analysis Main), the running time: " + time);
			System.out.println("List interactions: " + serial1.interactions());
			/*
			// reset logger and analyze in parallel
			logger.reset();
			analyzeParallel(conn, workload, candidateSet, logger);
			writeAnalysis(strategy, PARALLEL, logger.getBasicLog());

			System.out.println("Parallel log:");
			BasicLog parallel1 = (BasicLog)Files.readObjectFromFile(Configuration.analysisFile(strategy, PARALLEL));
			AnalysisLog parallel2 = parallel1.getAnalysisLog(0.1);
			parallel2.output(out);
			
		} finally {
			out.close();
		}
		*/
	}

	public static SerialIndexBenefitGraph[] analyzeSerial(DBConnection conn, SQLWorkload xacts, DB2IndexSet candidateSet, InteractionLogger logger) 
	throws SQLException {
		SerialIndexBenefitGraph[] ibgs;
		int i;
		
		logger.startTimer();
		
		/* analyze queries one at a time, combining their interactions */
		ibgs = new SerialIndexBenefitGraph[xacts.transactionCount()];
		i = 0;
		for (SQLTransaction xact : xacts) {
			SerialIndexBenefitGraph ibg = SerialIndexBenefitGraph.build(conn, new SQLWorkload(xact), candidateSet);
			SerialIBGAnalyzer analyzer = new SerialIBGAnalyzer(ibg);
			analyzer.doAnalysis(logger);
			ibgs[i++] = ibg;
		}
		
		//DB2GreedyScheduler.schedule(candidateSet.bitSet(), new SerialIndexBenefitGraph.ScheduleInfo(ibgs)); 

		return ibgs;
	}
	
	public static IndexBenefitGraph[] analyzeParallel(DBConnection conn, SQLWorkload xacts, DB2IndexSet candidateSet, InteractionLogger logger)
	throws SQLException {
		IndexBenefitGraph[] ibgs;
		IBGAnalyzer[] analyzers;
		int xactCount;
		int i;
		
		logger.startTimer();
		
		// initialize IBGs and Analyzers, separate one for each transaction
		xactCount = xacts.transactionCount();
		ibgs = new IndexBenefitGraph[xactCount];
		analyzers = new IBGAnalyzer[xactCount];
		i = 0;
		for (SQLTransaction xact : xacts) {
			ibgs[i] = new IndexBenefitGraph(conn, new SQLWorkload(xact), candidateSet);
			analyzers[i] = new IBGAnalyzer(ibgs[i]);
			i++;
		}
		
		// start the threads
		Thread analysisThread = new Thread(new RRAnalysisThread(analyzers, candidateSet, logger));
		Thread constructionThread = new Thread(new RRConstructionThread(ibgs));
		constructionThread.start();
		analysisThread.start();
		
		while (true) {
			try {
				analysisThread.join();
				constructionThread.join();
				break;
			} catch (InterruptedException e) {
				System.err.println("join() interrupted");
			}
		}

		//logger.print();
		//new interaction.ibg.multi.IBGPrinter().print(ibgs[0]);
		//multiSchedules(candidateSet, ibgs, bank);
		
		return ibgs;
	}
	
	private static void writeAnalysis(Generation.Strategy strategy, AnalysisMode mode, BasicLog log) throws IOException {
		File analysisFile = Configuration.analysisFile(strategy, mode);
		ObjectOutputStream out = new ObjectOutputStream(Files.initOutputFile(analysisFile));
		try {
			out.writeObject(log);
		} finally {
			out.close();
		}
	}
	
	private static void writeIBGs(SerialIndexBenefitGraph[] ibgs, Generation.Strategy strategy) throws IOException {
		File ibgFile = Configuration.ibgFile(strategy);
		ObjectOutputStream out = new ObjectOutputStream(Files.initOutputFile(ibgFile));
		try {
			out.writeObject(ibgs);
		} finally {
			out.close();
		}
	}
	
	public static void writeInteraction(List<InteractionPair> pairs,
	                                    Strategy s, AnalysisMode m, double t)
	{
	    try {
            PrintWriter writer = new PrintWriter (
                    new BufferedWriter(new FileWriter(
                                        Configuration.logInteractionFile(s, m, t), false), 65536));
            
            System.out.println(" file name: " + Configuration.logInteractionFile(s, m, t));
            for (InteractionPair pair : pairs)
                writer.println(pair);
            
            writer.close();
            
        } catch (IOException e) {            
            e.printStackTrace();
        }
	}
}