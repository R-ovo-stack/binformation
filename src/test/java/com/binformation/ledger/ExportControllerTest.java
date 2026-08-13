package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExportFullLedgerAsJson() throws Exception {
        mockMvc.perform(get("/api/export/full"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".json")))
                .andExpect(jsonPath("$.version", is("1.0")))
                .andExpect(jsonPath("$.endpointCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.assetCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.endpoints", org.hamcrest.Matchers.hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.assets[0].flows", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void shouldExportFullLedgerAsZip() throws Exception {
        mockMvc.perform(get("/api/export/full").param("format", "zip"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".zip")))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("zip")));
    }
}
