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
class ImpactAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAnalyzeEndpointDeleteImpact() throws Exception {
        mockMvc.perform(get("/api/impact")
                        .param("entityType", "ENDPOINT")
                        .param("entityId", "21")
                        .param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType", is("ENDPOINT")))
                .andExpect(jsonPath("$.entityId", is(21)))
                .andExpect(jsonPath("$.action", is("DELETE")))
                .andExpect(jsonPath("$.blockers").isArray());
    }

    @Test
    void shouldAnalyzeEndpointUpdateReferences() throws Exception {
        mockMvc.perform(get("/api/impact")
                        .param("entityType", "ENDPOINT")
                        .param("entityId", "21")
                        .param("action", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canProceed", is(true)))
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void shouldAnalyzeFlowDeleteWarnings() throws Exception {
        mockMvc.perform(get("/api/impact")
                        .param("entityType", "FLOW")
                        .param("entityId", "1")
                        .param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canProceed", is(true)))
                .andExpect(jsonPath("$.warnings.length()", greaterThan(0)));
    }

    @Test
    void shouldAnalyzeAssetDeleteImpact() throws Exception {
        mockMvc.perform(get("/api/impact")
                        .param("entityType", "ASSET")
                        .param("entityId", "1")
                        .param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType", is("ASSET")))
                .andExpect(jsonPath("$.entityId", is(1)))
                .andExpect(jsonPath("$.blockers").isArray());
    }
}
