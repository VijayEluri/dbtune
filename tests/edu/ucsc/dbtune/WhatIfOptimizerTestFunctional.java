package edu.ucsc.dbtune;

import edu.ucsc.dbtune.advisor.CandidateIndexExtractor;
import edu.ucsc.dbtune.connectivity.ConnectionManager;
import edu.ucsc.dbtune.connectivity.DatabaseConnection;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.SQLCategory;
import edu.ucsc.dbtune.optimizer.PreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.IBGOptimizer;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.spi.Environment;
import edu.ucsc.dbtune.util.SQLScriptExecuter;
import edu.ucsc.dbtune.workload.SQLStatement;

import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Condition;
import org.junit.If;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

import static edu.ucsc.dbtune.connectivity.JdbcConnectionManager.makeDatabaseConnectionManager;
import static edu.ucsc.dbtune.util.Instances.newBitSet;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

/**
 * Functional test for what-if optimizer implementations
 *
 * The test should check basically two properties:
 *   * monotonicity
 *   * sanity
 *
 * For more information on what these properties mean, refer to page 57 (Chapter 4, Section 2.1,
 * Property 4.1 and 4.2 respectively).
 *
 * @author huascar.sanchez@gmail.com (Huascar A. Sanchez)
 * @see {@code thesis} <a
 * href="http://proquest.umi.com/pqdlink?did=2171968721&Fmt=7&clientId=1565&RQT=309&VName=PQD">
 *     "On-line Index Selection for Physical Database Tuning"</a>
 */
public class WhatIfOptimizerTestFunctional {
    private static DatabaseConnection connection;
    private static Environment environment;

    @BeforeClass
    public static void setUp() throws Exception {
        environment = Environment.getInstance();

        final Properties        connProps   = environment.getAll();
        final ConnectionManager manager     = makeDatabaseConnectionManager(connProps);

        try {connection = manager.connect();} catch (Exception e) {connection = null;}

        File   outputdir   = new File(environment.getOutputFoldername() + "/one_table");
        String ddlfilename = environment.getScriptAtWorkloadsFolder("one_table/create.sql");

        outputdir.mkdirs();
        //SQLScriptExecuter.execute(connection.getJdbcConnection(), ddlfilename);
        connection.getJdbcConnection().setAutoCommit(false);
    }

    @Test // this test will pass once the what if optimizer returns something....
    @If(condition = "isDatabaseConnectionAvailable", is = true)
    public void testSingleSQLWhatIfOptimization() throws Exception {
        final SQLStatement            query      = new SQLStatement(SQLCategory.QUERY, "select a from tbl where a = 5;");
        final CandidateIndexExtractor extractor  = connection.getIndexExtractor();
        final Iterable<Index>         candidates = extractor.recommendIndexes(query);
        final Optimizer               optimizer  = connection.getOptimizer();

        assertThat(candidates, CoreMatchers.<Object>notNullValue());

        System.out.println("Getting cost with candidates " + candidates);

        final PreparedSQLStatement info = optimizer.explain(query.getSQL(), candidates);

        assertThat(info, CoreMatchers.<Object>notNullValue());
        assertThat(info.getStatement().getSQLCategory().isSame(SQLCategory.QUERY), is(true));
        for(Index each : candidates){
           assumeThat(info.getIndexMaintenanceCost(each) >= 0.0, is(true));
        }
    }


    @Test
    @If(condition = "isDatabaseConnectionAvailable", is = true)
    public void testSingleSQLIBGWhatIfOptimization() throws Exception {
        final SQLStatement            query      = new SQLStatement(SQLCategory.QUERY, "select count(*) from tbl where b > 3");
        final CandidateIndexExtractor extractor  = connection.getIndexExtractor();
        final Iterable<Index>         candidates = extractor.recommendIndexes(query);
        final IBGOptimizer      optimizer  = connection.getIBGWhatIfOptimizer();

        assertThat(candidates, CoreMatchers.<Object>notNullValue());

        double cost = optimizer.estimateCost(query.getSQL(), newBitSet(), newBitSet());

        assumeThat(cost >= 0, is(true));
    }

    @AfterClass
    public static void tearDown() throws Exception{
        if(connection != null) connection.close();
        connection  = null;
        environment.getAll().clear();
        environment = null;
    }

    @Condition
    public static boolean isDatabaseConnectionAvailable(){
        final boolean isNotNull = connection != null;
        boolean isOpened = false;
        if(isNotNull){
            isOpened = connection.isOpened();
        }
        return isNotNull && isOpened;
    }
}
