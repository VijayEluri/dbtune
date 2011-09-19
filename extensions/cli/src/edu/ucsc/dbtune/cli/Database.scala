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
package edu.ucsc.dbtune.cli

import edu.ucsc.dbtune.DatabaseSystem
import edu.ucsc.dbtune.cli.metadata.Configuration
import edu.ucsc.dbtune.cli.metadata.CoreCatalog
import edu.ucsc.dbtune.cli.metadata.Schema
import edu.ucsc.dbtune.optimizer.PreparedSQLStatement;
import edu.ucsc.dbtune.spi.Environment

import java.util.Properties

import edu.ucsc.dbtune.cli.metadata.Schema._
import edu.ucsc.dbtune.DatabaseSystem._
import edu.ucsc.dbtune.spi.EnvironmentProperties.DBMS
import edu.ucsc.dbtune.spi.EnvironmentProperties.JDBC_URL
import edu.ucsc.dbtune.spi.EnvironmentProperties.USERNAME
import edu.ucsc.dbtune.spi.EnvironmentProperties.OPTIMIZER
import edu.ucsc.dbtune.spi.EnvironmentProperties.PASSWORD

/** This class provides a hub for most of the operations that a user can execute through the CLI 
  */
class Database(dbms:DatabaseSystem) extends CoreCatalog(dbms.getCatalog) {
  var schemas:List[Schema] = asScalaSchema(dbms.getCatalog.getSchemas)

  /** Recommends indexes for the given SQL statement
    *
    * @param sql
    *   sql statement
    * @return
    *   a configuration
    */
  def recommend(sql:String) : Configuration =  {
    new Configuration(dbms.getOptimizer.recommendIndexes(sql))
  }
  
  /** Explains a SQL statement
    *
    * @param sql
    *   sql statement
    * @return
    *   a configuration
    */
  def explain(sql:String) : PreparedSQLStatement =  {
    dbms.getOptimizer.explain(sql)
  }
  
  /** Explains a SQL statement
    *
    * @param sql
    *   sql statement
    * @param conf
    *   configuration to be used
    * @return
    *   a configuration
    */
  def explain(sql:String, conf:Configuration) : PreparedSQLStatement =  {
    dbms.getOptimizer.explain(sql, conf)
  }
  
  /** Closes the connection to the DBMS
    */
  def close() =  {
    dbms.getConnection.close
  }
}

object Database
{
  /** Creates to  Database containing the metadata information about a DB.
    *
    * @param url
    *   JDBC url
    * @param usr
    *   username used to authenticate
    * @param pwd
    *   password used to authenticate
    * @return
    *   a databse instance
    */
  def connect(url:String, usr:String, pwd:String) : Database = {

    var properties = new Properties()

    properties.setProperty(USERNAME,  usr)
    properties.setProperty(PASSWORD,  pwd)
    properties.setProperty(JDBC_URL,  url)
    properties.setProperty(OPTIMIZER, DBMS)

    return new Database(newDatabaseSystem(properties))
  }
}