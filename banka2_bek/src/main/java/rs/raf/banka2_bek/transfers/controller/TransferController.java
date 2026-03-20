package rs.raf.banka2_bek.transfers.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.transfers.dto.*;
import rs.raf.banka2_bek.transfers.service.TransferService;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;

    @PostMapping("/internal")
    public ResponseEntity<TransferResponseDto> internalTransfer(@RequestBody TransferInternalRequestDto request) {
        // Auto-detect: if currencies differ, route to FX transfer
        Account from = accountRepository.findByAccountNumber(request.getFromAccountNumber()).orElse(null);
        Account to = accountRepository.findByAccountNumber(request.getToAccountNumber()).orElse(null);

        if (from != null && to != null && !from.getCurrency().getId().equals(to.getCurrency().getId())) {
            TransferFxRequestDto fxRequest = new TransferFxRequestDto();
            fxRequest.setFromAccountNumber(request.getFromAccountNumber());
            fxRequest.setToAccountNumber(request.getToAccountNumber());
            fxRequest.setAmount(request.getAmount());
            return ResponseEntity.ok(transferService.fxTransfer(fxRequest));
        }

        return ResponseEntity.ok(transferService.internalTransfer(request));
    }

    @PostMapping("/fx")
    public ResponseEntity<TransferResponseDto> fxTransfer(@RequestBody TransferFxRequestDto request) {
        return ResponseEntity.ok(transferService.fxTransfer(request));
    }

    @GetMapping
    public ResponseEntity<List<TransferResponseDto>> getAllTransfers() {
        Client client = getOptionalClient();
        if (client == null) return ResponseEntity.ok(Collections.emptyList());
        return ResponseEntity.ok(transferService.getAllTransfers(client));
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponseDto> getTransferById(@PathVariable Long transferId) {
        return ResponseEntity.ok(transferService.getTransferById(transferId));
    }

    private Client getOptionalClient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        String email;
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }
        return clientRepository.findByEmail(email).orElse(null);
    }
}
