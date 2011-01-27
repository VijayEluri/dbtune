package edu.ucsc.dbtune.core;

import edu.ucsc.dbtune.core.metadata.PGReifiedTypes;
import edu.ucsc.dbtune.core.optimizers.WhatIfOptimizationBuilder;
import edu.ucsc.dbtune.util.*;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.ucsc.dbtune.core.metadata.PGCommands.explainIndexesCost;
import static edu.ucsc.dbtune.spi.core.Commands.supplyValue;
import static edu.ucsc.dbtune.util.Instances.*;

/**
 * A Postgres-specific What-if optimizer.
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
class PostgresIBGWhatIfOptimizer extends AbstractIBGWhatIfOptimizer {
    private final AtomicInteger      whatifCount;
    private final IndexBitSet        cachedBitSet       = newBitSet();
    private final List<DBIndex>      cachedCandidateSet = newList();

    /**
     * construct a {@code Postgres-WhatIfOptimizer} object.
     * @param connection
     *      a database connection
     */
    PostgresIBGWhatIfOptimizer(DatabaseConnection connection){
        super(new PostgresWhatIfOptimizer(connection));
        whatifCount = newAtomicInteger();
    }

    @Override
    public void fixCandidates(Iterable<? extends DBIndex> candidateSet) throws SQLException {
        Checks.checkArgument(isEnabled(), "Error: Database Connection is closed.");
        cachedCandidateSet.clear();
        getCachedIndexBitSet().clear();
        for (DBIndex idx : candidateSet) {
            cachedCandidateSet.add(idx);
            getCachedIndexBitSet().set(idx.internalId());
        }
    }

    @Override
    public ExplainInfo explain(String sql) throws SQLException {
        final ExplainInfo result = super.explain(sql);
        incrementWhatIfCount();
        return result;
    }

    @Override
    protected IndexBitSet getCachedIndexBitSet() {
        return cachedBitSet;
    }

    @Override
    protected void updateCachedIndexBitSet(IndexBitSet newOne) {
        cachedBitSet.set(newOne);
    }


    @Override
    public Iterable<DBIndex> getCandidateSet() {
        return cachedCandidateSet;
    }

    @Override
    protected void incrementWhatIfCount() {
        whatifCount.incrementAndGet();
    }

    @Override
    protected double estimateCost(WhatIfOptimizationBuilder builder) throws SQLException {
        incrementWhatIfCount();
        Debug.print(".");
        if (whatifCount.get() % 75 == 0) Debug.println();

        final WhatIfOptimizationBuilderImpl whatIfImpl = Objects.cast(builder, WhatIfOptimizationBuilderImpl.class);
        if(whatIfImpl.withProfiledIndex()) throw new UnsupportedOperationException("Error: Used Columns in Database Connection not supported.");

        final String                            sql             = whatIfImpl.getSQL();
        final IndexBitSet                       usedSet         = whatIfImpl.getUsedSet();
        final IndexBitSet                       configuration   = whatIfImpl.getConfiguration();
        final PGReifiedTypes.ReifiedPGIndexList indexSet        = makeIndexList();
        return supplyValue(
                explainIndexesCost(usedSet),
                getConnection(),
                indexSet,
                configuration,
                sql,
                configuration.cardinality(),
                new Double[indexSet.size()]
        );
    }

    private PGReifiedTypes.ReifiedPGIndexList makeIndexList(){
        return new PGReifiedTypes.ReifiedPGIndexList(cachedCandidateSet);
    }

    @Override
    public int getWhatIfCount() {
        return whatifCount.get();
    }

    @Override
    public String toString() {
        return new ToStringBuilder<PostgresIBGWhatIfOptimizer>(this)
               .add("whatIf count", getWhatIfCount())
               .add("index set", getCachedIndexBitSet())
               .add("candidate index set", getCandidateSet())
            .toString();
    }
}
