package edu.ucsc.dbtune.advisor.wfit;

import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

import edu.ucsc.dbtune.DatabaseSystem;

import edu.ucsc.dbtune.advisor.Advisor;
import edu.ucsc.dbtune.advisor.RecommendationStatistics;
import edu.ucsc.dbtune.advisor.interactions.DegreeOfInteractionFinder;
import edu.ucsc.dbtune.advisor.interactions.IBGDoiFinder;
import edu.ucsc.dbtune.advisor.interactions.InteractionBank;

import edu.ucsc.dbtune.metadata.Index;

import edu.ucsc.dbtune.optimizer.ExplainedSQLStatement;
import edu.ucsc.dbtune.optimizer.PreparedSQLStatement;

import edu.ucsc.dbtune.workload.SQLStatement;

import static edu.ucsc.dbtune.util.MetadataUtils.findOrThrow;

/**
 * @author Ivo Jimenez
 */
public class WFIT extends Advisor
{
    private DatabaseSystem db;
    private SATuningDBTuneTranslator wfitDriver;
    private Set<Index> pool;
    private DegreeOfInteractionFinder doiFinder;
    private WFITRecommendationStatistics stats;
    private WFITRecommendationStatistics optStats;
    private boolean isCandidateSetFixed;

    /**
     * Creates a WFIT advisor, with an empty initial candidate set.
     *
     * @param db
     *      the dbms where wfit will run on
     * @param maxHotSetSize
     *      maximum number of candidates to keep in the hot set
     * @param indexStatisticsWindowSize
     *      size of the sliding window of interaction-related measurements
     * @param maxNumberOfStates
     *      maximum number of states per partition
     * @param numberOfPartitionIterations
     *      number of attempts that the repartitioning algorithm executes to stabilize the candidate 
     *      set partition
     */
    public WFIT(
        DatabaseSystem db,
        int maxHotSetSize,
        int maxNumberOfStates,
        int indexStatisticsWindowSize,
        int numberOfPartitionIterations)
    {
        this(db, new TreeSet<Index>(), maxHotSetSize, indexStatisticsWindowSize, maxNumberOfStates, 
                numberOfPartitionIterations);

        this.isCandidateSetFixed = false;
    }

    /**
     * Creates a WFIT advisor, with the given candidate set as the initial candidate set.
     *
     * @param db
     *      the dbms where wfit will run on
     * @param initialSet
     *      initial candidate set
     * @param maxHotSetSize
     *      maximum number of candidates to keep in the hot set
     * @param indexStatisticsWindowSize
     *      size of the sliding window of interaction-related measurements
     * @param maxNumberOfStates
     *      maximum number of states per partition
     * @param numberOfPartitionIterations
     *      number of attempts that the repartitioning algorithm executes to stabilize the candidate 
     *      set partition
     */
    public WFIT(
            DatabaseSystem db,
            Set<Index> initialSet,
            int maxHotSetSize,
            int maxNumberOfStates,
            int indexStatisticsWindowSize,
            int numberOfPartitionIterations)
    {
        this(db, initialSet, new IBGDoiFinder(), maxHotSetSize, indexStatisticsWindowSize, 
                maxNumberOfStates, numberOfPartitionIterations);
    }

    /**
     * Creates a WFIT advisor with the given initial candidate set, DoI finder and components 
     * parameters.
     *
     * @param db
     *      the dbms where wfit will run on
     * @param initialSet
     *      initial candidate set
     * @param doiFinder
     *      interaction finder
     * @param maxHotSetSize
     *      maximum number of candidates to keep in the hot set
     * @param indexStatisticsWindowSize
     *      size of the sliding window of interaction-related measurements
     * @param maxNumberOfStates
     *      maximum number of states per partition
     * @param numberOfPartitionIterations
     *      number of attempts that the repartitioning algorithm executes to stabilize the candidate 
     *      set partition
     */
    WFIT(
            DatabaseSystem db,
            Set<Index> initialSet,
            DegreeOfInteractionFinder doiFinder,
            int maxHotSetSize,
            int maxNumberOfStates,
            int indexStatisticsWindowSize,
            int numberOfPartitionIterations)
    {
        this.db = db;
        this.doiFinder = doiFinder;

        this.wfitDriver =
            new SATuningDBTuneTranslator(
                    db.getCatalog(),
                    initialSet,
                    maxHotSetSize,
                    maxNumberOfStates,
                    indexStatisticsWindowSize,
                    numberOfPartitionIterations);

        this.pool = new TreeSet<Index>(initialSet);
        this.stats = new WFITRecommendationStatistics("WFIT");
        this.optStats = new WFITRecommendationStatistics("OPT");

        if (initialSet.isEmpty())
            this.isCandidateSetFixed = false;
        else
            this.isCandidateSetFixed = true;
    }

