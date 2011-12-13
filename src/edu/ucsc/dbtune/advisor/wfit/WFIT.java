package edu.ucsc.dbtune.advisor.wfit;

import edu.ucsc.dbtune.advisor.Advisor;
import edu.ucsc.dbtune.metadata.Configuration;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.optimizer.PreparedSQLStatement;
import edu.ucsc.dbtune.workload.SQLStatement;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

/**
 * WFIT
 */
public class WFIT extends Advisor
{
	List<PreparedSQLStatement> qinfos;
	List<Double> overheads;
	List<Configuration> configurations;

	Configuration indexes;
	WorkFunctionAlgorithm wfa;
	Optimizer optimizer;

	int maxNumIndexes;
	int maxNumStates;
	int windowSize;
	int partitionIterations;

	/**
     */
	public WFIT(Optimizer optimizer, Configuration configuration,
			int maxNumIndexes, int maxNumStates, int windowSize,
			int partitionIterations) {
		this.indexes = configuration;
		this.maxNumIndexes = maxNumIndexes;
		this.maxNumStates = maxNumStates;
		this.windowSize = windowSize;
		this.partitionIterations = partitionIterations;
		this.optimizer = optimizer;
		this.qinfos = new ArrayList<PreparedSQLStatement>();
		this.wfa = new WorkFunctionAlgorithm(configuration, maxNumStates,
				maxNumIndexes);
		this.overheads = new ArrayList<Double>();
		this.configurations = new ArrayList<Configuration>();
	}

	/**
	 * Adds a query to the set of queries that are considered for
	 * recommendation.
	 * 
	 * @param sql
	 *            sql statement
	 * @throws SQLException
	 *             if the given statement can't be processed
	 */
	@Override
	public void process(SQLStatement sql) throws SQLException
{
		PreparedSQLStatement qinfo;

		qinfo = optimizer.prepareExplain(sql);

		qinfos.add(qinfo);

		// partitions =
		// getIndexPartitions(
		// indexes, qinfos, maxNumIndexes, maxNumStates, windowSize,
		// partitionIterations);

		// wfa.repartition(partitions);
		wfa.newTask(qinfo,indexes);

		configurations.add(new Configuration(wfa.getRecommendation()));
	}

	/**
	 * Returns the configuration obtained by the Advisor.
	 * 
	 * @return a {@code Configuration} object containing the information related
	 *         to the recommendation produced by the advisor.
	 * @throws SQLException
	 *             if the given statement can't be processed
	 */
	@Override
	public Configuration getRecommendation() throws SQLException
{
		if (qinfos.size() == 0) {
			return new Configuration("");
		}

		return configurations.get(qinfos.size() - 1);
	}

	public PreparedSQLStatement getStatement(int i)
{
		return qinfos.get(i);
	}
	/*
	 * public IndexPartitions getPartitions()
 { return partitions; }
	 * 
	 * private IndexPartitions getIndexPartitions( Configuration candidateSet,
	 * List<ExplainedSQLStatement> qinfos, int maxNumIndexes, int maxNumStates,
	 * int windowSize, int partitionIterations ) { IndexStatisticsFunction
	 * benefitFunc = new IndexStatisticsFunction(windowSize);
	 * 
	 * Configuration hotSet = chooseGreedy( candidateSet, new
	 * Configuration("old"), new Configuration("required"), benefitFunc,
	 * maxNumIndexes, false);
	 * 
	 * return choosePartitions( candidateSet, hotSet, partitions, benefitFunc,
	 * maxNumStates, partitionIterations); }
	 */
}
