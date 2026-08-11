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
class ExecutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateUpdateAndDeleteExecutor() throws Exception {
        String createBody = """
                {
                  "name": "test-crud-exec",
                  "code": "test-crud-exec",
                  "kind": "PROGRAM",
                  "defaultHostId": 253,
                  "status": "ACTIVE",
                  "remark": "crud test"
                }
                """;

        String created = mockMvc.perform(post("/api/executors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind", is("PROGRAM")))
                .andExpect(jsonPath("$.defaultHostId", is(253)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/executors/" + id.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody.replace("test-crud-exec", "test-crud-exec-upd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("test-crud-exec-upd")));

        mockMvc.perform(delete("/api/executors/" + id.longValue()))
                .andExpect(status().isNoContent());
    }
}
