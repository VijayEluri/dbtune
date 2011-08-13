package edu.ucsc.dbtune.optimizer;

import edu.ucsc.dbtune.connectivity.DatabaseConnection;
import edu.ucsc.dbtune.metadata.DB2Index;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.spi.core.Console;
import edu.ucsc.dbtune.util.*;

import java.sql.SQLException;
import java.util.List;

import static edu.ucsc.dbtune.connectivity.DB2Commands.*;
import static edu.ucsc.dbtune.spi.core.Functions.*;
import static edu.ucsc.dbtune.util.Instances.newLinkedList;
import static edu.ucsc.dbtune.util.Objects.cast;

/**
 * A DB2-specific What-If optimizer.
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class DB2IBGWhatIfOptimizer extends AbstractIBGWhatIfOptimizer {
    private final DatabaseConnection connection;
    private final List<Index> cachedCandidateSet = newLinkedList();

    /**
     * construct a new instance of a {@code DB2-specific} what-if optimizer.
     * @param connection
     *      a database connection
     */
    public DB2IBGWhatIfOptimizer(DatabaseConnection connection){
        super(new DB2Optimizer(connection.getJdbcConnection()));
        this.connection = connection;
    }

    @Override
    public Iterable<Index> getCandidateSet() {
        return cachedCandidateSet;
    }

    protected IndexBitSet getCachedIndexBitSet() {
        return Instances.newBitSet();
    }

    protected void updateCachedIndexBitSet(IndexBitSet newOne) {
        // do nothing
    }

    public void fixCandidates(Iterable<? extends Index> candidateSet) throws SQLException {
        cachedCandidateSet.clear();
        Iterables.copy(cachedCandidateSet, candidateSet);
        submitAll(
                submit(clearAdviseIndex(), connection),
                submit(loadAdviseIndex(), candidateSet.iterator(), false, connection)
        );
    }

    @Override
    double estimateCost(String sql, Iterable<Index> candidate, IndexBitSet configuration,IndexBitSet used)
    {
        return 0.0;
    }

    @Override
    public PreparedSQLStatement explain(String sql, Iterable<? extends Index> indexes) throws SQLException {
        fixCandidates(indexes);
        return delegate.explain(sql, indexes);
    }

    @Override
    protected double estimateCost(WhatIfOptimizationBuilder builder) throws SQLException {
        whatIfCount++;
        if (whatIfCount % 80 == 0) {
            fixAnyExistingCandidates();
            System.err.println();
        }

        final WhatIfOptimizationBuilderImpl whatIfImpl  = Objects.cast(builder, WhatIfOptimizationBuilderImpl.class);

        if(!whatIfImpl.withProfiledIndex()){
           return estimateCostWithoutProfiledIndex(whatIfImpl, connection);
        }  else {
           return estimateCostBasedOnProfiledIndex(whatIfImpl, connection);
        }

    }

    private void fixAnyExistingCandidates() throws SQLException {
        fixCandidates(getCandidateSet());
    }

    @SuppressWarnings({"UnusedAssignment"})
    private static Double estimateCostBasedOnProfiledIndex(
            WhatIfOptimizationBuilderImpl whatIfImpl,
            DatabaseConnection activeConnection
    ) throws SQLException {
        int explainCount; // should be equal to 1
        double totalCost;

        final String        sql             = whatIfImpl.getSQL();
        final DB2Index      profiledIndex   = cast(whatIfImpl.getProfiledIndex(), DB2Index.class);
        final IndexBitSet   usedColumns     = whatIfImpl.getUsedColumns();
        final IndexBitSet   configuration   = whatIfImpl.getConfiguration();
        submitAll(
                // clear explain tables that we will end up reading
                submit(clearExplainObject(), activeConnection),
                submit(clearExplainStatement(), activeConnection),
                submit(clearExplainPredicate(), activeConnection),
                // enable indexes and set explain mode
                submit(enableAdviseIndexRows(), activeConnection, configuration),
                submit(explainModeEvaluateIndexes(), activeConnection)
        );

        try {
            activeConnection.execute(sql);
            Console.streaming().dot();
        } catch (SQLException e){
            final StringBuilder errorMessage = new StringBuilder();
            errorMessage.append('.');
            errorMessage.append(e.getLocalizedMessage());
            Console.streaming().error(errorMessage.toString());
            throw e;
        }

        // reset explain mode (indexes are left enabled...)
        submit(explainModeNo(), activeConnection);
        // post-process the explain tables
        // first get workload cost
        CostLevel result = supplyValue(fetchExplainStatementTotals(), activeConnection);
        try {
            explainCount = result.getCount();
            totalCost    = result.getTotalCost();
        } catch (Exception e){
            result = result.close();
            throw new SQLException(e);
        }
        result = result.close();
        if (explainCount != 1){
            throw new SQLException(
                    "unexpected number of statements: "
                            + explainCount
                            + " (expected 1)"
            );
        }

        if(profiledIndex != null){
            final String preds = supplyValue(fetchExplainPredicateString(), activeConnection);
            for (int i = 0; i < profiledIndex.size(); i++) {
                if (preds.contains(profiledIndex.getColumn(i).getName())){
                    usedColumns.set(i);
                }
            }
        }

        activeConnection.commit();
        return totalCost;
    }


    @SuppressWarnings({"UnusedAssignment"})
    private static Double estimateCostWithoutProfiledIndex(
            WhatIfOptimizationBuilderImpl whatIfImpl,
            DatabaseConnection activeConnection
    ) throws SQLException {

        int explainCount; // should be equal to 1
        double totalCost;
        final String        sql     = whatIfImpl.getSQL();
        final IndexBitSet   config  = whatIfImpl.getConfiguration();
        final IndexBitSet   usedSet = whatIfImpl.getUsedSet();

        // silently supply a command and an input parameter so that it could
        // get executed in the background (since no return to the caller is needed)
        submitAll(
                // clear explain tables that we will end up reading
                submit(clearExplainObject(), activeConnection),
                submit(clearExplainStatement(), activeConnection),
                // enable indexes and set explain mode
                submit(enableAdviseIndexRows(), activeConnection, config),
                submit(explainModeEvaluateIndexes(), activeConnection)
        );

        // evaluate the query
        try {
            activeConnection.execute(sql);
            //throw new Error("returned from execute() in what-if mode");
            Console.streaming().dot();
        } catch (SQLException e) {
            Console.streaming().dot();
            throw e;
            // expected in explain mode
        }

        // reset explain mode (indexes are left enabled...)
        submit(explainModeNo(), activeConnection);


        // post-process the explain tables
        // first get workload cost
        CostLevel costLevel = supplyValue(fetchExplainStatementTotals(), activeConnection);
        try {
            explainCount = costLevel.getCount();
            totalCost    = costLevel.getTotalCost();
        } catch (Throwable e) {
            costLevel = costLevel.close();
            throw new SQLException(e);
        }
        costLevel = costLevel.close();
        Checks.checkSQLRelatedState(
                explainCount == 1,
                "Error: Unexpected number of statements: "
                        + explainCount
                        + " (expected 1)"
        );

        // now get used indexes, using the input BitSet
        usedSet.clear();
        submit(fetchExplainObjectCandidates(), activeConnection, usedSet);
        activeConnection.commit();

        return totalCost;
    }

    @Override
    public String toString() {
        return new ToStringBuilder<DB2IBGWhatIfOptimizer>(this)
               .add("what if count", whatIfCount)
               .add("candidate index set", getCandidateSet())
             .toString();
    }
}
