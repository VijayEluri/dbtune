package edu.ucsc.dbtune.advisor;

import edu.ucsc.dbtune.connectivity.DatabaseConnection;
import edu.ucsc.dbtune.ibg.CandidatePool;
import edu.ucsc.dbtune.ibg.CandidatePool.Snapshot;
import edu.ucsc.dbtune.ibg.IBGAnalyzer;
import edu.ucsc.dbtune.ibg.IBGConstructionException;
import edu.ucsc.dbtune.ibg.IndexBenefitGraphConstructor;
import edu.ucsc.dbtune.ibg.InteractionLogger;
import edu.ucsc.dbtune.ibg.ThreadIBGAnalysis;
import edu.ucsc.dbtune.ibg.ThreadIBGConstruction;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.optimizer.PreparedSQLStatement;
import edu.ucsc.dbtune.optimizer.IBGOptimizer;
import edu.ucsc.dbtune.optimizer.IBGPreparedSQLStatement;
import edu.ucsc.dbtune.spi.core.Console;
import edu.ucsc.dbtune.util.Threads;
import edu.ucsc.dbtune.workload.SQLStatement;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

/**
 * Represents an workload profiler.
 */
public class WorkloadProfilerImpl implements WorkloadProfiler {
    private final DatabaseConnection    connection;
    private final CandidatePool      candidatePool;
    private final ThreadIBGAnalysis     ibgAnalysis;
    private final ThreadIBGConstruction ibgConstruction;
    private final boolean               onlineCandidates;

    // prints to screen details of the profiling process
    private final Console               console  = Console.streaming();
    private final ExecutorService       executor = Threads.explicitThreadPerCpuExecutor("Task: IBG Profiling", console);

    /**
     * Construct a {@code profiler} given a {@link CandidatePool pool of candidate indexes},
     * a {@link DatabaseConnection database connection}, and a flag which indicate whether we
     * are dealing with {@code online} candidates.
     * @param connection
     *      a live {@link DatabaseConnection connection}.
     * @param candidatePool
     *      a {@link CandidatePool pool of candidate indexes}.
     * @param onlineCandidates
     *      {@code true} if we are dealing with online candidates. {@code false} otherwise.
     */
    public WorkloadProfilerImpl(DatabaseConnection connection, CandidatePool candidatePool, boolean onlineCandidates) {
        this(connection, new ThreadIBGAnalysis(), new ThreadIBGConstruction(), candidatePool, onlineCandidates);
    }

    /**
     * Construct a {@code profiler} given a {@link CandidatePool pool of candidate indexes},
     * a {@link DatabaseConnection database connection}, and a flag which indicate whether we
     * are dealing with {@code online} candidates.
     * @param connection
     *      a live {@link DatabaseConnection connection}.
     * @param analysis
     *      an {@link ThreadIBGAnalysis analysis} task.
     * @param construction
     *      a {@link ThreadIBGConstruction construction} task.
     * @param candidatePool
     *      a {@link CandidatePool pool of candidate indexes}.
     * @param onlineCandidates
     *      {@code true} if we are dealing with online candidates. {@code false} otherwise.
     */
    WorkloadProfilerImpl(DatabaseConnection connection,
                         ThreadIBGAnalysis analysis,
                         ThreadIBGConstruction construction,
                         CandidatePool candidatePool,
                         boolean onlineCandidates
    ) {
      this.connection         = connection;
      this.candidatePool      = candidatePool;
      this.onlineCandidates   = onlineCandidates;
      this.ibgAnalysis        = analysis;
      this.ibgConstruction    = construction;
      runProfiler();
    }


    @Override
    public Snapshot addCandidate(Index index) throws SQLException {
        candidatePool.addIndex(index);
        return candidatePool.getSnapshot();
    }


    private void execute(ThreadIBGAnalysis analysis, ThreadIBGConstruction construction){
       executor.execute(analysis);
       executor.execute(construction);
    }

    public void shutdown(){
        executor.shutdown();
    }


    @Override
    public IBGPreparedSQLStatement processQuery(SQLStatement sql){

        if (onlineCandidates) {
            try {
                final CandidateIndexExtractor extractor          = connection.getIndexExtractor();
                final Iterable<Index>         recommendedIndexes = extractor.recommendIndexes(sql);

                for(Index each : recommendedIndexes){
                    candidatePool.addIndex(each);
                }
            } catch (SQLException e) {
                console.error("SQLException caught while recommending indexes", e);
                throw new RuntimeException(e);
            }
        }

        // get the current set of candidates
        Snapshot           snapshot           = candidatePool.getSnapshot();
        IBGOptimizer ibgWhatIfOptimizer = connection.getIBGWhatIfOptimizer();
        PreparedSQLStatement        info;
        try {
            info = ibgWhatIfOptimizer.explain(sql.getSQL(), snapshot);
            console.info("WorkloadProfilerImpl#processQuery(String) returned an PreparedSQLStatement object=" + info) ;
        } catch (SQLException e) {
            console.error("SQLException caught while explaining command", e);
            throw new Error(e);
        }

        // build the IBG
        try {
            InteractionLogger            logger      = new InteractionLogger(snapshot);
            IndexBenefitGraphConstructor ibgCons     = new IndexBenefitGraphConstructor(connection, sql.getSQL(), snapshot);
            IBGAnalyzer                  ibgAnalyzer = new IBGAnalyzer(ibgCons);

//      ibgConstruction.startConstruction(ibgCons);
//
//      final StopWatch watch = new StopWatch();
//      ibgAnalysis.startAnalysis(ibgAnalyzer, logger);
//      double elapasedTime = watch.milliseconds();

      ibgConstruction.startConstruction(ibgCons);
      ibgConstruction.waitUntilDone();
      ibgAnalysis.startAnalysis(ibgAnalyzer, logger);

      long nStart = System.nanoTime();
      ibgAnalysis.waitUntilDone();
      long nStop = System.nanoTime();

      console.info("Analysis: " + ((nStop - nStart) / 1000000000.0));
      console.info("IBG has " + ibgCons.nodeCount() + " nodes");


      // pass the result to the tuner
      return new IBGPreparedSQLStatement(
            info, snapshot, ibgCons.getIBG(),logger.getInteractionBank(),
            ibgWhatIfOptimizer.getWhatIfCount(), (nStop - nStart)/1000000.0);
        } catch (SQLException e) {
            final String msg = "SQLException caught while building ibg";
            console.error(msg, e);
            throw new IBGConstructionException(msg, e);
        }
    }

    @Override
    public Snapshot processVote(Index index, boolean isPositive) throws SQLException {
        return isPositive ? addCandidate(index) : candidatePool.getSnapshot();
    }

    /**
     * run the profiler, which involves two tasks: {@link ThreadIBGAnalysis analysis} and
     * {@link ThreadIBGConstruction construction} tasks.
     */
    public final void runProfiler(){
        execute(ibgAnalysis, ibgConstruction);
    }
}
