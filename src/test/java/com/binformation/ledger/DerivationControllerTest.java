package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
class DerivationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateUpdateAndDeleteDerivation() throws Exception {
        String createBody = """
                {
                  "name": "test-crud-derivation",
                  "executorId": 203,
                  "hostId": 255,
                  "status": "ACTIVE",
                  "remark": "crud test",
                  "inputs": [
                    { "inputAssetId": 202, "sortOrder": 0 },
                    { "inputAssetId": 203, "sortOrder": 1 }
                  ]
                }
                """;

        String created = mockMvc.perform(post("/api/assets/204/derivations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outputAssetId", is(204)))
                .andExpect(jsonPath("$.inputs", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/assets/204/derivations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id.longValue() + ")]", hasSize(1)));

        mockMvc.perform(put("/api/derivations/" + id.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody.replace("test-crud-derivation", "test-crud-derivation-upd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("test-crud-derivation-upd")));

        mockMvc.perform(delete("/api/derivations/" + id.longValue()))
                .andExpect(status().isNoContent());
    }
}
