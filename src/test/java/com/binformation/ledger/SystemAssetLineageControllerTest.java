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
class SystemAssetLineageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListSystems() throws Exception {
        mockMvc.perform(get("/api/lineage/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void shouldListAssetsConsumedByDownstreamSystem() throws Exception {
        mockMvc.perform(get("/api/lineage/systems/13/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId", is(13)))
                .andExpect(jsonPath("$.assetCount", greaterThan(0)))
                .andExpect(jsonPath("$.assets[0].assetId").exists())
                .andExpect(jsonPath("$.assets[0].flows[0].id").exists());
    }

    @Test
    void shouldListDownstreamSystemsForAsset() throws Exception {
        mockMvc.perform(get("/api/lineage/assets/1/downstream-systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId", is(1)))
                .andExpect(jsonPath("$.systemCount", greaterThan(0)))
                .andExpect(jsonPath("$.systems[0].systemName").exists());
    }
}
