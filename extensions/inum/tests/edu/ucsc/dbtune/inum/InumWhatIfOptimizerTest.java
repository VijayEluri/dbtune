package edu.ucsc.dbtune.inum;

import edu.ucsc.dbtune.core.InumWhatIfOptimizer;
import edu.ucsc.dbtune.core.InumWhatIfOptimizerImpl;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * Tests the {@link InumWhatIfOptimizer} implementation.
 *
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class InumWhatIfOptimizerTest {
  @Test public void testQueryCostEstimation() throws Exception {
    final InumWhatIfOptimizer optimizer = configureWhatIfOptimizer();
    double cost = optimizer.estimateCost("SELECT * FROM PERSONS;");
    assertThat(Double.compare(7.0, cost), equalTo(0));
  }

  private static InumWhatIfOptimizer configureWhatIfOptimizer() throws Exception {
    final Inum                inum      = InumTest.configureInum();
    final InumWhatIfOptimizer optimizer = new InumWhatIfOptimizerImpl(inum);
    return optimizer;
  }
}
