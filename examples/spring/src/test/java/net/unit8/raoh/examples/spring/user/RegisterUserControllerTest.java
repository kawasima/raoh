package net.unit8.raoh.examples.spring.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerUser() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Alice@Example.com",
                                  "age": 24,
                                  "address": {
                                    "city": "Tokyo",
                                    "postalCode": "123-4567"
                                  },
                                  "tags": ["alpha", "beta"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.address.city").value("Tokyo"));
    }

    @Test
    void rejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bad",
                                  "age": 17,
                                  "role": "admin",
                                  "address": {
                                    "city": "",
                                    "postalCode": "1234"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[?(@.path == '/email')]").exists())
                .andExpect(jsonPath("$.issues[?(@.path == '/address/city')]").exists())
                .andExpect(jsonPath("$.issues[?(@.path == '/address/postalCode')]").exists());
                // Note: /role cross-field validation (admin must be >= 20) is not reported here
                // because flatMap only runs when all preceding field decoders succeed.
    }
}
