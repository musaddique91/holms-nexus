package com.maverickignite.mcp.agent;

public enum AgentRole {
    PLANNER, RETRIEVER, ANALYST, VISUALIZER, ASSISTANT;

    public String key() {
        return name().toLowerCase();
    }
}
