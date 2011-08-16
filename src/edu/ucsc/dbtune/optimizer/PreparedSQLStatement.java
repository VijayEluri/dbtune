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
package edu.ucsc.dbtune.optimizer;

import edu.ucsc.dbtune.metadata.Configuration;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.SQLCategory;
import edu.ucsc.dbtune.optimizer.plan.SQLStatementPlan;
import edu.ucsc.dbtune.workload.SQLStatement;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

/**
 * Represents a SQL statement that has been optimized. Each {@code SQLStatement} object is tied to a 
 * {@link Configuration} corresponding to the physical design considered by the optimizer at the 
 * time that the execution cost was estimated.
 * <p>
 * A prepared statement may have the knowledge required to optimize the query it corresponds to, given a physical 
 * configuration.
 * <p>
 * For example:
 * XXX: add an example of how this can be done through the getCost(Configuration) method
 *
 * @see    Optimizer
 * @author Ivo Jimenez
 */
public class PreparedSQLStatement
{
    /** statement corresponding to this prepared statement, i.e. SQL statement that was prepared */
    protected SQLStatement statement;

    /** configuration that was used to optimize the statement */
    protected Iterable<? extends Index> configuration;
    // protected Configuration configuration;

    /** cost assigned by an {@link Optimizer} */
    protected double cost;

    /** the optimized plan */
    protected SQLStatementPlan plan;

    /** a list of indexes that are used by the optimized plan */
    protected List<? extends Index> usedIndexes;

    /** for update statements, the cost that implies on each of the indexes containe in {@link getConfiguration} */
    protected double[] updateCosts;

    /** the optimizer that constructed the statement */
    protected Optimizer optimizer;

    /** number of optimization calls done on the statement */
    protected int optimizationCount;

    /** the time it took an optimizer to produce this statement */
    protected double analysisTime;

    /**
     * Constructs a {@code PreparedSQLStatement} given its corresponding statement and the cost assigned to it.
     *
     * @param statement
     *      the corresponding {@link edu.ucsc.dbtune.workload.SQLStatement} representing the actual SQL statement.
     * @param cost
     *      cost sql statement.
     * @param configuration
     *      configuration used to optimize the statement.
     */
    public PreparedSQLStatement(
            SQLStatementPlan plan,
            double cost,
            Iterable<? extends Index> configuration) {
        this(plan.getStatement(), plan, cost, null, configuration);
    }

    /**
     * construct a new {@code PreparedSQLStatement} object.
     *
     * @param sql
     *      the contents of the statement
     * @param category
     *      sql category.
     * @param cost
     *      execution cost
     */
    public PreparedSQLStatement(
            String sql, 
            SQLCategory category, 
            double cost, 
            Iterable<? extends Index> configuration) {
        this(new SQLStatement(category,sql), null, cost, null, configuration);
    }

    /**
     * construct a new {@code PreparedSQLStatement} for an update statement.
     *
     * @param category
     *      sql category.
     * @param updateCosts
     *      an array of incurred update costs, where each.
     * @param totalCost
     *      total creation cost.
     */
    public PreparedSQLStatement(
            String sql, 
            SQLCategory category,
            double cost,
            double[] updateCosts,
            Iterable<? extends Index> configuration) {
        this(new SQLStatement(category,sql), null, cost, updateCosts, configuration);
    }

    /**
     * construct a new {@code PreparedSQLStatement} for an update statement.
     *
     * @param statement
     *     corresponding the statement
     * @param plan
     *     the statement plan
     * @param cost
     *     the execution cost. For update statements, this cost corresponds only to the SELECT shell, i.e. no update costs 
     *     are considered
     * @param updateCosts
     *     for update statements, an array of incurred update costs, where each element corresponds to an index contained in 
     *     in {@code getConfiguration()}. The array should be indexed using each index id ({@link Index#getId()}).
     * @param configuration
     *     configuration used when the statement was optimized
     */
    public PreparedSQLStatement(
            SQLStatement statement,
            SQLStatementPlan plan,
            double cost,
            double[] updateCosts,
            Iterable<? extends Index> configuration)
    {
        this.statement           = statement;
        this.plan                = plan;
        this.cost                = cost;
        this.updateCosts         = updateCosts;
        this.usedIndexes         = new ArrayList<Index>();
        this.configuration       = configuration;
        this.optimizationCount   = 0; // optimizer.getOptimizationCount();
        this.analysisTime        = 0.0;
    }

