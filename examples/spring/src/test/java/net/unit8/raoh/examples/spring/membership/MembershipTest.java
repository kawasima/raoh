package net.unit8.raoh.examples.spring.membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class MembershipTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createUser() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Alice", "email": "alice@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email.value").value("alice@example.com"));
    }

    @Test
    void rejectInvalidUser() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "", "email": "bad" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[?(@.path == '/name')]").exists())
                .andExpect(jsonPath("$.issues[?(@.path == '/email')]").exists());
    }

    @Test
    void createGroupAndAddMember() throws Exception {
        // Create a user — capture the generated id
        var userBody = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Bob", "email": "bob-member@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = mapper.readTree(userBody).get("id").get("value").asLong();

        // Create a group — capture the generated id
        var groupBody = mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Engineering", "description": "The eng team" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Engineering"))
                .andReturn().getResponse().getContentAsString();
        long groupId = mapper.readTree(groupBody).get("id").get("value").asLong();

        // Add user to group
        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "userId": %d, "role": "ADMIN" }
                                """.formatted(userId)))
                .andExpect(status().isCreated());

        // Fetch user with groups — exercises Result.map2 + Result.traverse
        mockMvc.perform(get("/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("Bob"))
                .andExpect(jsonPath("$.groups[0].groupName").value("Engineering"))
                .andExpect(jsonPath("$.groups[0].role").value("ADMIN"));
    }

    @Test
    void groupDefaultDescription() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Design-team" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    void addMemberDefaultRole() throws Exception {
        // Create user + group
        var userBody = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Carol", "email": "carol-default@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = mapper.readTree(userBody).get("id").get("value").asLong();

        var groupBody = mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Sales-team" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long groupId = mapper.readTree(groupBody).get("id").get("value").asLong();

        // Add member without explicit role — defaults to MEMBER
        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "userId": %d }
                                """.formatted(userId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].role").value("MEMBER"));
    }

    @Test
    void addMemberToNonExistentUser() throws Exception {
        var groupBody = mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "HR-team" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long groupId = mapper.readTree(groupBody).get("id").get("value").asLong();

        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "userId": 9999 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[?(@.path == '/userId')]").exists());
    }
}
