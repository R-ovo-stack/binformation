package com.binformation.ledger.controller;

import com.binformation.ledger.dto.flow.EndpointOptionDto;
import com.binformation.ledger.service.EndpointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @GetMapping
    public List<EndpointOptionDto> listEndpoints(@RequestParam(required = false) String type) {
        return endpointService.listOptions(type);
    }
}
