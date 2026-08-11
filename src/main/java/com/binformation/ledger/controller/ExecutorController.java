package com.binformation.ledger.controller;

import com.binformation.ledger.dto.flow.ExecutorOptionDto;
import com.binformation.ledger.service.ExecutorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<ExecutorOptionDto> listExecutors() {
        return executorService.listOptions();
    }
}
