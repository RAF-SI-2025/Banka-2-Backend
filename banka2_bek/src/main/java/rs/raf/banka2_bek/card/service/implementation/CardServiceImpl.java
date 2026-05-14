package rs.raf.banka2_bek.card.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.model.CardType;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.service.CardService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final MailNotificationService mailNotificationService;

    @Override
    @Transactional
    public CardResponseDto createCard(CreateCardRequestDto request) {
        // SC28/T2-007 fix (14.05.2026): kad zaposleni pravi karticu u ime klijenta,
        // JWT principal je email zaposlenog (NIJE klijent), pa getAuthenticatedClient()
        // ne nadje klijenta i puca "Klijent nije pronadjen". Resenje: za EMPLOYEE/ADMIN
        // role resolve klijenta preko account.getClient(); za CLIENT zadrzi ownership check.
        boolean callerIsEmployee = isCallerEmployeeOrAdmin();
        Client client;
        if (callerIsEmployee) {
            // Auth check (employee mora biti autentifikovan da bismo dosli ovde).
            requireAuthenticated();
        } else {
            client = getAuthenticatedClient();
            Account account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));
            if (account.getClient() == null || !account.getClient().getId().equals(client.getId())) {
                throw new RuntimeException("Nemate pristup ovom racunu");
            }
            checkCardLimit(account, client);
            BigDecimal limit = request.getCardLimit() != null ? request.getCardLimit() : BigDecimal.valueOf(100000);
            CardType cardType = request.getCardType() != null ? request.getCardType() : CardType.VISA;
            return toResponse(createAndSaveCard(account, client, limit, cardType));
        }

        // Employee/Admin path: lookup account, resolve client from account
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));
        client = account.getClient();
        if (client == null) {
            throw new RuntimeException("Racun nema vlasnika (klijenta)");
        }
        checkCardLimit(account, client);
        BigDecimal limit = request.getCardLimit() != null ? request.getCardLimit() : BigDecimal.valueOf(100000);
        CardType cardType = request.getCardType() != null ? request.getCardType() : CardType.VISA;
        return toResponse(createAndSaveCard(account, client, limit, cardType));
    }

    @Override
    @Transactional
    public CardResponseDto createCardForAccount(Long accountId, Long clientId, BigDecimal limit, CardType cardType) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Klijent nije pronadjen"));

        checkCardLimit(account, client);

        BigDecimal cardLimit = limit != null ? limit : BigDecimal.valueOf(100000);
        CardType type = cardType != null ? cardType : CardType.VISA;
        return toResponse(createAndSaveCard(account, client, cardLimit, type));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponseDto> getMyCards() {
        Client client = getOptionalClient();
        if (client == null) return Collections.emptyList();
        return cardRepository.findByClientId(client.getId()).stream()
                .map(this::toMaskedResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponseDto> getCardsByAccount(Long accountId) {
        return cardRepository.findByAccountId(accountId).stream()
                .map(this::toMaskedResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CardResponseDto blockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        // Samo vlasnik kartice moze da je blokira
        Client client = getOptionalClient();
        if (client != null && (card.getClient() == null || !card.getClient().getId().equals(client.getId()))) {
            throw new RuntimeException("Nemate pristup ovoj kartici");
        }

        if (card.getStatus() == CardStatus.DEACTIVATED) {
            throw new RuntimeException("Deaktivirana kartica se ne moze blokirati. Deaktivirane kartice ne mogu biti ponovo aktivirane.");
        }
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new RuntimeException("Kartica je vec blokirana");
        }
        card.setStatus(CardStatus.BLOCKED);
        CardResponseDto response = toMaskedResponse(cardRepository.save(card));

        try {
            String last4 = card.getCardNumber().substring(card.getCardNumber().length() - 4);
            mailNotificationService.sendCardBlockedMail(
                    card.getClient().getEmail(), last4, LocalDate.now());
        } catch (Exception e) {
            log.warn("Failed to send card notification email", e);
        }

        return response;
    }

    @Override
    @Transactional
    public CardResponseDto unblockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new RuntimeException("Samo blokirana kartica se moze deblokirati");
        }
        card.setStatus(CardStatus.ACTIVE);
        CardResponseDto response = toMaskedResponse(cardRepository.save(card));

        try {
            String last4 = card.getCardNumber().substring(card.getCardNumber().length() - 4);
            mailNotificationService.sendCardUnblockedMail(
                    card.getClient().getEmail(), last4);
        } catch (Exception e) {
            log.warn("Failed to send card notification email", e);
        }

        return response;
    }

    @Override
    @Transactional
    public CardResponseDto deactivateCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        if (card.getStatus() == CardStatus.DEACTIVATED) {
            throw new RuntimeException("Kartica je vec deaktivirana. Deaktivirane kartice ne mogu biti ponovo aktivirane.");
        }

        card.setStatus(CardStatus.DEACTIVATED);
        return toMaskedResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponseDto updateCardLimit(Long cardId, BigDecimal newLimit) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        if (card.getStatus() == CardStatus.DEACTIVATED) {
            throw new RuntimeException("Ne moze se menjati limit deaktivirane kartice");
        }
        card.setCardLimit(newLimit);
        return toMaskedResponse(cardRepository.save(card));
    }

    // --- helpers ---

    private void checkCardLimit(Account account, Client client) {
        boolean isBusiness = account.getAccountType() == AccountType.BUSINESS;

        if (isBusiness) {
            // Poslovni racun: max 1 kartica po ovlascenom licu (klijentu)
            long activeCardsForPerson = cardRepository.countByAccountIdAndClientIdAndStatusNot(
                    account.getId(), client.getId(), CardStatus.DEACTIVATED);
            if (activeCardsForPerson >= 1) {
                throw new RuntimeException("Ovlasceno lice vec ima karticu za ovaj poslovni racun (max 1 po osobi)");
            }
        } else {
            // Licni racun: max 2 kartice ukupno
            long activeCards = cardRepository.countByAccountIdAndStatusNot(account.getId(), CardStatus.DEACTIVATED);
            if (activeCards >= 2) {
                throw new RuntimeException("Dostignut maksimalan broj kartica za ovaj racun (2)");
            }
        }
    }

    /**
     * P2.3 — alocira slot 1 ili 2 za novu karticu. Slot je deo
     * partial UNIQUE INDEX-a (account_id, client_id, card_slot) gde
     * status != 'DEACTIVATED' — DB-level enforcement preventiva za
     * race-condition (dva paralelna createCard mogu da prodju kroz
     * service-level checkCardLimit ali DB ce odbiti drugi insert).
     *
     * @return 1 ili 2 (uvek 1 za poslovne, 1 ili 2 za licne)
     * @throws RuntimeException ako su svi slotovi zauzeti
     */
    private int allocateSlot(Account account, Client client) {
        boolean isBusiness = account.getAccountType() == AccountType.BUSINESS;
        // Aktivne kartice ZA TAJ par (account, client) — slotovi su per-par-ovi.
        List<Card> existing = cardRepository.findByAccountIdAndClientIdAndStatusNot(
                account.getId(), client.getId(), CardStatus.DEACTIVATED);
        java.util.Set<Integer> taken = existing.stream()
                .map(Card::getCardSlot)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (isBusiness) {
            // Poslovni: slot uvek 1 (max 1 kartica po osobi)
            if (taken.contains(1)) {
                throw new RuntimeException("Ovlasceno lice vec ima karticu za ovaj poslovni racun (slot 1 zauzet)");
            }
            return 1;
        }

        // Licni: 1 ili 2
        if (!taken.contains(1)) return 1;
        if (!taken.contains(2)) return 2;
        throw new RuntimeException("Dostignut maksimalan broj kartica za ovaj racun (2)");
    }

    private Card createAndSaveCard(Account account, Client client, BigDecimal limit, CardType cardType) {
        String cardNumber;
        do {
            cardNumber = Card.generateCardNumber(cardType);
        } while (cardRepository.findByCardNumber(cardNumber).isPresent());

        int slot = allocateSlot(account, client);

        Card card = Card.builder()
                .cardNumber(cardNumber)
                .cardName(resolveCardName(cardType))
                .cardType(cardType)
                .cvv(Card.generateCvv())
                .account(account)
                .client(client)
                .cardLimit(limit)
                .cardSlot(slot)
                .status(CardStatus.ACTIVE)
                .createdAt(LocalDate.now())
                .expirationDate(LocalDate.now().plusYears(4))
                .build();

        return cardRepository.save(card);
    }

    private String resolveCardName(CardType cardType) {
        return switch (cardType) {
            case VISA -> "Visa Debit";
            case MASTERCARD -> "MasterCard Debit";
            case DINACARD -> "DinaCard Debit";
            case AMERICAN_EXPRESS -> "American Express";
        };
    }

    private CardResponseDto toResponse(Card card) {
        return CardResponseDto.builder()
                .id(card.getId())
                .cardNumber(card.getCardNumber())
                .cardName(card.getCardName())
                .cvv(card.getCvv())
                .cardType(card.getCardType())
                .accountId(card.getAccount().getId())
                .accountNumber(card.getAccount().getAccountNumber())
                .ownerName(card.getClient().getFirstName() + " " + card.getClient().getLastName())
                .cardLimit(card.getCardLimit())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .expirationDate(card.getExpirationDate())
                .build();
    }

    private CardResponseDto toMaskedResponse(Card card) {
        return CardResponseDto.builder()
                .id(card.getId())
                .cardNumber(maskCardNumber(card.getCardNumber()))
                .cardName(card.getCardName())
                .cvv(null)
                .cardType(card.getCardType())
                .accountId(card.getAccount().getId())
                .accountNumber(card.getAccount().getAccountNumber())
                .ownerName(card.getClient().getFirstName() + " " + card.getClient().getLastName())
                .cardLimit(card.getCardLimit())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .expirationDate(card.getExpirationDate())
                .build();
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) return cardNumber;
        String digits = cardNumber.replaceAll("\\s+", "");
        String first4 = digits.substring(0, 4);
        String last4 = digits.substring(digits.length() - 4);
        return first4 + " **** **** " + last4;
    }

    private Client getAuthenticatedClient() {
        Client client = getOptionalClient();
        if (client == null) throw new RuntimeException("Klijent nije pronadjen");
        return client;
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

    private void requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("Klijent nije pronadjen");
        }
    }

    private boolean isCallerEmployeeOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return "ROLE_ADMIN".equals(role) || "ROLE_EMPLOYEE".equals(role)
                    || "ADMIN".equals(role) || "EMPLOYEE".equals(role)
                    || "SUPERVISOR".equals(role);
        });
    }
}
