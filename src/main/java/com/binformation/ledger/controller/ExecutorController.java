package com.binformation.ledger.controller;

import com.binformation.ledger.dto.executor.ExecutorDetailDto;
import com.binformation.ledger.dto.executor.ExecutorSaveRequest;
import com.binformation.ledger.dto.flow.ExecutorOptionDto;
import com.binformation.ledger.service.ExecutorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/executors")
public class ExecutorController {

    private final ExecutorService executorService;

    public ExecutorController(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @GetMapping
    public List<?> listExecutors(@RequestParam(defaultValue = "false") boolean optionsOnly) {
        if (optionsOnly) {
            return executorService.listOptions();
        }
        return executorService.listAll();
    }

    @GetMapping("/{id}")
    public ExecutorDetailDto getExecutor(@PathVariable Long id) {
        return executorService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutorDetailDto createExecutor(@Valid @RequestBody ExecutorSaveRequest request) {
        return executorService.create(request);
    }

    @PutMapping("/{id}")
    public ExecutorDetailDto updateExecutor(
            @PathVariable Long id,
            @Valid @RequestBody ExecutorSaveRequest request) {
        return executorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExecutor(@PathVariable Long id) {
        executorService.delete(id);
    }
}
