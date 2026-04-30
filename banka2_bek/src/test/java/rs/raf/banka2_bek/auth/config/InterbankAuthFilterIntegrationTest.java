package rs.raf.banka2_bek.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InterbankAuthFilterIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void testWithoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/interbank/message"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testWrongApiKey_returns401() throws Exception {
        mockMvc.perform(post("/interbank/message")
                        .header("X-Api-Key", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testValidApiKey_passes() throws Exception {
        var result = mockMvc.perform(post("/interbank/message")
                        .header("X-Api-Key", "test-key-dummy"))
                .andReturn();

        assertNotEquals(401, result.getResponse().getStatus());
    }
}
