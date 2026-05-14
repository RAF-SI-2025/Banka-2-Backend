package rs.raf.banka2_bek.client.service;

import org.springframework.data.domain.Page;
import rs.raf.banka2_bek.client.dto.ClientResponseDto;
import rs.raf.banka2_bek.client.dto.CreateClientRequestDto;
import rs.raf.banka2_bek.client.dto.UpdateClientRequestDto;

public interface ClientService {
    ClientResponseDto createClient(CreateClientRequestDto request);
    Page<ClientResponseDto> getClients(int page, int limit, String firstName, String lastName, String email, String search);

    /**
     * Backwards-compatible overload (no unified search param). Internally delegates to the 6-arg version with search=null.
     */
    default Page<ClientResponseDto> getClients(int page, int limit, String firstName, String lastName, String email) {
        return getClients(page, limit, firstName, lastName, email, null);
    }
    ClientResponseDto getClientById(Long id);
    ClientResponseDto updateClient(Long id, UpdateClientRequestDto request);
}