    /**
     * copy constructor
     *
     * @param other
     *      object being copied
     */
    public PreparedSQLStatement(PreparedSQLStatement other)
    {
        this.statement           = other.statement;
        this.plan                = other.plan;
        this.cost                = other.cost;
        this.configuration       = other.configuration;
        this.updateCosts         = other.updateCosts;
        this.usedIndexes         = other.usedIndexes;
    }

    /**
     * Re-optimizes this statement, using the current context and the given the set of indexes. 
     * Given a configuration this method tries to determine what would the cost be, as if the 
     * optimizer would have used the information contained in the given statement. Different 
     * implementations will determine how to create a new PreparedSQLStatement in a custom way, 
     * depending on which information is available to them.
     * <p>
     * This method effectively implements (a sort-of 'mini') what-if optimization using the original 
     * context that the optimizer used when it optimized the statement, specifically the structures 
     * contained in {@code getConfiguration()}). For example:
     * <p>
     * XXX: write example
     * <p>
     * This base implementation does a 'naive' what-if optimization by comparing the given 
     * configuration against the one contained in {@code statement}, proceeding in the following 
     * way:
     * <ul>
     * <li> If {@code getUsedConfiguration()} is contained in {@code configuration}, it produces a 
     * new statement identical.
     * <li> If not, then the statement is explained again but using the optimizer, with {@code 
     * configuration} as the new configuration.
     * </ul>
     *
     * @param configuration
     *      the configuration considered to estimate the cost of the new statement. This can (or 
     *      not) be the same as {@link getConfiguration}.
     * @return
     *      a new statement
     * @throws SQLException
     *      if it's not possible to do what-if optimization on the given configuration
     */
    public PreparedSQLStatement explain(Iterable<? extends Index> configuration)
        throws SQLException
    {
        throw new SQLException("Not implemented yet");
    }

    /**
     * Assigns the execution plan.
     *
     * @param plan
     *     the plan
     */
    protected void setPlan(SQLStatementPlan plan)
    {
        this.plan = plan;
    }

    /**
     * Returns the cost of executing the statement. The cost returned is the cost that an optimizer estimated given the set    
     * of physical structures contained in {@code getConfiguration()}. For update statements, this cost doesn't correspond 
     * only to the SELECT shell, i.e. no update costs are considered. In order
     *
     * @return
     *      the execution cost of the statement.
     */
    public double getCost()
    {
        return cost;
    }

    /**
     * Assigns the cost of executing the statement.
     *
     * @param cost
     *      the execution cost of the statement.
     */
    protected void setCost(double cost)
    {
        this.cost = cost;
    }

    /**
     * Sets the configuration that the optimizer considered when it optimized the statement
     *
     * @param configuration
     *     the list of indexes considered at optimization time.
     */
    protected void setConfiguration(Iterable<? extends Index> configuration)
    {
        this.configuration = configuration;
    }

    /**
     * Returns the UPDATE cost that executing the statement implies on the given index. The cost returned is the cost that an 
     * optimizer estimated given the set of physical structures contained in {@code getConfiguration()}. This cost doesn't 
     * include the SELECT shell cost, i.e. the cost of retrieving the tuples that will be affected by the update
     *
     * @param index
     *      a {@link edu.ucsc.dbtune.metadata.Index} object.
     * @return
     *      maintenance cost.
     *      the execution cost of the statement.
     */
    public double getUpdateCost(Index index) throws SQLException
    {
        if(!SQLCategory.DML.isSame(statement.getSQLCategory())) {
            return 0.0;
        }

        if(updateCosts == null) {
            throw new SQLException("No update costs given when instantiating object");
        }

        return updateCosts[index.getId()];
    }

