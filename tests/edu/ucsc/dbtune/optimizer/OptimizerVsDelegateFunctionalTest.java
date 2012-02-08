package edu.ucsc.dbtune.optimizer;

import java.util.Set;

import edu.ucsc.dbtune.DatabaseSystem;
import edu.ucsc.dbtune.advisor.candidategeneration.CandidateGenerator;
import edu.ucsc.dbtune.advisor.candidategeneration.OptimizerCandidateGenerator;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.util.Environment;
import edu.ucsc.dbtune.workload.SQLStatement;
import edu.ucsc.dbtune.workload.Workload;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static edu.ucsc.dbtune.DatabaseSystem.newDatabaseSystem;
import static edu.ucsc.dbtune.util.TestUtils.loadWorkloads;
import static edu.ucsc.dbtune.util.TestUtils.workloads;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Functional test for testing non-dbms optimizers against the corresponding dbms one. The optimizer 
 * being tested is specified by the {@link edu.ucsc.dbtune.util.EnvironmentProperties#OPTIMIZER} 
 * property. The base optimizer, i.e. the implementation of {@link Optimizer} that runs right on top 
 * of the DBMS (e.g. {@link DB2Optimizer}) is retrieved through the.
 *
 * @author Ivo Jimenez
 */
public class OptimizerVsDelegateFunctionalTest
{
    private static DatabaseSystem db;
    private static Environment env;
    private static Optimizer optimizer;
    private static Optimizer delegate;
    private static CandidateGenerator candGen;

    /**
     * @throws Exception
     *      if {@link #newDatabaseSystem} throws an exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception
    {
        env = Environment.getInstance();
        db = newDatabaseSystem(env);
        optimizer = db.getOptimizer();
        delegate = optimizer.getDelegate();
        candGen = new OptimizerCandidateGenerator(delegate);
        
        loadWorkloads(db.getConnection());
    }

    /**
     * @throws Exception
     *      if something goes wrong while closing the connection to the dbms
     */
    @AfterClass
    public static void afterClass() throws Exception
    {
        db.getConnection().close();
    }

    /**
     * @throws Exception
     *      if something goes wrong
     * @see OptimizerTest#checkPreparedExplain
     */
    @Test
    public void testExplain() throws Exception
    {
        if (delegate == null) return;

        for (Workload wl : workloads(env.getWorkloadFolders()))
            for (SQLStatement sql : wl)
                assertThat(optimizer.explain(sql), is(delegate.explain(sql)));
    }

    /**
     * @throws Exception
     *      if something goes wrong
     * @see OptimizerTest#checkPreparedExplain
     */
    @Test
    public void testWhatIfExplain() throws Exception
    {
        if (delegate == null) return;

        for (Workload wl : workloads(env.getWorkloadFolders())) {

            final Set<Index> conf = candGen.generate(wl);

            for (SQLStatement sql : wl)
                assertThat(optimizer.explain(sql, conf), is(delegate.explain(sql, conf)));
        }
    }

    /**
     * @throws Exception
     *      if something goes wrong
     * @see OptimizerTest#checkPreparedExplain
     */
    @Test
    public void testPreparedSQLStatement() throws Exception
    {
        if (delegate == null) return;

        for (Workload wl : workloads(env.getWorkloadFolders())) {

            final Set<Index> conf = candGen.generate(wl);

            for (SQLStatement sql : wl) {
                final PreparedSQLStatement pSql = optimizer.prepareExplain(sql);
                System.out.println(
                    "INUM: " + pSql.explain(conf).getSelectCost() +
                    " DB2: " + delegate.explain(sql, conf).getSelectCost());
                //assertThat(pSql.explain(conf), is(delegate.explain(sql, conf)));
            }
        }
    }
}
