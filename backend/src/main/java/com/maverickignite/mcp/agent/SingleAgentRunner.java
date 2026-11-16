package com.maverickignite.mcp.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;

/** Fast mode: one agent with every attached tool, streaming its answer directly. */
@Component
public class SingleAgentRunner {

    private final AgentSupport support;

    public SingleAgentRunner(AgentSupport support) {
        this.support = support;
    }

    public AgentRun start(AgentRequest req, Runnable finish) {
        TurnRecorder turn = req.turn();
        String system = req.settings().systemPrompt() + "\n\n" + support.prompts().get("presentation") + "\n\n"
                + support.prompts().get("highcharts") + support.context(req);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.addAll(req.history());
        messages.add(new UserMessage(req.question()));

        List<ToolCallback> tools = support.tools(req.servers(), turn.toolListener(null));
        Disposable d = support.client(req, null, null, tools).prompt()
                .messages(messages)
                .toolCallbacks(tools)
                .stream()
                .chatResponse()
                .subscribe(
                        response -> {
                            String text = AgentSupport.textOf(response);
                            if (text != null && !text.isEmpty()) {
                                turn.token(text);
                            }
                        },
                        error -> {
                            turn.fail(AgentSupport.describe(error));
                            finish.run();
                        },
                        finish);
        return d::dispose;
    }
}
