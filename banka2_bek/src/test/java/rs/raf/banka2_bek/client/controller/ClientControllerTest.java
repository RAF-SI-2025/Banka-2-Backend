package rs.raf.banka2_bek.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.client.dto.ClientResponseDto;
import rs.raf.banka2_bek.client.dto.CreateClientRequestDto;
import rs.raf.banka2_bek.client.dto.UpdateClientRequestDto;
import rs.raf.banka2_bek.client.service.ClientService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ClientControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ClientService clientService;

    @InjectMocks
    private ClientController clientController;

    private ClientResponseDto testClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders
                .standaloneSetup(clientController)
                .setMessageConverters(converter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testClient = ClientResponseDto.builder()
                .id(1L)
                .firstName("Petar")
                .lastName("Petrovic")
                .email("petar@test.com")
                .phone("+381601234567")
                .address("Beograd")
                .gender("M")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .active(true)
                .createdAt(LocalDateTime.of(2025, 3, 15, 10, 0))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /clients
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /clients - 201 Created")
    void createClient_returnsCreated() throws Exception {
        when(clientService.createClient(any())).thenReturn(testClient);

        String payload = """
                {
                  "firstName": "Petar",
                  "lastName": "Petrovic",
                  "email": "petar@test.com",
                  "phone": "+381601234567",
                  "address": "Beograd",
                  "gender": "M",
                  "password": "Test12345"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Petar"))
                .andExpect(jsonPath("$.lastName").value("Petrovic"))
                .andExpect(jsonPath("$.email").value("petar@test.com"))
                .andExpect(jsonPath("$.active").value(true));

        verify(clientService).createClient(any(CreateClientRequestDto.class));
    }

    @Test
    @DisplayName("POST /clients - 400 when firstName is blank")
    void createClient_missingFirstName_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "lastName": "Petrovic",
                  "email": "petar@test.com"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /clients - 400 when lastName is blank")
    void createClient_missingLastName_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "firstName": "Petar",
                  "email": "petar@test.com"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /clients - 400 when email is blank")
    void createClient_missingEmail_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "firstName": "Petar",
                  "lastName": "Petrovic"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /clients - 400 when email format is invalid")
    void createClient_invalidEmail_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "firstName": "Petar",
                  "lastName": "Petrovic",
                  "email": "not-an-email"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /clients - 400 when duplicate email")
    void createClient_duplicateEmail_returnsBadRequest() throws Exception {
        when(clientService.createClient(any()))
                .thenThrow(new RuntimeException("Klijent sa ovim emailom vec postoji"));

        String payload = """
                {
                  "firstName": "Petar",
                  "lastName": "Petrovic",
                  "email": "existing@test.com"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /clients - 201 without password (auto-generated)")
    void createClient_noPassword_returnsCreated() throws Exception {
        when(clientService.createClient(any())).thenReturn(testClient);

        String payload = """
                {
                  "firstName": "Petar",
                  "lastName": "Petrovic",
                  "email": "petar@test.com"
                }
                """;

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /clients
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /clients - 200 OK paginated list")
    void getClients_returnsPaginatedList() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(List.of(testClient), PageRequest.of(0, 10), 1);
        when(clientService.getClients(0, 10, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/clients")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].firstName").value("Petar"))
                .andExpect(jsonPath("$.content[0].email").value("petar@test.com"));
    }

    @Test
    @DisplayName("GET /clients?firstName=Petar - 200 OK filtered")
    void getClients_filteredByFirstName() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(List.of(testClient), PageRequest.of(0, 10), 1);
        when(clientService.getClients(0, 10, "Petar", null, null, null)).thenReturn(page);

        mockMvc.perform(get("/clients")
                        .param("page", "0")
                        .param("limit", "10")
                        .param("firstName", "Petar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /clients?lastName=Petrovic - 200 OK filtered by last name")
    void getClients_filteredByLastName() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(List.of(testClient), PageRequest.of(0, 10), 1);
        when(clientService.getClients(0, 10, null, "Petrovic", null, null)).thenReturn(page);

        mockMvc.perform(get("/clients")
                        .param("page", "0")
                        .param("limit", "10")
                        .param("lastName", "Petrovic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /clients?email=petar@test.com - 200 OK filtered by email")
    void getClients_filteredByEmail() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(List.of(testClient), PageRequest.of(0, 10), 1);
        when(clientService.getClients(0, 10, null, null, "petar@test.com", null)).thenReturn(page);

        mockMvc.perform(get("/clients")
                        .param("page", "0")
                        .param("limit", "10")
                        .param("email", "petar@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /clients - 200 OK empty page")
    void getClients_emptyPage() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(clientService.getClients(0, 10, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/clients")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("GET /clients - default pagination when no params")
    void getClients_defaultPagination() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(List.of(testClient), PageRequest.of(0, 10), 1);
        when(clientService.getClients(0, 10, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /clients with all filters - 200 OK")
    void getClients_allFilters() throws Exception {
        Page<ClientResponseDto> page = new PageImpl<>(List.of(testClient), PageRequest.of(0, 10), 1);
        when(clientService.getClients(0, 5, "Petar", "Petrovic", "petar@test.com", null)).thenReturn(page);

        mockMvc.perform(get("/clients")
                        .param("page", "0")
                        .param("limit", "5")
                        .param("firstName", "Petar")
                        .param("lastName", "Petrovic")
                        .param("email", "petar@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /clients/{id}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /clients/1 - 200 OK")
    void getClientById_returnsClient() throws Exception {
        when(clientService.getClientById(1L)).thenReturn(testClient);

        mockMvc.perform(get("/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Petar"))
                .andExpect(jsonPath("$.lastName").value("Petrovic"))
                .andExpect(jsonPath("$.email").value("petar@test.com"));

        verify(clientService).getClientById(1L);
    }

    @Test
    @DisplayName("GET /clients/999 - 400 when not found")
    void getClientById_notFound_returnsBadRequest() throws Exception {
        when(clientService.getClientById(999L))
                .thenThrow(new RuntimeException("Klijent sa ID 999 nije pronadjen"));

        mockMvc.perform(get("/clients/999"))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  PUT /clients/{id}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /clients/1 - 200 OK partial update")
    void updateClient_returnsOk() throws Exception {
        ClientResponseDto updated = ClientResponseDto.builder()
                .id(1L).firstName("Petar").lastName("Petrovic")
                .email("petar@test.com").phone("+381609999999")
                .address("Novi Sad").active(true).build();

        when(clientService.updateClient(eq(1L), any(UpdateClientRequestDto.class))).thenReturn(updated);

        String payload = """
                {
                  "phone": "+381609999999",
                  "address": "Novi Sad"
                }
                """;

        mockMvc.perform(put("/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+381609999999"))
                .andExpect(jsonPath("$.address").value("Novi Sad"));

        verify(clientService).updateClient(eq(1L), any(UpdateClientRequestDto.class));
    }

    @Test
    @DisplayName("PUT /clients/1 - 200 OK full update")
    void updateClient_fullUpdate() throws Exception {
        ClientResponseDto updated = ClientResponseDto.builder()
                .id(1L).firstName("Marko").lastName("Markovic")
                .email("petar@test.com").phone("+381609999999")
                .address("Novi Sad").gender("M").active(true).build();

        when(clientService.updateClient(eq(1L), any(UpdateClientRequestDto.class))).thenReturn(updated);

        String payload = """
                {
                  "firstName": "Marko",
                  "lastName": "Markovic",
                  "phone": "+381609999999",
                  "address": "Novi Sad",
                  "gender": "M"
                }
                """;

        mockMvc.perform(put("/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Marko"))
                .andExpect(jsonPath("$.lastName").value("Markovic"));
    }

    @Test
    @DisplayName("PUT /clients/999 - 400 when not found")
    void updateClient_notFound_returnsBadRequest() throws Exception {
        when(clientService.updateClient(eq(999L), any(UpdateClientRequestDto.class)))
                .thenThrow(new RuntimeException("Klijent sa ID 999 nije pronadjen"));

        String payload = """
                {
                  "phone": "+381609999999"
                }
                """;

        mockMvc.perform(put("/clients/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /clients/1 - 200 OK with empty body (no changes)")
    void updateClient_emptyBody() throws Exception {
        when(clientService.updateClient(eq(1L), any(UpdateClientRequestDto.class))).thenReturn(testClient);

        mockMvc.perform(put("/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
