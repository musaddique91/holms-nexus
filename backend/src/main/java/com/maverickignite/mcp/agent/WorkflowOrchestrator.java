package com.maverickignite.mcp.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;

/**
 * Multi-agent pipeline for one turn:
 *
 * <pre>
 *   Planner ──▶ Retriever × N (parallel, one MCP server each) ──▶ Analyst (streams text)
 *                                                            └──▶ Visualizer (Highcharts, parallel)
 * </pre>
 *
 * Every stage reports live through {@link TurnRecorder}. Nothing here is server-specific — agents only see the
 * tool catalogs that MCP servers advertise, so any integration behaves the same way.
 */
@Component
public class WorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrator.class);

    private static final long RETRIEVER_TIMEOUT_MIN = 6;

    private final PlannerAgent planner;

    private final RetrieverAgent retriever;

    private final AnalystAgent analyst;

    private final VisualizerAgent visualizer;

    private final ExecutorService executor;

    public WorkflowOrchestrator(PlannerAgent planner, RetrieverAgent retriever, AnalystAgent analyst,
            VisualizerAgent visualizer, ExecutorService nexusExecutor) {
        this.planner = planner;
        this.retriever = retriever;
        this.analyst = analyst;
        this.visualizer = visualizer;
        this.executor = nexusExecutor;
    }

    public AgentRun start(AgentRequest req, Runnable finish) {
        Run run = new Run(req, finish);
        run.main = executor.submit(run::execute);
        return run;
    }

    private final class Run implements AgentRun {

        private final AgentRequest req;

        private final Runnable finish;

        private final List<Future<?>> futures = new ArrayList<>();

        private volatile Future<?> main;

        private volatile Disposable analystStream;

        private volatile boolean cancelled;

        Run(AgentRequest req, Runnable finish) {
            this.req = req;
            this.finish = finish;
        }

        void execute() {
            TurnRecorder turn = req.turn();
            try {
                // 1 — plan
                turn.agentStart("planner", AgentRole.PLANNER, "Planner", "Understand the request and plan data retrieval",
                        null, null);
                Plan plan = planner.plan(req);
                turn.plan(plan.toMap());
                turn.agentEnd("planner", "ok", planSummary(plan), null);
                if (cancelled) {
                    return;
                }

                // 2 — retrieve in parallel
                List<RetrieverAgent.Finding> findings = retrieve(plan);
                if (cancelled) {
                    return;
                }
                String findingsText = formatFindings(findings);

                // 3 — analyse (stream) + visualize (parallel)
                boolean visualize = plan.visualize() && req.settings().visualizer();
                Future<Map<String, Object>> chart = visualize
                        ? executor.submit(() -> visualizer.run(req, "visualizer", plan, findingsText))
                        : null;
                CountDownLatch analystDone = new CountDownLatch(1);
                analystStream = analyst.stream(req, "analyst", plan, findingsText, visualize, analystDone::countDown);
                analystDone.await();
                if (chart != null && !cancelled) {
                    Map<String, Object> artifact = chart.get(4, TimeUnit.MINUTES);
                    if (artifact != null) {
                        turn.artifact(artifact);
                    }
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            catch (Exception ex) {
                if (!cancelled) {
                    log.warn("Workflow failed", ex);
                    turn.fail(AgentSupport.describe(ex));
                }
            }
            finally {
                finish.run();
            }
        }

        private List<RetrieverAgent.Finding> retrieve(Plan plan) throws InterruptedException {
            Semaphore slots = new Semaphore(Math.max(1, req.settings().maxParallelAgents()));
            List<Future<RetrieverAgent.Finding>> pending = new ArrayList<>();
            int i = 0;
            for (Plan.Step step : plan.steps()) {
                AgentRequest.ServerHandle server = req.servers().stream()
                        .filter(h -> h.server().getName().equals(step.server())).findFirst().orElse(null);
                if (server == null) {
                    continue;
                }
                String agentId = "retriever-" + (++i);
                Future<RetrieverAgent.Finding> f = executor.submit(() -> {
                    slots.acquire();
                    try {
                        return retriever.run(req, agentId, step, server);
                    }
                    finally {
                        slots.release();
                    }
                });
                pending.add(f);
                futures.add(f);
            }
            List<RetrieverAgent.Finding> out = new ArrayList<>();
            for (Future<RetrieverAgent.Finding> f : pending) {
                try {
                    out.add(f.get(RETRIEVER_TIMEOUT_MIN, TimeUnit.MINUTES));
                }
                catch (InterruptedException ex) {
                    throw ex;
                }
                catch (Exception ex) {
                    f.cancel(true);
                }
            }
            return out;
        }

        @Override
        public void cancel() {
            cancelled = true;
            Disposable d = analystStream;
            if (d != null) {
                d.dispose();
            }
            futures.forEach(f -> f.cancel(true));
            if (main != null) {
                main.cancel(true);
            }
        }
    }

    private static String planSummary(Plan plan) {
        String steps = plan.steps().isEmpty() ? "no data retrieval needed"
                : plan.steps().size() + " retrieval step" + (plan.steps().size() > 1 ? "s" : "");
        return (plan.fallback() ? "Fallback plan · " : "") + steps + (plan.visualize() ? " · chart" : "");
    }

    private static String formatFindings(List<RetrieverAgent.Finding> findings) {
        if (findings.isEmpty()) {
            return "(No data was retrieved for this turn. Answer from the conversation and general knowledge, and "
                    + "say so if real data would be needed.)";
        }
        StringBuilder sb = new StringBuilder();
        for (RetrieverAgent.Finding f : findings) {
            sb.append("### From ").append(f.server()).append(f.ok() ? "" : " (FAILED)").append('\n')
                    .append("Goal: ").append(f.goal()).append("\n\n")
                    .append(TurnRecorder.truncate(f.report(), 20_000)).append("\n\n");
        }
        return sb.toString();
    }
}
