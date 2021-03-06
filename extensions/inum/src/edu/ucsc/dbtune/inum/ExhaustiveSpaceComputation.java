package edu.ucsc.dbtune.inum;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.Table;
import edu.ucsc.dbtune.optimizer.ExplainedSQLStatement;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.optimizer.plan.InumPlan;
import edu.ucsc.dbtune.workload.SQLStatement;

import static com.google.common.collect.Sets.cartesianProduct;
import static com.google.common.collect.Sets.newHashSet;

import static edu.ucsc.dbtune.inum.FullTableScanIndex.getFullTableScanIndexInstance;
import static edu.ucsc.dbtune.optimizer.plan.Operator.NLJ;
import static edu.ucsc.dbtune.util.MetadataUtils.getIndexesPerTable;

/**
 * An implementation of the INUM space computation that populates it exhaustively without any kind 
 * of optimization. This is in contrast to other more sophisticated strategies such as the ones 
 * outlined in [1], like <i>Lazy</i> and <i>Cost-based</i>.
 *
 * @author Ivo Jimenez
 * @author Quoc Trung Tran
 * @see <a href="http://portal.acm.org/citation.cfm?id=1325974"?>
 *          [1] Efficient use of the query optimizer for automated physical design
 *      </a>
 */
public class ExhaustiveSpaceComputation extends AbstractSpaceComputation
{
    /**
     * {@inheritDoc}
     */
    @Override
    public void computeWithCompleteConfiguration(
            Set<InumPlan> space,
            Set<? extends Index> indexes,
            SQLStatement statement,
            Optimizer delegate)
        throws SQLException
    {
        List<Set<Index>> indexesPerTable = new ArrayList<Set<Index>>();

        for (Map.Entry<Table, Set<Index>> e : getIndexesPerTable(indexes).entrySet()) {
            e.getValue().add(getFullTableScanIndexInstance(e.getKey()));
            indexesPerTable.add(e.getValue());
        }

        for (List<Index> atomic : cartesianProduct(indexesPerTable)) {

            ExplainedSQLStatement atomicExplained = delegate.explain(statement, newHashSet(atomic));

            if (!atomicExplained.getPlan().contains(NLJ))
                space.add(new InumPlan(delegate, atomicExplained));
        }
    }
}
