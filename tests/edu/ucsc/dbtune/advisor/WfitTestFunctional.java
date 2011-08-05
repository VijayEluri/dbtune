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
package edu.ucsc.dbtune.advisor;

import edu.ucsc.dbtune.workload.SQLStatement;
import edu.ucsc.dbtune.workload.Workload;
import edu.ucsc.dbtune.core.DBIndex;
import edu.ucsc.dbtune.core.DatabaseConnection;
import edu.ucsc.dbtune.advisor.ProfiledQuery;
import edu.ucsc.dbtune.ibg.CandidatePool;
import edu.ucsc.dbtune.spi.Environment;
import edu.ucsc.dbtune.util.IndexBitSet;
import edu.ucsc.dbtune.util.SQLScriptExecuter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.BeforeClass;
import org.junit.Test;

import static edu.ucsc.dbtune.core.JdbcConnectionManager.makeDatabaseConnectionManager;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Functional test for the WFIT use case.
 *
 * Some of the documentation contained in this class refers to sections of Karl Schnaitter's 
 * Doctoral Thesis "On-line Index Selection for Physical Database Tuning". Specifically to chapter 
 * 6.
 *
 * @see <a href="http://proquest.umi.com/pqdlink?did=2171968721&Fmt=7&clientId=1565&RQT=309&VName=PQD">
 *     "On-line Index Selection for Physical Database Tuning"</a>
 * @author Ivo Jimenez
 */
public class WfitTestFunctional
{
    public final static DatabaseConnection connection;
    public final static Environment        env;

    static {
        try {       
            env        = Environment.getInstance();
            connection = makeDatabaseConnectionManager(env.getAll()).connect();
        } catch(SQLException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @BeforeClass
    public static void setUp() throws Exception
    {
        File   outputdir   = new File(env.getOutputFoldername() + "/one_table");
        String ddlfilename = env.getScriptAtWorkloadsFolder("one_table/create.sql");

        outputdir.mkdirs();
        SQLScriptExecuter.execute(connection.getJdbcConnection(), ddlfilename);
        connection.getJdbcConnection().setAutoCommit(false);
    }

    /**
     * The test has to check first just one query and one index. We need to make sure that the index 
     * gets recommended and dropped consistently to what we expect.
     */
    @Test
    public void testWFIT() throws Exception
    {
        ProfiledQuery<DBIndex> qinfo;
        CandidatePool<DBIndex> pool;
        WFIT wfit;
        Workload workload;
        String   workloadFile;

        FileReader  fileReader;
        IndexBitSet configuration;
        int         maxNumIndexes;
        int         maxNumStates;
        int         whatIfCount;
        int         q;

        workloadFile  = env.getScriptAtWorkloadsFolder("one_table/workload.sql");
        maxNumIndexes = env.getMaxNumIndexes();
        maxNumStates  = env.getMaxNumStates();
        pool          = getCandidates(connection, workloadFile);
        fileReader    = new FileReader(workloadFile);
        workload      = new Workload(fileReader);
        whatIfCount   = 0;
        q             = 0;

        // --------------
        wfit = new WFIT(connection, pool, maxNumStates, maxNumIndexes);

        for (SQLStatement sql : workload) {
            wfit.process(sql);

            assertThat(wfit.getPartitions().subsetCount(), is(1));

            configuration = wfit.getRecommendation();

            qinfo = wfit.getProfiledQuery(q);

            assertThat(qinfo.getCandidateSnapshot().maxInternalId()+1, is(1));

            if(q < 5) {
                assertThat(configuration.cardinality(), is(0));
                assertThat(configuration.isEmpty(), is(true));
                assertThat(qinfo.getWhatIfCount()-whatIfCount, is(4));
            } else if(q == 5) {
                assertThat(configuration.cardinality(), is(1));
                assertThat(configuration.isEmpty(), is(false));
                assertThat(qinfo.getWhatIfCount()-whatIfCount, is(4));
            } else if(q == 6) {
                assertThat(configuration.cardinality(), is(0));
                assertThat(configuration.isEmpty(), is(true));
                assertThat(qinfo.getWhatIfCount()-whatIfCount, is(3));
            } else {
                throw new SQLException("Workload should have 7 statements");
            }

            whatIfCount = qinfo.getWhatIfCount();
            q++;
        }
    }

    private static CandidatePool<DBIndex> getCandidates(DatabaseConnection con, String 
            workloadFilename)
        throws SQLException, IOException
    {
        CandidatePool<DBIndex> pool;
        Iterable<DBIndex>      candidateSet;
        File                   workloadFile;

        pool         = new CandidatePool<DBIndex>();
        workloadFile = new File(workloadFilename);
        candidateSet = con.getIndexExtractor().recommendIndexes(workloadFile);

        for (DBIndex index : candidateSet) {
            pool.addIndex(index);
        }

        return pool;
    }
}