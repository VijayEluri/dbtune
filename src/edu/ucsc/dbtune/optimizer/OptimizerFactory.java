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
package edu.ucsc.dbtune.optimizer;

import edu.ucsc.dbtune.connectivity.DatabaseConnection;

/**
 * Creates optimizers
 */
public interface OptimizerFactory
{
    /**
     * creates a new {@link Optimizer} object.
     *
     * @param connection
     *      the {@link DatabaseConnection} that gets this {@code optimizer} assigned to.
     * @return
     *      a generic optimizer
     */
    Optimizer newOptimizer(DatabaseConnection connection);

    /**
     * makes a new {@link IBGOptimizer} object.
     * @param connection
     *      the {@link edu.ucsc.dbtune.connectivity.DatabaseConnection} that gets this {@code optimizer} assigned to.
     * @return
     *      a IBG-specific what-if optimizer.
     */
    IBGOptimizer newIBGWhatIfOptimizer(DatabaseConnection connection);
}
