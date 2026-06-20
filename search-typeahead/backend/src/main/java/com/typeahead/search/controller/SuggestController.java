package com.typeahead.search.controller;

import com.typeahead.search.repository.QueryRepository;
import com.typeahead.search.repository.QueryRepository.SuggestProjection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class SuggestController {

    private final QueryRepository queryRepository;

    public SuggestController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @GetMapping("/suggest")
    public List<SuggestProjection> suggest(@RequestParam(name = "q", required = false) String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return queryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(prefix);
    }
}
