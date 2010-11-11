/*
 * ****************************************************************************
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
 *  ****************************************************************************
 */
package edu.ucsc.satuning.db;

import edu.ucsc.satuning.util.PreConditions;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author huascar.sanchez@gmail.com (Huascar A. Sanchez)
 */
public class DefaultDatabaseConnection<I extends DBIndex<I>> extends AbstractDatabaseConnection<I>
implements DatabaseConnection<I> {

    /**
     * construct a new {@code DefaultDatabaseConnection} object.
     * @param connectionManager
     *      a {@link edu.ucsc.satuning.db.DatabaseConnectionManager} instance.
     * @param sqlConnection
     *      a {@link java.sql.Connection} instance.
     */
    public DefaultDatabaseConnection(
            DatabaseConnectionManager<I> connectionManager,
            Connection sqlConnection
    ){
        super(connectionManager);
        this.sqlConnection.compareAndSet(null, sqlConnection);
        setStatement(makeStatement(sqlConnection));
    }

    private static Statement makeStatement(Connection connection){
        try {
            return connection.createStatement();
        } catch (SQLException e) {
            return null;
        }
    }


    /**
     * @return a recently given
     *      {@link Connection} object.
     */
    public Connection getJdbcConnection(){
        return PreConditions.checkNotNull(sqlConnection.get());
    }


    @Override
    public void init() throws Exception {
        getIndexExtractor().adjust(this);
    }
}
