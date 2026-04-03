package rs.raf.banka2_bek.client.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.client.dto.ClientResponseDto;
import rs.raf.banka2_bek.client.dto.CreateClientRequestDto;
import rs.raf.banka2_bek.client.dto.UpdateClientRequestDto;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.client.service.implementation.ClientServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private ClientServiceImpl clientService;

    @Test
    @DisplayName("createClient - uspesno kreira klijenta i user zapis")
    void createClientSuccess() {
        var dto = new CreateClientRequestDto();
        dto.setFirstName("Petar");
        dto.setLastName("Petrovic");
        dto.setEmail("petar@test.com");
        dto.setPassword("Test12345");
        dto.setPhone("+381601234567");
        dto.setDateOfBirth(LocalDate.of(1990, 1, 15));
        dto.setGender("M");
        dto.setAddress("Beograd");

        when(clientRepository.findByEmail("petar@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(userRepository.findByEmail("petar@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponseDto result = clientService.createClient(dto);

        assertNotNull(result);
        assertEquals("Petar", result.getFirstName());
        assertEquals("Petrovic", result.getLastName());
        assertEquals("petar@test.com", result.getEmail());
        verify(clientRepository).save(any(Client.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createClient - duplikat emaila baca RuntimeException")
    void createClientDuplicateEmail() {
        var dto = new CreateClientRequestDto();
        dto.setEmail("existing@test.com");

        when(clientRepository.findByEmail("existing@test.com"))
                .thenReturn(Optional.of(Client.builder().id(1L).build()));

        assertThrows(RuntimeException.class, () -> clientService.createClient(dto));
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("createClient - bez password-a generise random")
    void createClientNoPassword() {
        var dto = new CreateClientRequestDto();
        dto.setFirstName("Ana");
        dto.setLastName("Anic");
        dto.setEmail("ana@test.com");
        // password je null

        when(clientRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponseDto result = clientService.createClient(dto);
        assertNotNull(result);
        verify(passwordEncoder, times(2)).encode(any());
    }

    @Test
    @DisplayName("getClients - vraca paginiranu listu sa filterima")
    void getClientsWithFilters() {
        Client c1 = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").active(true).build();
        Client c2 = Client.builder().id(2L).firstName("Milica").lastName("Nikolic")
                .email("milica@test.com").active(true).build();

        Page<Client> page = new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 10), 2);
        when(clientRepository.findByFilters(eq("Stefan"), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(page);

        Page<ClientResponseDto> result = clientService.getClients(0, 10, "Stefan", null, null);

        assertEquals(2, result.getTotalElements());
        assertEquals("Stefan", result.getContent().get(0).getFirstName());
    }

    @Test
    @DisplayName("getClientById - vraca klijenta")
    void getClientByIdSuccess() {
        Client client = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").active(true).build();

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        ClientResponseDto result = clientService.getClientById(1L);
        assertEquals("Stefan", result.getFirstName());
    }

    @Test
    @DisplayName("getClientById - ne postoji baca RuntimeException")
    void getClientByIdNotFound() {
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> clientService.getClientById(999L));
    }

    @Test
    @DisplayName("updateClient - parcijalno azuriranje")
    void updateClientPartial() {
        Client existing = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").phone("+381601111111").address("Beograd").active(true).build();

        var dto = new UpdateClientRequestDto();
        dto.setPhone("+381609999999");
        dto.setAddress("Novi Sad");

        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = new User();
        user.setEmail("stefan@test.com");
        when(userRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponseDto result = clientService.updateClient(1L, dto);

        assertEquals("+381609999999", result.getPhone());
        assertEquals("Novi Sad", result.getAddress());
        assertEquals("Jovanovic", result.getLastName()); // nije menjano
        verify(userRepository).save(any(User.class)); // sync sa users
    }

    @Test
    @DisplayName("updateClient - ne postoji baca RuntimeException")
    void updateClientNotFound() {
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> clientService.updateClient(999L, new UpdateClientRequestDto()));
    }

    @Test
    @DisplayName("updateClient - menja prezime i sinhronizuje sa User tabelom")
    void updateClientSyncsLastName() {
        Client existing = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").active(true).build();

        var dto = new UpdateClientRequestDto();
        dto.setLastName("Stefanovic");

        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = new User();
        user.setEmail("stefan@test.com");
        user.setLastName("Jovanovic");
        when(userRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        clientService.updateClient(1L, dto);

        verify(userRepository).save(argThat(u -> "Stefanovic".equals(u.getLastName())));
    }

    @Test
    @DisplayName("updateClient - azurira sva polja odjednom")
    void updateClientAllFields() {
        Client existing = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").phone("+381601111111").address("Beograd")
                .gender("M").dateOfBirth(LocalDate.of(1990, 1, 1)).active(true).build();

        var dto = new UpdateClientRequestDto();
        dto.setFirstName("Marko");
        dto.setLastName("Markovic");
        dto.setGender("M");
        dto.setPhone("+381609999999");
        dto.setAddress("Novi Sad");
        dto.setDateOfBirth(LocalDate.of(1985, 5, 20));

        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = new User();
        user.setEmail("stefan@test.com");
        when(userRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponseDto result = clientService.updateClient(1L, dto);

        assertEquals("Marko", result.getFirstName());
        assertEquals("Markovic", result.getLastName());
        assertEquals("+381609999999", result.getPhone());
        assertEquals("Novi Sad", result.getAddress());
        assertEquals(LocalDate.of(1985, 5, 20), result.getDateOfBirth());
    }

    @Test
    @DisplayName("updateClient - user ne postoji u users tabeli, ne sinhrounizuje")
    void updateClientUserNotInUsersTable() {
        Client existing = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").active(true).build();

        var dto = new UpdateClientRequestDto();
        dto.setPhone("+381609999999");

        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmail("stefan@test.com")).thenReturn(Optional.empty());

        ClientResponseDto result = clientService.updateClient(1L, dto);
        assertEquals("+381609999999", result.getPhone());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("getClients - vraca paginiranu listu bez filtera")
    void getClientsNoFilters() {
        Client c1 = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").active(true).build();

        Page<Client> page = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(clientRepository.findByFilters(isNull(), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(page);

        Page<ClientResponseDto> result = clientService.getClients(0, 10, null, null, null);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("getClients - vraca praznu stranicu")
    void getClientsEmpty() {
        Page<Client> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(clientRepository.findByFilters(isNull(), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(page);

        Page<ClientResponseDto> result = clientService.getClients(0, 10, null, null, null);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getClients - filtrira po prezimenu i emailu")
    void getClientsFilterByLastNameAndEmail() {
        Client c1 = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").active(true).build();

        Page<Client> page = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(clientRepository.findByFilters(isNull(), eq("Jovanovic"), eq("stefan@test.com"), any(PageRequest.class)))
                .thenReturn(page);

        Page<ClientResponseDto> result = clientService.getClients(0, 10, null, "Jovanovic", "stefan@test.com");
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("createClient - kreira User zapis koji vec postoji u users tabeli - ne kreira duplikat")
    void createClientUserAlreadyExists() {
        var dto = new CreateClientRequestDto();
        dto.setFirstName("Ana");
        dto.setLastName("Anic");
        dto.setEmail("ana@test.com");
        dto.setPassword("Test12345");

        when(clientRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(3L);
            return c;
        });
        // User already exists
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(new User()));

        ClientResponseDto result = clientService.createClient(dto);
        assertNotNull(result);
        // Should not save a new user since one already exists
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("createClient - setuje sva polja ispravno")
    void createClientAllFieldsSet() {
        var dto = new CreateClientRequestDto();
        dto.setFirstName("Jovan");
        dto.setLastName("Jovanovic");
        dto.setEmail("jovan@test.com");
        dto.setPassword("Pass12345");
        dto.setPhone("+381601234567");
        dto.setDateOfBirth(LocalDate.of(1985, 7, 20));
        dto.setGender("M");
        dto.setAddress("Nis");

        when(clientRepository.findByEmail("jovan@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(userRepository.findByEmail("jovan@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponseDto result = clientService.createClient(dto);

        assertEquals("Jovan", result.getFirstName());
        assertEquals("Jovanovic", result.getLastName());
        assertEquals("jovan@test.com", result.getEmail());
        assertEquals("+381601234567", result.getPhone());
        assertEquals("Nis", result.getAddress());
        assertEquals("M", result.getGender());
        assertEquals(LocalDate.of(1985, 7, 20), result.getDateOfBirth());
        assertTrue(result.getActive());
    }

    @Test
    @DisplayName("updateClient - ne menja polja koja su null u dto")
    void updateClientNullFieldsIgnored() {
        Client existing = Client.builder().id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").phone("+381601111111").address("Beograd")
                .gender("M").dateOfBirth(LocalDate.of(1990, 1, 1)).active(true).build();

        // All fields null - nothing should change
        var dto = new UpdateClientRequestDto();

        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmail("stefan@test.com")).thenReturn(Optional.empty());

        ClientResponseDto result = clientService.updateClient(1L, dto);

        assertEquals("Stefan", result.getFirstName());
        assertEquals("Jovanovic", result.getLastName());
        assertEquals("+381601111111", result.getPhone());
        assertEquals("Beograd", result.getAddress());
    }
}
