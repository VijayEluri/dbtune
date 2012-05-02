package edu.ucsc.dbtune.advisor.wfit;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import edu.ucsc.dbtune.advisor.Advisor;
import edu.ucsc.dbtune.advisor.RecommendationStatistics;
import edu.ucsc.dbtune.advisor.interactions.DegreeOfInteractionFinder;
import edu.ucsc.dbtune.advisor.interactions.IBGDoiFinder;
import edu.ucsc.dbtune.advisor.interactions.InteractionBank;
import edu.ucsc.dbtune.metadata.ByContentIndex;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.optimizer.ExplainedSQLStatement;
import edu.ucsc.dbtune.optimizer.IBGOptimizer;
import edu.ucsc.dbtune.optimizer.IBGPreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.workload.SQLStatement;

import static edu.ucsc.dbtune.advisor.wfit.WorkFunctionAlgorithm.transitionCost;
import static edu.ucsc.dbtune.util.MetadataUtils.convert;
import static edu.ucsc.dbtune.util.MetadataUtils.toByContent;

/**
 *
 * @author Ivo Jimenez
 */
public class WFIT extends Advisor
{
    private IBGOptimizer ibgOptimizer;
    private Selector selector;
    private Set<Index> pool;
    private DegreeOfInteractionFinder doiFinder;
    private RecommendationStatistics stats;
    private BitSet previousState;

    /**
     * Creates a WFIT advisor.
     *
     * @param optimizer
     *      used to execute what-if calls
     */
    public WFIT(Optimizer optimizer)
    {
        if (!(optimizer instanceof IBGOptimizer))
            throw new RuntimeException(
                    "Expecting IBGOptimizer; found: " + optimizer.getClass().getName());

        ibgOptimizer = (IBGOptimizer) optimizer;

        selector = new Selector();
        doiFinder = new IBGDoiFinder();
        pool = new HashSet<Index>();
        stats = new RecommendationStatistics("WFIT");
        previousState = new BitSet();
    }

    /**
     * Creates a WFIT advisor.
     *
     * @param optimizer
     *      used to execute what-if calls
     * @param initialSet
     *      initial candidate set
     */
    public WFIT(Optimizer optimizer, Set<Index> initialSet)
    {
        if (!(optimizer instanceof IBGOptimizer))
            throw new RuntimeException(
                    "Expecting IBGOptimizer; found: " + optimizer.getClass().getName());

        ibgOptimizer = (IBGOptimizer) optimizer;

        selector = new Selector(initialSet);
        doiFinder = new IBGDoiFinder();
        pool = new HashSet<Index>(initialSet);
        stats = new RecommendationStatistics("WFIT");
        previousState = new BitSet();
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
        pool.addAll(toByContent(ibgOptimizer.recommendIndexes(sql)));

        int whatIfCountBefore = ibgOptimizer.getWhatIfCount();

        IBGPreparedSQLStatement pStmt = (IBGPreparedSQLStatement) ibgOptimizer.prepareExplain(sql);
        ExplainedSQLStatement eStmt = pStmt.explain(pool);

        int whatIfCountAfter = ibgOptimizer.getWhatIfCount();

        InteractionBank bank = doiFinder.degreeOfInteraction(pStmt, pool);

        selector.analyzeQuery(
                new ProfiledQuery(
                    sql.getSQL(),
                    eStmt,
                    getSnapshot(pool),
                    pool,
                    pStmt.getIndexBenefitGraph(),
                    bank,
                    whatIfCountAfter - whatIfCountBefore));

        BitSet newState = new BitSet();
        for (Index idx : selector.getRecommendation())
            newState.set(idx.getId());
        
        stats.addNewEntry(
                eStmt.getTotalCost(), selector.getRecommendation(),
                transitionCost(getSnapshot(pool), previousState, newState));

        previousState = newState;
    }

    /**
     * Creates a {@link CandidatePool.Snapshot} out of a index set.
     *
     * @param indexes
     *      set from which the snapshot is created
     * @return
     *      the snapshot
     * @throws SQLException
     *      if an error occurs while adding indexes to the snapshot
     */
    private CandidatePool.Snapshot getSnapshot(Set<Index> indexes)
        throws SQLException
    {
        CandidatePool pool = new CandidatePool();

        for (Index i : indexes)
            pool.addIndex(i);

        return pool.getSnapshot();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Index> getRecommendation() throws SQLException
    {
        Set<Index> recommendation = new HashSet<Index>();

        for (Index idx : selector.getRecommendation())
            recommendation.add(new ByContentIndex(idx));

        return recommendation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecommendationStatistics getRecommendationStatistics()
    {
        return stats;
    }

    /**
     * {@inheritDoc}
     */
    public RecommendationStatistics getOptimalRecommendationStatistics()
    {
        RecommendationStatistics optStats = new RecommendationStatistics("OPT");
        BitSet[] optimalSchedule = selector.getOptimalScheduleRecommendation();

        BitSet prevState = new BitSet();

        int i = 0;

        for (BitSet bs : optimalSchedule) {

            BitSet newState = new BitSet();

            newState.set(bs);

            try {
                optStats.addNewEntry(
                        selector.getCost(i, bs), convert(optimalSchedule[i], pool),
                        transitionCost(getSnapshot(pool), prevState, newState));
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }

            prevState = newState;
        }

        return optStats;
    }
}
