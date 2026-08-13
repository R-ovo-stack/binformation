package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssetGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldBuildGraphForSampleAsset() throws Exception {
        mockMvc.perform(get("/api/assets/1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId", is(1)))
                .andExpect(jsonPath("$.assetCode", is("ASSET_ORDER_FILE")))
                .andExpect(jsonPath("$.nodes", hasSize(org.hamcrest.Matchers.greaterThan(0))))
                .andExpect(jsonPath("$.edges", hasSize(3)))
                .andExpect(jsonPath("$.groups", hasSize(2)));
    }

    @Test
    void shouldIncludeAuxiliaryFlowsWhenRequested() throws Exception {
        mockMvc.perform(get("/api/assets/1/graph").param("includeAuxiliary", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges", hasSize(4)));
    }
}
