package com.binformation.ledger.controller;

import com.binformation.ledger.dto.search.SearchResultDto;
import com.binformation.ledger.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResultDto search(
            @RequestParam String q,
            @RequestParam(required = false) Integer limit) {
        return searchService.search(q, limit);
    }
}
