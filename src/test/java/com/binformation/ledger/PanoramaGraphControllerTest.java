package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PanoramaGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldBuildPanoramaWithDerivationEdges() throws Exception {
        mockMvc.perform(get("/api/graph/panorama"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.nodes", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.edges", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.edges[?(@.type=='DERIVE')]", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void shouldIncludeEndpointLinksWhenRequested() throws Exception {
        mockMvc.perform(get("/api/graph/panorama").param("includeEndpointLinks", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.type=='ENDPOINT_LINK')]", hasSize(greaterThanOrEqualTo(0))));
    }
}