    /**
     * Adds a query to the set of queries that are considered for
     * recommendation.
     * 
     * @param sql
     *      sql statement
     * @throws SQLException
     *      if the given statement can't be processed
     */
    @Override
    public void process(SQLStatement sql) throws SQLException
    {
        if (!isCandidateSetFixed)
            pool.addAll(db.getOptimizer().recommendIndexes(sql));

        PreparedSQLStatement  pStmt = db.getOptimizer().prepareExplain(sql);
        ExplainedSQLStatement eStmt = pStmt.explain(pool);
        InteractionBank       bank  = doiFinder.degreeOfInteraction(pStmt, pool);

        wfitDriver.analyzeQuery(sql.getSQL(), pStmt, eStmt, pool, bank);

        Set<Index> recommendation = getRecommendation();

        stats.addNewEntry(
            pStmt.explain(recommendation).getTotalCost(),
            pool,
            recommendation,
            getStablePartitioning(),
            wfitDriver.getWorkFunctionScores(pool));

        if (isCandidateSetFixed)
            getOptimalRecommendationStatistics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Index> getRecommendation() throws SQLException
    {
        return wfitDriver.getRecommendation();
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
     * Returns the stable partitioning of the current candidate set.
     *
     * @return
     *      set of sets of indexes, where each set corresponds to a partition
     */
    public Set<Set<Index>> getStablePartitioning()
    {
        return wfitDriver.getStablePartitioning(pool);
    }

    /**
     * The statistics corresponding to the idealized {@code OPT} algorithm.
     *
     * @return
     *      recommendation statistics for {@code OPT}
     * @throws SQLException
     *      if the candidate set wasn't specified from the beginning
     */
    public RecommendationStatistics getOptimalRecommendationStatistics()
        throws SQLException
    {
        if (!isCandidateSetFixed)
            throw new SQLException("Can't produce OPT without specifying an initial candidate set");
            
        optStats.clear();

        int i = 0;

        for (Set<Index> optRecommendation : wfitDriver.getOptimalScheduleRecommendation(pool))
            optStats.addNewEntry(
                wfitDriver.getCost(i, optRecommendation),
                pool,
                optRecommendation,
                new TreeSet<Set<Index>>());

        return optStats;
    }

    /**
     * Gets the pool for this instance.
     *
     * @return The pool.
     */
    public Set<Index> getPool()
    {
        return this.pool;
    }

    /**
     * Gives a positive vote for the given index.
     *
     * @param id
     *      id of index being voted
     */
    public void voteUp(int id)
    {
        voteUp(findOrThrow(pool, id));
    }

    /**
     * Gives a positive vote for the given index.
     *
     * @param index
     *      index being voted
     */
    public void voteUp(Index index)
    {
        wfitDriver.vote(index, true);
    }

    /**
     * Gives a negative vote for the given index.
     *
     * @param id
     *      id of index being voted
     * @throws SQLException
     *      if the index can't be voted
     */
    public void voteDown(int id) throws SQLException
    {
        voteDown(findOrThrow(pool, id));
    }

    /**
     * Gives a negative vote for the given index.
     *
     * @param index
     *      index being voted
     */
    public void voteDown(Index index)
    {
        wfitDriver.vote(index, false);
    }
}
