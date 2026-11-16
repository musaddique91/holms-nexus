package com.maverickignite.mcp.llm;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.maverickignite.mcp.llm.LlmDtos.ProviderRequest;
import com.maverickignite.mcp.llm.LlmDtos.ProviderView;
import com.maverickignite.mcp.llm.LlmDtos.TestResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/providers")
public class LlmController {

    private final LlmProviderService service;

    public LlmController(LlmProviderService service) {
        this.service = service;
    }

    @GetMapping
    List<ProviderView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProviderView create(@Valid @RequestBody ProviderRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    ProviderView update(@PathVariable UUID id, @Valid @RequestBody ProviderRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/models/refresh")
    ProviderView refreshModels(@PathVariable UUID id) {
        return service.refreshModels(id);
    }

    @PostMapping("/{id}/test")
    TestResult test(@PathVariable UUID id) {
        return service.test(id);
    }
}
