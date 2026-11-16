package com.maverickignite.mcp.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The planner's decision for one turn. */
public record Plan(String intent, List<Step> steps, boolean visualize, String chartHint, String presentation,
        boolean fallback) {

    public record Step(String server, String goal) {
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intent", intent);
        List<Map<String, String>> s = new ArrayList<>();
        steps.forEach(step -> s.add(Map.of("server", step.server(), "goal", step.goal())));
        m.put("steps", s);
        m.put("visualize", visualize);
        m.put("chartHint", chartHint == null ? "" : chartHint);
        m.put("presentation", presentation == null ? "text" : presentation);
        m.put("fallback", fallback);
        return m;
    }
}
