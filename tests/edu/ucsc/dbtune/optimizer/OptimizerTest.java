package edu.ucsc.dbtune.optimizer;

import java.util.HashSet;
import java.util.Set;

import edu.ucsc.dbtune.metadata.Catalog;
import edu.ucsc.dbtune.metadata.Column;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.util.BitArraySet;
import edu.ucsc.dbtune.workload.SQLCategory;
import edu.ucsc.dbtune.workload.SQLStatement;

import org.junit.BeforeClass;
import org.junit.Test;

import static edu.ucsc.dbtune.metadata.Index.NON_UNIQUE;
import static edu.ucsc.dbtune.metadata.Index.SECONDARY;
import static edu.ucsc.dbtune.metadata.Index.UNCLUSTERED;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Unit test for optimizer implementations.
 * <p>
 * Checks, among other things, that an optimizer is well-behaved and complies to the monotonicity 
 * and sanity properties. For more information on what these properties mean, refer to page 57 
 * (Chapter 4, Definition 4.3, Property 4.1 and 4.2 respectively).
 *
 * @author Huascar A. Sanchez
 * @author Ivo Jimenez
 * @see <a href="http://http://bit.ly/wXaQC3">
 *         "On-line Index Selection for Physical Database Tuning"
 *      </a>
 */
public class OptimizerTest
{
    /**
     */
    @BeforeClass
    public static void beforeClass()
    {
        // env = new Environment(configureAny());
        // note: issue #104 setUp mock objects and complete all the empty test methods
    }

    /**
     * Checks that each supported optimizer returns not-null instances.
     */
    @Test
    public void testNotNull()
    {
    }

