/* **************************************************************************** *
 *   Copyright 2010 University of California Santa Cruz                         *
 *                                                                              *
 *   Licensed under the Apache License, Version 2.0 (the "License");            *
 *   you may not use this file except in compliance with the License.           *
 *   You may obtain a copy of the License at                                    *
 *                                                                              *
 *       http://www.apache.org/licenses/LICENSE-2.0                             *
 *                                                                              *
 *   Unless required by applicable law or agreed to in writing, software        *
 *   distributed under the License is distributed on an "AS IS" BASIS,          *
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 *   See the License for the specific language governing permissions and        *
 *   limitations under the License.                                             *
 * **************************************************************************** */
/**
 * Used to represent the DBMS metadata. The SQL standard (as well as JDBC) defines the following hierarchy:
 * <p>
 * <ul>
 * <li>{@link edu.ucsc.dbtune.metadata.Catalog}</li>
 * <li>{@link edu.ucsc.dbtune.metadata.Schema}</li>
 * <li>{@link edu.ucsc.dbtune.metadata.Table}</li>
 * <li>{@link edu.ucsc.dbtune.metadata.Column}</li>
 * <li>{@link edu.ucsc.dbtune.metadata.Index}</li>
 * </ul>
 * <p>
 * For more info, refer to the Wiki article
 * <a href="https://github.com/dbgroup-at-ucsc/dbtune/wiki/databasemetadata">"Database Metadata in DBTune"</a>:
 */
@Generated(value={})
package edu.ucsc.dbtune.metadata;

import javax.annotation.Generated;
