package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateUpdateAndDeleteDirectory() throws Exception {
        String createBody = """
                {
                  "type": "DIRECTORY",
                  "name": "/data/test-crud",
                  "parentId": 40,
                  "attrs": "{\\"dirPath\\":\\"/data/test-crud\\"}",
                  "status": "ACTIVE",
                  "remark": "crud test"
                }
                """;

        String created = mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("DIRECTORY")))
                .andExpect(jsonPath("$.parentId", is(40)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/endpoints/" + id.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody.replace("/data/test-crud", "/data/test-crud-upd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("/data/test-crud-upd")));

        mockMvc.perform(delete("/api/endpoints/" + id.longValue()))
                .andExpect(status().isNoContent());
    }
}
