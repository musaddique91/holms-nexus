package com.maverickignite.mcp.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.maverickignite.mcp.chat.ChatDtos.ConversationDetail;
import com.maverickignite.mcp.chat.ChatDtos.ConversationSummary;
import com.maverickignite.mcp.chat.ChatDtos.CreateConversation;
import com.maverickignite.mcp.chat.ChatDtos.SendMessage;
import com.maverickignite.mcp.chat.ChatDtos.UpdateConversation;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/conversations")
public class McpChatController {

    private final ChatService service;

    public McpChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping
    List<ConversationSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID workspaceId) {
        return service.list(q, workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationSummary create(@RequestBody CreateConversation req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    ConversationDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    ConversationSummary update(@PathVariable UUID id, @RequestBody UpdateConversation req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping(path = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable UUID id, @Valid @RequestBody SendMessage req) {
        return service.stream(id, req);
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void stop(@PathVariable UUID id) {
        service.stop(id);
    }
}
