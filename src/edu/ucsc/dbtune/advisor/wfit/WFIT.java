/* ************************************************************************** *
 *   Copyright 2010 University of California Santa Cruz                       *
 *                                                                            *
 *   Licensed under the Apache License, Version 2.0 (the "License");          *
 *   you may not use this file except in compliance with the License.         *
 *   You may obtain a copy of the License at                                  *
 *                                                                            *
 *       http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                            *
 *   Unless required by applicable law or agreed to in writing, software      *
 *   distributed under the License is distributed on an "AS IS" BASIS,        *
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied  *
 *   See the License for the specific language governing permissions and      *
 *   limitations under the License.                                           *
 * ************************************************************************** */
package edu.ucsc.dbtune.advisor.wfit;

import edu.ucsc.dbtune.advisor.Advisor;
import edu.ucsc.dbtune.advisor.interactions.IndexPartitions;
import edu.ucsc.dbtune.advisor.interactions.IndexStatisticsFunction;
import edu.ucsc.dbtune.metadata.Configuration;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.optimizer.IBGOptimizer;
import edu.ucsc.dbtune.optimizer.IBGPreparedSQLStatement;
import edu.ucsc.dbtune.workload.SQLStatement;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

import static edu.ucsc.dbtune.advisor.wfit.HotSetSelector.chooseGreedy;
import static edu.ucsc.dbtune.advisor.interactions.InteractionSelector.choosePartitions;

/**
 * WFIT
 */
public class WFIT extends Advisor
{
    List<IBGPreparedSQLStatement> qinfos;
    List<Double>                  overheads;
    List<Configuration>           configurations;

    Configuration         indexes;
    IndexPartitions       partitions;
    WorkFunctionAlgorithm wfa;
    IBGOptimizer          ibgOptimizer;

    int maxNumIndexes;
    int maxNumStates;
    int windowSize;
    int partitionIterations;

    /**
     */
    public WFIT(
            Optimizer optimizer,
            Configuration configuration,
            int maxNumIndexes,
            int maxNumStates,
            int windowSize,
            int partitionIterations)
    {
        this.indexes             = configuration;
        this.maxNumIndexes       = maxNumIndexes;
        this.maxNumStates        = maxNumStates;
        this.windowSize          = windowSize;
        this.partitionIterations = partitionIterations;
        this.ibgOptimizer        = new IBGOptimizer(optimizer); // XXX: check if optimizer is IBGOptimizer
        this.qinfos              = new ArrayList<IBGPreparedSQLStatement>();
        this.wfa                 = new WorkFunctionAlgorithm(configuration,partitions,maxNumStates,maxNumIndexes);
        this.overheads           = new ArrayList<Double>();
        this.configurations      = new ArrayList<Configuration>();
    }

    /**
     * Adds a query to the set of queries that are considered for recommendation.
     *
     * @param sql
     *      sql statement
     * @throws SQLException
     *      if the given statement can't be processed
     */
    @Override
    public void process(SQLStatement sql) throws SQLException
    {
        IBGPreparedSQLStatement qinfo;

        qinfo = (IBGPreparedSQLStatement) ibgOptimizer.explain(sql,indexes);

        qinfos.add(qinfo);

        partitions =
            getIndexPartitions(
                indexes, qinfos, maxNumIndexes, maxNumStates, windowSize, partitionIterations);

        wfa.repartition(partitions);
        wfa.newTask(qinfo);

        configurations.add(new Configuration(wfa.getRecommendation()));
    }

    /**
     * Returns the configuration obtained by the Advisor.
     *
     * @return
     *      a {@code Configuration} object containing the information related to
     *      the recommendation produced by the advisor.
     * @throws SQLException
     *      if the given statement can't be processed
     */
    @Override
    public Configuration getRecommendation() throws SQLException
    {
        if(qinfos.size() == 0) {
            return new Configuration("");
        }

        return configurations.get(qinfos.size()-1);
    }

    public IBGPreparedSQLStatement getStatement(int i) {
        return qinfos.get(i);
    }
    public IndexPartitions getPartitions() {
        return partitions;
    }

    private IndexPartitions getIndexPartitions(
            Configuration candidateSet,
            List<IBGPreparedSQLStatement> qinfos, 
            int maxNumIndexes,
            int maxNumStates,
            int windowSize,
            int partitionIterations )
    {
        IndexStatisticsFunction benefitFunc = new IndexStatisticsFunction(windowSize);

        Configuration hotSet  =
            chooseGreedy(
                    candidateSet,
                    new Configuration("old"),
                    new Configuration("required"),
                    benefitFunc, maxNumIndexes, false);

        return choosePartitions(
                candidateSet,
                hotSet,
                getPartitions(),
                benefitFunc,
                maxNumStates,
                partitionIterations);
    }
}
