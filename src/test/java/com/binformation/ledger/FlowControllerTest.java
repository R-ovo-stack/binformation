package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FlowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListFlowsForAsset() throws Exception {
        mockMvc.perform(get("/api/assets/1/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void shouldCreateUpdateAndDeleteFlow() throws Exception {
        String createBody = """
                {
                  "sourceEndpointId": 50,
                  "targetEndpointId": 51,
                  "purpose": "SHARE",
                  "primary": true,
                  "status": "ACTIVE",
                  "remark": "test flow",
                  "paths": [
                    {
                      "name": "默认路径",
                      "enabled": true,
                      "sortOrder": 0,
                      "steps": [
                        {
                          "seq": 1,
                          "hostId": 40,
                          "executorId": 1,
                          "method": "DIRECT_PUSH",
                          "remark": "step1"
                        }
                      ]
                    }
                  ]
                }
                """;

        String created = mockMvc.perform(post("/api/assets/1/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetId", is(1)))
                .andExpect(jsonPath("$.paths", hasSize(1)))
                .andExpect(jsonPath("$.paths[0].steps", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number flowIdNum = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        Long flowId = flowIdNum.longValue();

        mockMvc.perform(get("/api/flows/" + flowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark", is("test flow")));

        String updateBody = createBody.replace("test flow", "updated flow");
        mockMvc.perform(put("/api/flows/" + flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark", is("updated flow")));

        mockMvc.perform(delete("/api/flows/" + flowId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/flows/" + flowId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListReferenceData() throws Exception {
        mockMvc.perform(get("/api/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        mockMvc.perform(get("/api/executors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }
}
