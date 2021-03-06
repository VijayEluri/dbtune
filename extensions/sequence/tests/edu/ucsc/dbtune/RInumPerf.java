package edu.ucsc.dbtune;

import java.util.List;
import java.util.Set;

import edu.ucsc.dbtune.advisor.candidategeneration.CandidateGenerator;
import edu.ucsc.dbtune.advisor.candidategeneration.OptimizerCandidateGenerator;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.optimizer.InumPreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.seq.utils.RTimer;
import edu.ucsc.dbtune.util.Environment;
import edu.ucsc.dbtune.util.OptimizerUtils;
import edu.ucsc.dbtune.util.TestUtils;
import edu.ucsc.dbtune.workload.SQLStatement;

/**
 * Temporary code, for debugging purpose only
 * @author wangrui
 *
 */
public class RInumPerf {
    private static DatabaseSystem db;
    private static Environment env;
    private static Optimizer optimizer;
    private static Optimizer delegate;
    private static CandidateGenerator candGen;

    public static void main(String[] args) throws Exception {
        RTimer timer = new RTimer();
        env = Environment.getInstance();
        db = DatabaseSystem.newDatabaseSystem(env);
        optimizer = db.getOptimizer();
        delegate = OptimizerUtils.getBaseOptimizer(optimizer);
        candGen = new OptimizerCandidateGenerator(delegate);
        if (!(optimizer instanceof edu.ucsc.dbtune.optimizer.InumOptimizer))
            throw new Error();
        if (!(delegate instanceof edu.ucsc.dbtune.optimizer.DB2Optimizer))
            throw new Error();
        timer.finish("loading optimizer");
        timer.reset();

        // TestUtils.loadWorkloads(db.getConnection());
        List<SQLStatement> wl = TestUtils.workload(env.getWorkloadsFoldername()
                + "/tpch-cophy");
        // final Set<Index> allRecommendedIndexes = candGen.generate(wl);
        timer.finish("loading");
        int n = 0;
        for (SQLStatement sql : wl) {
//            SQLStatement sql = wl.get(2);
            System.out.println("query");
            long time = System.nanoTime();
            InumPreparedSQLStatement pSql = (InumPreparedSQLStatement) optimizer
                    .prepareExplain(sql);
            long prepareTime = System.nanoTime() - time;
            System.out.format("%fs\n", prepareTime / 1000000000.0);
            n++;
            // if (n > 1)
            // break;
        }

        db.getConnection().close();
    }

}