    /**
     * @see OptimizerTest#checkExplain
     */
    @Test
    public void testExplain()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkUsedExplain(opt);
        }
        */
    }

    /**
     * @see OptimizerTest#checkWhatIfExplain
     */
    @Test
    public void testWhatIfExplain()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkWhatIfExplain(opt);
        }
        */
    }

    /**
     * @see OptimizerTest#checkRecommendIndexes
     */
    @Test
    public void testRecommendIndexes()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkRecommendIndexes(opt);
        }
        */
    }

    /**
     * Checks that, prepared statements that each supported optimizer generates, the set of used 
     * physical structures is correct.
     *
     * @see checkUsedConfiguration
     */
    @Test
    public void testUsedConfiguration()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkUsedConfiguration(opt);
        }
        */
    }

    /**
     * Checks that each supported optimizer is well behaved.
     *
     * @see checkIsWellBehaved
     */
    @Test
    public void testIsWellBehaved()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkIsWellBehaved(opt);
        }
        */
    }

    /**
     * Checks that each supported optimizer complies with the monotonicity property.
     *
     * @see checkMonotonicity
     */
    @Test
    public void testMonotonicity()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkMonotonicity(opt);
        }
        */
    }

    /**
     * Checks that each supported optimizer complies with the sanity property.
     *
     * @see checkSanity
     */
    @Test
    public void testSanity()
    {
        /*
        for (Optimizer opt : getSupportedOptimizersIterator(env)) {
            checkSanity(opt);
        }
        */
    }

    /**
     * Checks that a "regular" explain operation is done correctly. A "regular" cost estimation is 
     * an optimization call without hypothetical structures (or one empty hypothetical 
     * configuration).
     *
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkExplain(Optimizer opt) throws Exception
    {
        SQLStatement sql;
        ExplainedSQLStatement sqlp;
        Set<Index> conf;
        double cost1;
        double cost2;

        sql   = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        sqlp  = opt.explain(sql);
        cost1 = sqlp.getSelectCost();

        assertThat(sqlp, notNullValue());
        assertThat(sqlp.getStatement().getSQLCategory().isSame(SQLCategory.SELECT), is(true));
        assertThat(sqlp.getSelectCost(), greaterThan(0.0));
        assertThat(sqlp.getOptimizationCount(), is(1));

        conf  = new BitArraySet<Index>();
        sqlp  = opt.explain(sql, conf);
        cost2 = sqlp.getSelectCost();
        
        assertThat(sqlp, notNullValue());
        assertThat(sqlp.getStatement().getSQLCategory().isSame(SQLCategory.SELECT), is(true));
        assertThat(sqlp.getSelectCost(), greaterThan(0.0));
        assertThat(sqlp.getOptimizationCount(), is(1));

        assertThat(cost1, is(cost2));

        sql  = new SQLStatement("UPDATE one_table.tbl set a = 3 where a = 5");
        sqlp = opt.explain(sql);

        assertThat(sqlp, notNullValue());
        assertThat(sqlp.getStatement().getSQLCategory().isSame(SQLCategory.UPDATE), is(true));
        assertThat(sqlp.getSelectCost(), is(greaterThan(0.0)));
        assertThat(sqlp.getUpdateCost(), is(greaterThan(0.0)));
        assertThat(sqlp.getOptimizationCount(), is(1));

        sqlp = opt.explain(sql, conf);

        assertThat(sqlp, notNullValue());
        assertThat(sqlp.getStatement().getSQLCategory().isSame(SQLCategory.UPDATE), is(true));
        assertThat(sqlp.getSelectCost(), is(greaterThan(0.0)));
        assertThat(sqlp.getUpdateCost(), is(greaterThan(0.0)));
        assertThat(sqlp.getOptimizationCount(), is(1));
    }

    /**
     * Checks that the given optimizer can execute basic what-if optimizations.
     *
     * @param cat
     *      catalog used to retrieve metadata
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkWhatIfExplain(Catalog cat, Optimizer opt) throws Exception
    {
        SQLStatement sql;
        ExplainedSQLStatement sqlp;
        Column col;
        Set<Index> conf;
        Index idxa;
        Index idxb;
        double cost1;
        double cost2;

        sql   = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        cost1 = opt.explain(sql).getSelectCost();

        idxa = new Index(cat.<Column>findByName("one_table.tbl.a"));
        conf = new BitArraySet<Index>();

        conf.add(idxa);

        sqlp  = opt.explain(sql, conf);
        cost2 = sqlp.getSelectCost();

        assertThat(sqlp, notNullValue());
        assertThat(sqlp.getStatement().getSQLCategory().isSame(SQLCategory.SELECT), is(true));
        assertThat(sqlp.getSelectCost(), greaterThan(0.0));
        assertThat(sqlp.getOptimizationCount(), is(1));
        assertThat(cost1, is(not(cost2)));

        col  = cat.<Column>findByName("one_table.tbl.b");
        idxb = new Index(col, SECONDARY, UNCLUSTERED, NON_UNIQUE);

        conf.add(idxb);

        sql  = new SQLStatement("UPDATE one_table.tbl set a = 3 where a = 5");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getSelectCost(), is(cost2));
        assertThat(sqlp.getStatement().getSQLCategory().isSame(SQLCategory.UPDATE), is(true));
        assertThat(sqlp.getTotalCost(), is(greaterThan(sqlp.getSelectCost())));
        assertThat(sqlp.getTotalCost(), is(greaterThan(sqlp.getUpdateCost())));
        assertThat(sqlp.getTotalCost(), is(sqlp.getSelectCost() + sqlp.getUpdateCost()));

        assertThat(sqlp.getUpdateCost(), is(greaterThan(sqlp.getUpdateCost(idxa))));
        assertThat(sqlp.getUpdateCost(), is(greaterThan(sqlp.getUpdateCost(idxb))));

        idxa.getSchema().remove(idxa);
        idxb.getSchema().remove(idxb);
    }

    /**
     * Checks that the given optimizer can execute basic index recommendation operations.
     *
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkRecommendIndexes(Optimizer opt) throws Exception
    {
        SQLStatement sql;
        Set<Index> rec;
        
        sql = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        rec = opt.recommendIndexes(sql);

        assertThat(rec.isEmpty(), is(false));

        sql = new SQLStatement("UPDATE one_table.tbl SET a = -1 WHERE a = 5");
        rec = opt.recommendIndexes(sql);

        assertThat(rec.isEmpty(), is(false));
    }

    /**
     * Checks that for prepared statements generated by the given optimizer, the corresponding set 
     * of used physical structures is correct.
     * @param cat
     *      catalog used to retrieve metadata
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkUsedConfiguration(Catalog cat, Optimizer opt) throws Exception
    {
        SQLStatement sql;
        ExplainedSQLStatement sqlp;
        Column col;
        Set<Index> conf;
        Index idxa;
        Index idxb;
        
        col  = cat.<Column>findByName("one_table.tbl.a");
        idxa = new Index(col, SECONDARY, UNCLUSTERED, NON_UNIQUE);
        conf = new BitArraySet<Index>();
        col  = cat.<Column>findByName("one_table.tbl.b");
        idxb = new Index(col, SECONDARY, UNCLUSTERED, NON_UNIQUE);

        sql  = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getUsedConfiguration().size(), is(0));
        assertThat(sqlp.getUsedConfiguration().contains(idxa), is(false));

        sql  = new SQLStatement("UPDATE one_table.tbl set a = 3 where a > 0");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getUpdatedConfiguration().contains(idxa), is(false));
        assertThat(sqlp.getUpdatedConfiguration().contains(idxb), is(false));

        conf.add(idxa);

        sql  = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getUsedConfiguration().size(), is(1));
        assertThat(sqlp.getUsedConfiguration().contains(idxa), is(true));

        sql  = new SQLStatement("UPDATE one_table.tbl set a = 3 where a > 0");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getUsedConfiguration().contains(idxa), is(true));
        //assertThat(sqlp.getUpdatedConfiguration().contains(idxa), is(true));
        //assertThat(sqlp.getUpdatedConfiguration().contains(idxb), is(false));

        conf.add(idxb);

        sql  = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getUsedConfiguration().contains(idxa), is(true));
        assertThat(sqlp.getUsedConfiguration().contains(idxb), is(false));

        assertThat(conf.containsAll(sqlp.getUsedConfiguration()), is(true));
        assertThat(conf.size(), greaterThan(sqlp.getUsedConfiguration().size()));

        sql  = new SQLStatement("UPDATE one_table.tbl set a = 3 where a > 0");
        sqlp = opt.explain(sql, conf);

        assertThat(sqlp.getUsedConfiguration().contains(idxa), is(true));
        //assertThat(sqlp.getUpdatedConfiguration().contains(idxa), is(true));
        //assertThat(sqlp.getUpdatedConfiguration().contains(idxb), is(true));

        idxa.getSchema().remove(idxa);
        idxb.getSchema().remove(idxb);
    }

    // protected static void checkAnalysisTime(Optimizer opt) throws Exception

    /**
     * Checks that the optimizer is well behaved.
     *
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkIsWellBehaved(Optimizer opt) throws Exception
    {
        // this can't be done unless there's a way of getting the set of alternative plans that the 
        // optimizer is using internally after each EXPLAIN. This isn't necessary and it's left here 
        // just to keep it on the record.
    }

    /**
     * Checks that the optimizer respects the monotonicity property. Defined by:
     *
     *    For any index-sets X, Y and query q, if X ⊆ Y then cost(q, X) ≥ cost(q, Y)
     *
     * @param cat
     *      catalog used to retrieve metadata
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkMonotonicity(Catalog cat, Optimizer opt) throws Exception
    {
        SQLStatement sql;
        Column col;
        Set<Index> conf;
        Index idx;
        double cost1;
        double cost2;

        sql  = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        col  = cat.<Column>findByName("one_table.tbl.a");
        idx  = new Index(col, SECONDARY, UNCLUSTERED, NON_UNIQUE);
        conf = new BitArraySet<Index>();

        conf.add(idx);

        cost1 = opt.explain(sql).getSelectCost();
        cost2 = opt.explain(sql, conf).getSelectCost();

        assertThat(cost1, greaterThanOrEqualTo(cost2));

        idx.getSchema().remove(idx);
    }

    /**
     * Checks that the given optimizer complies with the sanity property.
     * <p>
     * For any index-sets X, Y and query q:
     *
     *   if used (q, Y) ⊆ X ⊆ Y, then optplan(q, X) = optplan(q, Y)
     *
     * @param cat
     *      catalog used to retrieve metadata
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkSanity(Catalog cat, Optimizer opt) throws Exception
    {
        SQLStatement sql;
        Column colA;
        Column colB;
        Set<Index> conf;
        ExplainedSQLStatement exp1;
        ExplainedSQLStatement exp2;
        Index idxA;
        Index idxB;

        sql  = new SQLStatement("select a from one_table.tbl where a = 5");
        colA = cat.<Column>findByName("one_table.tbl.a");
        colB = cat.<Column>findByName("one_table.tbl.b");
        idxA = new Index(colA);
        idxB = new Index(colB);
        conf = new BitArraySet<Index>();

        conf.add(idxA);

        exp1 = opt.explain(sql, conf);

        conf.add(idxB);

        exp2 = opt.explain(sql, conf);

        assertThat(exp1.getSelectCost(), is(exp2.getSelectCost()));
        assertThat(exp1.getUpdateCost(), is(exp2.getUpdateCost()));
        assertThat(exp1.getUsedConfiguration(), is(exp2.getUsedConfiguration()));

        // we should also check the contents of the plan (the tree) are the same

        idxA.getSchema().remove(idxA);
        idxB.getSchema().remove(idxB);
    }

    /**
     * Checks that {@link PreparedSQLStatement} objects generated by the optimizer are correct.
     *
     * @param cat
     *      catalog used to retrieve metadata
     * @param opt
     *      optimizer under test
     * @throws Exception
     *      if something wrong occurs
     */
    protected static void checkPreparedExplain(Catalog cat, Optimizer opt) throws Exception
    {
        SQLStatement sql;
        PreparedSQLStatement stmt;
        ExplainedSQLStatement exp1;
        ExplainedSQLStatement exp2;
        Set<Index> conf;
        Column col;
        Index idxa;
        Index idxb;

        // regular explain
        sql = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        stmt = opt.prepareExplain(sql);

        assertThat(stmt, is(notNullValue()));

        exp1 = opt.explain(sql);
        exp2 = stmt.explain(new HashSet<Index>());

        assertThat(exp1, is(notNullValue()));
        assertThat(exp2, is(notNullValue()));
        assertThat(exp2, is(exp2));

        sql  = new SQLStatement("UPDATE one_table.tbl set a = 3 where a = 5");
        stmt = opt.prepareExplain(sql);

        assertThat(stmt, is(notNullValue()));

        exp1 = opt.explain(sql);
        exp2 = stmt.explain(new HashSet<Index>());

        // what-if call
        sql = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5");
        stmt = opt.prepareExplain(sql);

        assertThat(stmt, is(notNullValue()));

        idxa = new Index(cat.<Column>findByName("one_table.tbl.a"));
        conf = new BitArraySet<Index>();

        conf.add(idxa);

        exp1 = opt.explain(sql, conf);
        exp2 = stmt.explain(conf);

        assertThat(exp1, is(notNullValue()));
        assertThat(exp2, is(notNullValue()));
        assertThat(exp2, is(exp2));

        col  = cat.<Column>findByName("one_table.tbl.b");
        idxb = new Index(col, SECONDARY, UNCLUSTERED, NON_UNIQUE);

        conf.add(idxb);

        sql = new SQLStatement("SELECT a FROM one_table.tbl WHERE a = 5 AND b = 2");
        stmt = opt.prepareExplain(sql);

        assertThat(stmt, is(notNullValue()));

        exp1 = opt.explain(sql, conf);
        exp2 = stmt.explain(conf);

        assertThat(exp1, is(notNullValue()));
        assertThat(exp2, is(notNullValue()));
        assertThat(exp2, is(exp2));

        conf.remove(idxa);

        exp1 = opt.explain(sql, conf);
        exp2 = stmt.explain(conf);

        assertThat(exp1, is(notNullValue()));
        assertThat(exp2, is(notNullValue()));
        assertThat(exp2, is(exp2));

        idxa.getSchema().remove(idxa);
        idxb.getSchema().remove(idxb);
    }
}
