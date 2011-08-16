package edu.ucsc.dbtune;

import edu.ucsc.dbtune.connectivity.DatabaseConnection;
import edu.ucsc.dbtune.metadata.Configuration;
import edu.ucsc.dbtune.spi.Environment;
import edu.ucsc.dbtune.util.Iterables;

import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Condition;
import org.junit.If;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static edu.ucsc.dbtune.connectivity.JdbcConnectionManager.makeDatabaseConnectionManager;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Depends on the 'one_table' workload.
 *
 * @author huascar.sanchez@gmail.com (Huascar A. Sanchez)
 * @author ivo@cs.ucsc.edu (Ivo Jimenez)
 */
public class IndexExtractorTestFunctional {
    private static DatabaseConnection connection;
    private static Environment        environment = Environment.getInstance();;

    @BeforeClass
    public static void setUp() throws Exception {
        String ddlfilename;

        connection  = makeDatabaseConnectionManager(environment.getAll()).connect();
        ddlfilename = environment.getScriptAtWorkloadsFolder("one_table/create.sql");
        
        //SQLScriptExecuter.execute(connection.getJdbcConnection(), ddlfilename);
        connection.getJdbcConnection().setAutoCommit(false);
    }


    @Test
    @If(condition = "isDatabaseConnectionAvailable", is = true)
    public void testConnectionIsAlive() throws Exception {
        assertThat(connection.isOpened(), is(true));
        final Connection jdbcConnection = connection.getJdbcConnection();
        final DatabaseMetaData meta = jdbcConnection.getMetaData();
        assertThat(meta, CoreMatchers.<Object>notNullValue());
    }

    @Test
    @If(condition = "isDatabaseConnectionAvailable", is = true)
    public void testSingleSQLRecommendIndexes() throws Exception {
        Configuration candidates = connection.getOptimizer().recommendIndexes("select a from tbl where a = 5;");

        assertThat(candidates, CoreMatchers.<Object>notNullValue());
        assertThat(Iterables.asCollection(candidates).isEmpty(), is(false));

        candidates = connection.getOptimizer().recommendIndexes("update tbl set a=-1 where a = 5;");

        assertThat(candidates, CoreMatchers.<Object>notNullValue());
        assertThat(Iterables.asCollection(candidates).isEmpty(), is(false));
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
