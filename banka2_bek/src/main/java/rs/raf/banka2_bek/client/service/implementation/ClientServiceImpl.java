package rs.raf.banka2_bek.client.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.client.dto.ClientResponseDto;
import rs.raf.banka2_bek.client.dto.CreateClientRequestDto;
import rs.raf.banka2_bek.client.dto.UpdateClientRequestDto;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.client.service.ClientService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public ClientResponseDto createClient(CreateClientRequestDto request) {
        if (clientRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Klijent sa ovim emailom vec postoji");
        }

        String password = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword()
                : UUID.randomUUID().toString().substring(0, 12);
        String salt = UUID.randomUUID().toString().substring(0, 16);

        Client client = Client.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .password(passwordEncoder.encode(password + salt))
                .saltPassword(salt)
                .active(true)
                .build();

        client = clientRepository.save(client);

        // Also create a User entry for login
        if (userRepository.findByEmail(request.getEmail()).isEmpty()) {
            User user = new User();
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(password));
            user.setPhone(request.getPhone());
            user.setAddress(request.getAddress());
            user.setActive(true);
            user.setRole("CLIENT");
            userRepository.save(user);
        }

        return toResponse(client);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientResponseDto> getClients(int page, int limit, String firstName, String lastName, String email, String search) {
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by("lastName").ascending());
        if (search != null && !search.isBlank()) {
            return clientRepository.findByUnifiedSearch(search.trim(), pageRequest)
                    .map(this::toResponse);
        }
        return clientRepository.findByFilters(firstName, lastName, email, pageRequest)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponseDto getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Klijent sa ID " + id + " nije pronadjen"));
        return toResponse(client);
    }

    @Override
    @Transactional
    public ClientResponseDto updateClient(Long id, UpdateClientRequestDto request) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Klijent sa ID " + id + " nije pronadjen"));

        if (request.getFirstName() != null) client.setFirstName(request.getFirstName());
        if (request.getLastName() != null) client.setLastName(request.getLastName());
        if (request.getGender() != null) client.setGender(request.getGender());
        if (request.getPhone() != null) client.setPhone(request.getPhone());
        if (request.getAddress() != null) client.setAddress(request.getAddress());
        if (request.getDateOfBirth() != null) client.setDateOfBirth(request.getDateOfBirth());

        client = clientRepository.save(client);

        // Sync with users table
        userRepository.findByEmail(client.getEmail()).ifPresent(user -> {
            if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
            if (request.getLastName() != null) user.setLastName(request.getLastName());
            if (request.getPhone() != null) user.setPhone(request.getPhone());
            if (request.getAddress() != null) user.setAddress(request.getAddress());
            userRepository.save(user);
        });

        return toResponse(client);
    }

    private ClientResponseDto toResponse(Client client) {
        return ClientResponseDto.builder()
                .id(client.getId())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .dateOfBirth(client.getDateOfBirth())
                .gender(client.getGender())
                .email(client.getEmail())
                .phone(client.getPhone())
                .address(client.getAddress())
                .active(client.getActive())
                .createdAt(client.getCreatedAt())
                .build();
    }
}