    /**
     * Returns the UPDATE cost that executing the statement implies on the given set of indexes. The cost returned is the 
     * cost that an optimizer estimated given the set of physical structures contained in {@code getConfiguration()}. This 
     * cost doesn't include the SELECT shell cost, i.e. the cost of retrieving the tuples that will be affected by the update
     *
     * @param indexes
     *      list of {@link edu.ucsc.dbtune.metadata.Index} objects.
     * @return
     *      aggregation of update costs of the given configuration.
     */
    public double getUpdateCost(Iterable<? extends Index> indexes) throws SQLException
    {
        double updateCost = 0.0;

        for(Index idx : indexes) {
            updateCost += updateCosts[idx.getId()];
        }

        return updateCost;
    }

    /**
     * Returns the update costs for the statement. If an index in {@code configuration} is contained in {@code 
     * getUpdatedConfiguration()}, then it's cost is aggregated into the returned value.
     *
     * @return
     *     the update costs for the given configuration.
     */
    public double getUpdateCost() throws SQLException
    {
        return getUpdateCost(getConfiguration());
    }

    /**
     * Returns the total cost of this statement. The total is equal to the sum of the statement's plan cost and the 
     * maintenance of each of the updated indexes. For non-update statements this is equal to {@link getCost}
     *
     * @return
     *      the total cost of this query.
     * @see getUpdatedConfiguration
     */
    public double getTotalCost() throws SQLException
    {
        return getCost() + getUpdateCost();
    }

    /**
     * Returns the statement that was used to generate this prepared statement.
     *
     * @return
     *     the SQL statement from which this prepared statement was obtained from.
     */
    public SQLStatement getStatement()
    {
        return statement;
    }

    /**
     * Determines whether a given index is used by the corresponding execution plan.
     *
     * @param
     *     an index
     * @return
     *     {@code true} if used; {@code false} otherwise.
     */
    public boolean isUsed(Index index)
    {
        return usedIndexes.contains(index);
    }

    /**
     * Returns the configuration that the optimizer considered when it optimized the statement. Note that this is different 
     * from {@code getUsedConfiguration()}
     *
     * @return
     *     the list of indexes considered at optimization time.
     */
    public Iterable<? extends Index> getConfiguration()
    {
        return configuration;
    }

    /**
     * Returns the set of indexes that are used by the plan. Note that this is different from {@code getConfiguration()} and, 
     * for update statements, from {@code getUpdatedConfiguration()}.
     *
     * @return
     *     the configuration that is used by the execution plan, i.e. the set of physical structures that are read when 
     *     calculating the answer of a statement.
     */
    public List<Index> getUsedConfiguration()
    {
        List<Index> usedConfiguration = new ArrayList<Index>();

        for(Index idx : getConfiguration()) {
            if( isUsed(idx) ) {
                usedConfiguration.add(idx);
            }
        }

        return usedConfiguration;
    }

    /**
     * For UPDATE statements, this method returns the set of indexes that are updated. Note that this is different from 
     * {@code getConfiguration()} and {@code getUsedConfiguration()}.
     *
     * @return
     *     the configuration that is used by the execution plan, i.e. the set of physical structures that are read when 
     *     calculating the answer of a statement.
     */
    public List<Index> getUpdatedConfiguration()
    {
        List<Index> updatedConfiguration = new ArrayList<Index>();

        for(Index idx : getConfiguration()) {
            if(updateCosts[idx.getId()] != 0) {
                updatedConfiguration.add(idx);
            }
        }

        return updatedConfiguration;
    }

    /**
     * Returns the time that an optimizer took to produce this statement
     *
     * @return
     *     the analysis time.
     */
    public double getAnalysisTime()
    {
        return analysisTime;
    }

    /**
     * Returns the number of optimization calls that have been done on this object. Note that the 
     * what if count includes the number of optimization calls that an optimizer did in order to 
     * generate this statement.
     *
     * @return
     *     the optimization count for this statement.
     */
    public int getOptimizationCount()
    {
        return optimizationCount;
    }
}
