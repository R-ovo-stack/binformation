package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DataAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateUpdateAndDeleteAsset() throws Exception {
        String body = """
                {
                  "name": "测试资产",
                  "code": "ASSET_TEST_CRUD",
                  "dataType": "FILE",
                  "status": "ACTIVE",
                  "remark": "crud test"
                }
                """;

        String created = mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("ASSET_TEST_CRUD")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/assets/" + id.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("测试资产", "测试资产-改")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("测试资产-改")));

        mockMvc.perform(delete("/api/assets/" + id.longValue()))
                .andExpect(status().isNoContent());
    }
}
