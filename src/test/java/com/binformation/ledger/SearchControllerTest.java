package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnEmptyForBlankQuery() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(jsonPath("$.groups.length()", is(0)));
    }

    @Test
    void shouldSearchAssetsByName() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "kdc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThan(0)))
                .andExpect(jsonPath("$.groups[0].entityType").exists());
    }

    @Test
    void shouldSearchEndpointById() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThan(0)));
    }
}
