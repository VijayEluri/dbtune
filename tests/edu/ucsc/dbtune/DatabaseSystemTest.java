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
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 *   See the License for the specific language governing permissions and      *
 *   limitations under the License.                                           *
 * ************************************************************************** */
package edu.ucsc.dbtune;

import edu.ucsc.dbtune.metadata.Catalog;
import edu.ucsc.dbtune.metadata.extraction.DB2Extractor;
import edu.ucsc.dbtune.metadata.extraction.MySQLExtractor;
import edu.ucsc.dbtune.metadata.extraction.PGExtractor;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.optimizer.DB2Optimizer;
import edu.ucsc.dbtune.optimizer.IBGOptimizer;
import edu.ucsc.dbtune.optimizer.MySQLOptimizer;
import edu.ucsc.dbtune.optimizer.PGOptimizer;
import edu.ucsc.dbtune.spi.Environment;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static edu.ucsc.dbtune.DbTuneMocks.makeOptimizerMock;
import static edu.ucsc.dbtune.DBTuneInstances.configureDB2;
import static edu.ucsc.dbtune.DBTuneInstances.configureMySQL;
import static edu.ucsc.dbtune.DBTuneInstances.configurePG;
import static edu.ucsc.dbtune.DBTuneInstances.configureDBMSOptimizer;
import static edu.ucsc.dbtune.DBTuneInstances.configureIBGOptimizer;
import static edu.ucsc.dbtune.DBTuneInstances.configureINUMOptimizer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
/**
 * @author Ivo Jimenez
 */
@PrepareForTest({DatabaseSystem.class})
public class DatabaseSystemTest
{
    /**
     * Checks if a system is constructed correctly.
     */
    @Test
    public void testConstructor() throws Exception
    {
        mockStatic(DriverManager.class);

        Connection     con = mock(Connection.class);
        Catalog        cat = mock(Catalog.class);
        Optimizer      opt = makeOptimizerMock();
        DatabaseSystem db  = new DatabaseSystem(con,cat,opt);

        assertThat(db.getConnection(), is(con));
        assertThat(db.getCatalog(), is(cat));
        assertThat(db.getOptimizer(), is(opt));
    }

    /**
     * Checks the static (factory) methods
     */
    @Test
    public void testFactory() throws Exception
    {
        Environment env;

        Connection con = mock(Connection.class);

        mockStatic(DriverManager.class);

        when(DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(con);

        // check DB2
        env = new Environment(configureDBMSOptimizer(configureDB2()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        assertThat(DatabaseSystem.getOptimizer(env,con) instanceof DB2Optimizer, is(true));
        assertThat(DatabaseSystem.getExtractor(env) instanceof DB2Extractor, is(true));

        // check MySQL
        env = new Environment(configureDBMSOptimizer(configureMySQL()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        assertThat(DatabaseSystem.getOptimizer(env,con) instanceof MySQLOptimizer, is(true));
        assertThat(DatabaseSystem.getExtractor(env) instanceof MySQLExtractor, is(true));

        // check PostgreSQL
        env = new Environment(configureDBMSOptimizer(configurePG()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        assertThat(DatabaseSystem.getOptimizer(env,con) instanceof PGOptimizer, is(true));
        assertThat(DatabaseSystem.getExtractor(env) instanceof PGExtractor, is(true));
        
        // check IBG
        env = new Environment(configureIBGOptimizer(configureDB2()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        assertThat(DatabaseSystem.getOptimizer(env,con) instanceof IBGOptimizer, is(true));
        assertThat(DatabaseSystem.getExtractor(env) instanceof DB2Extractor, is(true));

        env = new Environment(configureIBGOptimizer(configureMySQL()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        assertThat(DatabaseSystem.getOptimizer(env,con) instanceof IBGOptimizer, is(true));
        assertThat(DatabaseSystem.getExtractor(env) instanceof MySQLExtractor, is(true));

        env = new Environment(configureIBGOptimizer(configurePG()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        assertThat(DatabaseSystem.getOptimizer(env,con) instanceof IBGOptimizer, is(true));
        assertThat(DatabaseSystem.getExtractor(env) instanceof PGExtractor, is(true));

        // check INUM
        env = new Environment(configureINUMOptimizer(configureDB2()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        try {
            Optimizer opt = DatabaseSystem.getOptimizer(env,con);
            fail("Optimizer " + opt + " shouldn't be returned");
        } catch(SQLException e) {
            // nice;
        }
        assertThat(DatabaseSystem.getExtractor(env) instanceof DB2Extractor, is(true));

        env = new Environment(configureINUMOptimizer(configureMySQL()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        try {
            Optimizer opt = DatabaseSystem.getOptimizer(env,con);
            fail("Optimizer " + opt + " shouldn't be returned");
        } catch(SQLException e) {
            // nice;
        }
        assertThat(DatabaseSystem.getExtractor(env) instanceof MySQLExtractor, is(true));

        env = new Environment(configureINUMOptimizer(configurePG()));

        assertThat(DatabaseSystem.getConnection(env), is(con));
        try {
            Optimizer opt = DatabaseSystem.getOptimizer(env,con);
            fail("Optimizer " + opt + " shouldn't be returned");
        } catch(SQLException e) {
            // nice;
        }
        assertThat(DatabaseSystem.getExtractor(env) instanceof PGExtractor, is(true));
    }
}
