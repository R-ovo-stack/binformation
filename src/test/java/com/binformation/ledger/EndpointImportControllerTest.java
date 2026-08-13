package com.binformation.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldDownloadImportTemplate() throws Exception {
        mockMvc.perform(get("/api/endpoints/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }

    @Test
    void shouldImportEndpointsFromCsv() throws Exception {
        String csv = """
                type,name,parentPath,code,status,owner,remark,attrs
                SECURITY_ZONE,导入测试区,,,ACTIVE,,,
                SYSTEM,导入测试系统,导入测试区,,ACTIVE,,,
                HOST,导入测试主机,导入测试区 / 导入测试系统,,ACTIVE,,,
                """;

        mockMvc.perform(multipart("/api/endpoints/import")
                        .file(new MockMultipartFile(
                                "file", "endpoints.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(3)))
                .andExpect(jsonPath("$.created", is(3)))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void shouldReportDuplicatePathAsSkipped() throws Exception {
        String csv = """
                type,name,parentPath,code,status,owner,remark,attrs
                SECURITY_ZONE,安全区A,,,ACTIVE,,,
                """;

        mockMvc.perform(multipart("/api/endpoints/import")
                        .file(new MockMultipartFile(
                                "file", "dup.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(0)))
                .andExpect(jsonPath("$.skipped", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("已跳过")));
    }
}
