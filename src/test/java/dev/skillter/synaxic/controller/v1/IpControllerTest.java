package dev.skillter.synaxic.controller.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getIp_ShouldReturnIpAddress() throws Exception {
        mockMvc.perform(get("/v1/ip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip", notNullValue()))
                .andExpect(jsonPath("$.ipVersion", anyOf(is("IPv4"), is("IPv6"))));
    }

    @Test
    void whoAmI_ShouldReturnRequestDetails() throws Exception {
        mockMvc.perform(get("/v1/whoami")
                        .header("User-Agent", "TestAgent/1.0")
                        .header("X-Custom-Header", "TestValue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip", notNullValue()))
                .andExpect(jsonPath("$.ipVersion", notNullValue()))
                .andExpect(jsonPath("$.userAgent", is("TestAgent/1.0")))
                .andExpect(jsonPath("$.method", is("GET")))
                .andExpect(jsonPath("$.headers", hasKey("x-custom-header")));
    }

    @Test
    void echo_ShouldReturnContentMetadata() throws Exception {
        String testContent = "Hello, Synaxic!";

        mockMvc.perform(post("/v1/echo")
                        .contentType("text/plain")
                        .content(testContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(testContent.length())))
                .andExpect(jsonPath("$.sha256", notNullValue()))
                .andExpect(jsonPath("$.contentType", is("text/plain")))
                .andExpect(jsonPath("$.isEmpty", is(false)));
    }

    @Test
    void echo_WithEmptyBody_ShouldReturnEmptyMetadata() throws Exception {
        mockMvc.perform(post("/v1/echo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(0)))
                .andExpect(jsonPath("$.isEmpty", is(true)));
    }
}