package rs.raf.banka2_bek.card.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.model.CardType;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.service.implementation.CardServiceImpl;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock private CardRepository cardRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private MailNotificationService mailNotificationService;

    @InjectMocks
    private CardServiceImpl cardService;

    private Client client;
    private Account personalAccount;
    private Account businessAccount;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").build();

        personalAccount = Account.builder()
                .id(1L).accountNumber("222000112345678911")
                .accountType(AccountType.CHECKING)
                .client(client).build();

        businessAccount = Account.builder()
                .id(2L).accountNumber("222000112345678912")
                .accountType(AccountType.BUSINESS)
                .client(client).build();
    }

    private void mockAuth(String email) {
        UserDetails userDetails = User.builder()
                .username(email).password("pass").authorities("ROLE_CLIENT").build();
        Authentication auth = mock(Authentication.class);
        // SC28/T2-007 fix (14.05.2026): createCard sad prvo proverava isCallerEmployeeOrAdmin()
        // koja cita auth.getAuthorities(). Stubovi getPrincipal/getAuthentication su lenient
        // jer ih neki test putevi (npr. accountNotFound) ne zovu.
        org.mockito.Mockito.lenient().when(auth.getPrincipal()).thenReturn(userDetails);
        java.util.Collection<org.springframework.security.core.GrantedAuthority> auths =
                java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENT"));
        org.mockito.Mockito.lenient().when(((Authentication) auth).getAuthorities()).thenAnswer(inv -> auths);
        SecurityContext ctx = mock(SecurityContext.class);
        org.mockito.Mockito.lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Nested
    @DisplayName("createCard")
    class CreateCard {

        @Test
        @DisplayName("kreira karticu za licni racun")
        void successPersonal() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);
            dto.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(dto);

            assertThat(result).isNotNull();
            assertThat(result.getCardNumber()).hasSize(16);
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
            assertThat(result.getCardLimit()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("baca gresku kad je dostignut max 2 kartice za licni racun")
        void maxCardsPersonal() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(2L);

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maksimalan broj kartica");
        }

        @Test
        @DisplayName("baca gresku kad ovlasceno lice vec ima karticu za poslovni racun")
        void maxCardsBusiness() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(2L)).thenReturn(Optional.of(businessAccount));
            when(cardRepository.countByAccountIdAndClientIdAndStatusNot(2L, 1L, CardStatus.DEACTIVATED)).thenReturn(1L);

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(2L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec ima karticu za ovaj poslovni racun");
        }

        @Test
        @DisplayName("default limit je 100000 kad nije zadat")
        void defaultLimit() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);

            CardResponseDto result = cardService.createCard(dto);
            assertThat(result.getCardLimit()).isEqualByComparingTo("100000");
        }
    }

    @Nested
    @DisplayName("getMyCards")
    class GetMyCards {

        @Test
        @DisplayName("vraca kartice za klijenta")
        void returnsCards() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .cardLimit(BigDecimal.valueOf(250000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByClientId(1L)).thenReturn(List.of(card));

            List<CardResponseDto> result = cardService.getMyCards();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCardNumber()).isEqualTo("4222 **** **** 7890");
            assertThat(result.get(0).getCvv()).isNull();
        }

        @Test
        @DisplayName("vraca praznu listu za non-client (admin)")
        void emptyForNonClient() {
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(null);
            SecurityContextHolder.setContext(ctx);

            List<CardResponseDto> result = cardService.getMyCards();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("blockCard / unblockCard / deactivateCard")
    class CardStatusChanges {

        @Test
        @DisplayName("blokira aktivnu karticu")
        void blockActive() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.blockCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.BLOCKED);
        }

        @Test
        @DisplayName("ne moze da blokira deaktiviranu karticu")
        void cannotBlockDeactivated() {
            Card card = Card.builder().id(1L).status(CardStatus.DEACTIVATED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.blockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ne moze blokirati");
        }

        @Test
        @DisplayName("odblokira blokiranu karticu")
        void unblockBlocked() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.BLOCKED).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.unblockCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }

        @Test
        @DisplayName("ne moze da odblokira aktivnu karticu")
        void cannotUnblockActive() {
            Card card = Card.builder().id(1L).status(CardStatus.ACTIVE).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.unblockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("blokirana");
        }

        @Test
        @DisplayName("deaktivira karticu")
        void deactivateCard() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.deactivateCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.DEACTIVATED);
        }
    }

    @Nested
    @DisplayName("updateCardLimit")
    class UpdateLimit {

        @Test
        @DisplayName("menja limit aktivne kartice")
        void success() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.updateCardLimit(1L, BigDecimal.valueOf(200000));
            assertThat(result.getCardLimit()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("ne moze menjati limit deaktivirane kartice")
        void cannotChangeLimitOfDeactivated() {
            Card card = Card.builder().id(1L).status(CardStatus.DEACTIVATED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.updateCardLimit(1L, BigDecimal.valueOf(50000)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("deaktivirane");
        }

        @Test
        @DisplayName("baca gresku kad kartica ne postoji")
        void notFound() {
            when(cardRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.updateCardLimit(999L, BigDecimal.valueOf(50000)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjena");
        }

        @Test
        @DisplayName("menja limit blokirane kartice (dozvoljen)")
        void canChangeLimitOfBlocked() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.BLOCKED).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.updateCardLimit(1L, BigDecimal.valueOf(300000));
            assertThat(result.getCardLimit()).isEqualByComparingTo("300000");
        }
    }

    @Nested
    @DisplayName("createCardForAccount")
    class CreateCardForAccount {

        @Test
        @DisplayName("kreira karticu za racun sa default limitom i tipom")
        void successWithDefaults() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            CardResponseDto result = cardService.createCardForAccount(1L, 1L, null, null);

            assertThat(result).isNotNull();
            assertThat(result.getCardLimit()).isEqualByComparingTo("100000");
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }

        @Test
        @DisplayName("kreira karticu sa zadatim limitom i MasterCard tipom")
        void successWithCustomLimitAndType() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(2L);
                return c;
            });

            CardResponseDto result = cardService.createCardForAccount(1L, 1L, BigDecimal.valueOf(250000), CardType.MASTERCARD);

            assertThat(result).isNotNull();
            assertThat(result.getCardLimit()).isEqualByComparingTo("250000");
            assertThat(result.getCardName()).isEqualTo("MasterCard Debit");
        }

        @Test
        @DisplayName("baca gresku kad racun ne postoji")
        void accountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.createCardForAccount(999L, 1L, null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Racun nije pronadjen");
        }

        @Test
        @DisplayName("baca gresku kad klijent ne postoji")
        void clientNotFound() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.createCardForAccount(1L, 999L, null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }

        @Test
        @DisplayName("baca gresku kad je dostignut limit kartica")
        void maxCardsReached() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(2L);

            assertThatThrownBy(() -> cardService.createCardForAccount(1L, 1L, null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maksimalan broj kartica");
        }

        @Test
        @DisplayName("kreira DinaCard karticu")
        void dinacardType() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(3L);
                return c;
            });

            CardResponseDto result = cardService.createCardForAccount(1L, 1L, null, CardType.DINACARD);
            assertThat(result.getCardName()).isEqualTo("DinaCard Debit");
        }

        @Test
        @DisplayName("kreira American Express karticu")
        void americanExpressType() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(4L);
                return c;
            });

            CardResponseDto result = cardService.createCardForAccount(1L, 1L, null, CardType.AMERICAN_EXPRESS);
            assertThat(result.getCardName()).isEqualTo("American Express");
        }
    }

    @Nested
    @DisplayName("getCardsByAccount")
    class GetCardsByAccount {

        @Test
        @DisplayName("vraca kartice za racun")
        void returnsCards() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .cardLimit(BigDecimal.valueOf(250000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByAccountId(1L)).thenReturn(List.of(card));

            List<CardResponseDto> result = cardService.getCardsByAccount(1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCvv()).isNull(); // masked response
        }

        @Test
        @DisplayName("vraca praznu listu za racun bez kartica")
        void emptyList() {
            when(cardRepository.findByAccountId(999L)).thenReturn(List.of());
            assertThat(cardService.getCardsByAccount(999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("blockCard / unblockCard / deactivateCard - dodatni slucajevi")
    class AdditionalStatusChanges {

        @Test
        @DisplayName("blockCard - baca gresku kad kartica ne postoji")
        void blockCardNotFound() {
            when(cardRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.blockCard(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjena");
        }

        @Test
        @DisplayName("blockCard - baca gresku kad je kartica vec blokirana")
        void blockAlreadyBlocked() {
            Card card = Card.builder().id(1L).status(CardStatus.BLOCKED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.blockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec blokirana");
        }

        @Test
        @DisplayName("blockCard - proverava vlasnistvo kartice")
        void blockCardOwnershipCheck() {
            mockAuth("stefan@test.com");

            Client otherClient = Client.builder()
                    .id(99L).firstName("Drugi").lastName("Klijent")
                    .email("drugi@test.com").build();

            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(otherClient)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            assertThatThrownBy(() -> cardService.blockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nemate pristup");
        }

        @Test
        @DisplayName("blockCard - salje email notifikaciju")
        void blockCardSendsEmail() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            cardService.blockCard(1L);

            verify(mailNotificationService).sendCardBlockedMail(
                    eq("stefan@test.com"), eq("7890"), any(LocalDate.class));
        }

        @Test
        @DisplayName("blockCard - email failure ne sprecava blokiranje")
        void blockCardEmailFailureIgnored() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("SMTP error")).when(mailNotificationService)
                    .sendCardBlockedMail(anyString(), anyString(), any(LocalDate.class));

            CardResponseDto result = cardService.blockCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.BLOCKED);
        }

        @Test
        @DisplayName("unblockCard - baca gresku kad kartica ne postoji")
        void unblockCardNotFound() {
            when(cardRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.unblockCard(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjena");
        }

        @Test
        @DisplayName("unblockCard - ne moze odblokirati deaktiviranu karticu")
        void cannotUnblockDeactivated() {
            Card card = Card.builder().id(1L).status(CardStatus.DEACTIVATED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.unblockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("blokirana");
        }

        @Test
        @DisplayName("unblockCard - salje email notifikaciju")
        void unblockCardSendsEmail() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.BLOCKED).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            cardService.unblockCard(1L);

            verify(mailNotificationService).sendCardUnblockedMail(
                    eq("stefan@test.com"), eq("7890"));
        }

        @Test
        @DisplayName("unblockCard - email failure ne sprecava deblokiranje")
        void unblockCardEmailFailureIgnored() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.BLOCKED).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("SMTP error")).when(mailNotificationService)
                    .sendCardUnblockedMail(anyString(), anyString());

            CardResponseDto result = cardService.unblockCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }

        @Test
        @DisplayName("deactivateCard - baca gresku kad kartica ne postoji")
        void deactivateCardNotFound() {
            when(cardRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.deactivateCard(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjena");
        }

        @Test
        @DisplayName("deactivateCard - baca gresku kad je vec deaktivirana")
        void deactivateAlreadyDeactivated() {
            Card card = Card.builder().id(1L).status(CardStatus.DEACTIVATED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.deactivateCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec deaktivirana");
        }

        @Test
        @DisplayName("deactivateCard - moze deaktivirati blokiranu karticu")
        void deactivateBlockedCard() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.BLOCKED).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.deactivateCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.DEACTIVATED);
        }
    }

    @Nested
    @DisplayName("createCard - dodatni slucajevi")
    class CreateCardAdditional {

        @Test
        @DisplayName("baca gresku kad racun ne postoji")
        void accountNotFound() {
            mockAuth("stefan@test.com");
            // clientRepository.findByEmail stub je lenient — accountNotFound test
            // baca gresku PRE getAuthenticatedClient() poziva (sad se accountRepository.findById
            // poziva prvi posle 14.05 SC28 fix-a).
            org.mockito.Mockito.lenient().when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(999L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Racun nije pronadjen");
        }

        @Test
        @DisplayName("baca gresku kad klijent nije vlasnik racuna")
        void notAccountOwner() {
            Client otherClient = Client.builder()
                    .id(99L).firstName("Drugi").lastName("K")
                    .email("drugi@test.com").build();

            Account otherAccount = Account.builder()
                    .id(3L).accountNumber("222000112345678913")
                    .accountType(AccountType.CHECKING)
                    .client(otherClient).build();

            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(3L)).thenReturn(Optional.of(otherAccount));

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(3L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nemate pristup");
        }

        @Test
        @DisplayName("baca gresku kad klijent nije autentifikovan")
        void notAuthenticated() {
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(null);
            SecurityContextHolder.setContext(ctx);

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }

        @Test
        @DisplayName("kreira karticu sa MasterCard tipom")
        void createMastercard() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);
            dto.setCardType(CardType.MASTERCARD);

            CardResponseDto result = cardService.createCard(dto);
            assertThat(result.getCardName()).isEqualTo("MasterCard Debit");
        }

        @Test
        @DisplayName("kreira drugu karticu za licni racun (1 od 2)")
        void createSecondCardForPersonal() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(1L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(2L);
                return c;
            });

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);

            CardResponseDto result = cardService.createCard(dto);
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }

        @Test
        @DisplayName("racun bez klijenta - baca gresku o pristupu")
        void accountWithoutClient() {
            Account noClientAccount = Account.builder()
                    .id(5L).accountNumber("222000112345678915")
                    .accountType(AccountType.BUSINESS)
                    .client(null).build();

            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(5L)).thenReturn(Optional.of(noClientAccount));

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(5L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nemate pristup");
        }
    }

    @Nested
    @DisplayName("maskCardNumber i getMyCards")
    class MaskCardNumber {

        @Test
        @DisplayName("getMyCards maskira broj kartice i cvv")
        void masksCardData() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .cardLimit(BigDecimal.valueOf(250000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByClientId(1L)).thenReturn(List.of(card));

            List<CardResponseDto> result = cardService.getMyCards();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCardNumber()).startsWith("4222");
            assertThat(result.get(0).getCardNumber()).endsWith("7890");
            assertThat(result.get(0).getCardNumber()).contains("****");
            assertThat(result.get(0).getCvv()).isNull();
        }

        @Test
        @DisplayName("getCardsByAccount maskira kartice za vise kartica")
        void masksMultipleCards() {
            Card card1 = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .cardLimit(BigDecimal.valueOf(100000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            Card card2 = Card.builder()
                    .id(2L).cardNumber("5333009876543210").cardName("MasterCard Debit")
                    .cvv("456").account(personalAccount).client(client)
                    .cardLimit(BigDecimal.valueOf(200000)).status(CardStatus.BLOCKED)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByAccountId(1L)).thenReturn(List.of(card1, card2));

            List<CardResponseDto> result = cardService.getCardsByAccount(1L);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCvv()).isNull();
            assertThat(result.get(1).getCvv()).isNull();
            assertThat(result.get(0).getCardNumber()).contains("****");
            assertThat(result.get(1).getCardNumber()).contains("****");
        }
    }

    @Nested
    @DisplayName("CardType generation paths")
    class CardTypeGeneration {

        @Test
        @DisplayName("createCard resolves VISA card name")
        void visaCardName() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.VISA);
            req.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result.getCardName()).isEqualTo("Visa Debit");
            assertThat(result.getCardType()).isEqualTo(CardType.VISA);
        }

        @Test
        @DisplayName("createCard resolves MASTERCARD card name")
        void mastercardCardName() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(11L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.MASTERCARD);
            req.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result.getCardName()).isEqualTo("MasterCard Debit");
        }

        @Test
        @DisplayName("createCard resolves DINACARD card name")
        void dinacardCardName() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(12L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.DINACARD);
            req.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result.getCardName()).isEqualTo("DinaCard Debit");
        }

        @Test
        @DisplayName("createCard resolves AMERICAN_EXPRESS card name")
        void amexCardName() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(13L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.AMERICAN_EXPRESS);
            req.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result.getCardName()).isEqualTo("American Express");
        }

        @Test
        @DisplayName("createCard with null cardType defaults to VISA")
        void nullCardTypeDefaultsToVisa() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(14L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(null);
            req.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result.getCardType()).isEqualTo(CardType.VISA);
        }

        @Test
        @DisplayName("createCard with null cardLimit defaults to 100000")
        void nullCardLimitDefaults() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(15L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.VISA);
            req.setCardLimit(null);

            CardResponseDto result = cardService.createCard(req);
            assertThat(result.getCardLimit()).isEqualByComparingTo("100000");
        }
    }

    @Nested
    @DisplayName("Card limit enforcement for account types")
    class CardLimitEnforcement {

        @Test
        @DisplayName("business account allows 1 card per person - succeeds when 0 active")
        void businessAllowsOneCard() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(2L)).thenReturn(Optional.of(businessAccount));
            when(cardRepository.countByAccountIdAndClientIdAndStatusNot(2L, 1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(20L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(2L);
            req.setCardType(CardType.VISA);
            req.setCardLimit(BigDecimal.valueOf(100000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("business account rejects second card for same person")
        void businessRejectsSecondCard() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(2L)).thenReturn(Optional.of(businessAccount));
            when(cardRepository.countByAccountIdAndClientIdAndStatusNot(2L, 1L, CardStatus.DEACTIVATED)).thenReturn(1L);

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(2L);
            req.setCardType(CardType.VISA);

            assertThatThrownBy(() -> cardService.createCard(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec ima karticu");
        }

        @Test
        @DisplayName("personal account allows up to 2 cards")
        void personalAllowsTwoCards() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(1L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(21L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.MASTERCARD);
            req.setCardLimit(BigDecimal.valueOf(150000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("personal account rejects third card")
        void personalRejectsThirdCard() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(2L);

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.VISA);

            assertThatThrownBy(() -> cardService.createCard(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maksimalan broj");
        }
    }

    @Nested
    @DisplayName("createCardForAccount - all CardType and default branches")
    class CreateCardForAccountAllTypes {

        @Test
        @DisplayName("createCardForAccount with null limit and null type defaults")
        void nullLimitAndType() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(30L);
                return c;
            });

            CardResponseDto result = cardService.createCardForAccount(1L, 1L, null, null);
            assertThat(result.getCardType()).isEqualTo(CardType.VISA);
            assertThat(result.getCardLimit()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("createCardForAccount with explicit limit and DINACARD type")
        void explicitLimitAndDinacard() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(31L);
                return c;
            });

            CardResponseDto result = cardService.createCardForAccount(1L, 1L, BigDecimal.valueOf(75000), CardType.DINACARD);
            assertThat(result.getCardType()).isEqualTo(CardType.DINACARD);
            assertThat(result.getCardLimit()).isEqualByComparingTo("75000");
        }
    }

    @Nested
    @DisplayName("getOptionalClient - principal type branches")
    class GetOptionalClientBranches {

        @Test
        @DisplayName("String principal resolves to email")
        void stringPrincipal() {
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn("stefan@test.com");
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            List<CardResponseDto> result = cardService.getMyCards();
            // Just verify it doesn't throw - string principal is handled
            verify(clientRepository).findByEmail("stefan@test.com");
        }

        @Test
        @DisplayName("null authentication returns empty list for getMyCards")
        void nullAuth() {
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(null);
            SecurityContextHolder.setContext(ctx);

            List<CardResponseDto> result = cardService.getMyCards();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getAuthenticatedClient throws when no client found")
        void noClientThrows() {
            mockAuth("ghost@test.com");
            when(clientRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.createCard(new CreateCardRequestDto()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }
    }

    @Nested
    @DisplayName("maskCardNumber edge cases")
    class MaskCardNumberEdgeCases {

        @Test
        @DisplayName("null card number returns null")
        void nullCardNumber() {
            Card card = Card.builder()
                    .id(1L).cardNumber(null).cardName("Test")
                    .cvv("123").account(personalAccount).client(client)
                    .cardType(CardType.VISA)
                    .cardLimit(BigDecimal.valueOf(100000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByAccountId(1L)).thenReturn(List.of(card));

            List<CardResponseDto> result = cardService.getCardsByAccount(1L);
            assertThat(result.get(0).getCardNumber()).isNull();
        }

        @Test
        @DisplayName("short card number returned as-is")
        void shortCardNumber() {
            Card card = Card.builder()
                    .id(1L).cardNumber("1234567").cardName("Test")
                    .cvv("123").account(personalAccount).client(client)
                    .cardType(CardType.VISA)
                    .cardLimit(BigDecimal.valueOf(100000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByAccountId(1L)).thenReturn(List.of(card));

            List<CardResponseDto> result = cardService.getCardsByAccount(1L);
            assertThat(result.get(0).getCardNumber()).isEqualTo("1234567");
        }
    }

    @Nested
    @DisplayName("Card number generation retry")
    class CardNumberRetry {

        @Test
        @DisplayName("retries when card number already exists")
        void retriesOnDuplicate() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            // First call returns existing, second returns empty
            when(cardRepository.findByCardNumber(anyString()))
                    .thenReturn(Optional.of(Card.builder().build()))
                    .thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(40L);
                return c;
            });

            CreateCardRequestDto req = new CreateCardRequestDto();
            req.setAccountId(1L);
            req.setCardType(CardType.VISA);
            req.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(req);
            assertThat(result).isNotNull();
            verify(cardRepository, times(2)).findByCardNumber(anyString());
        }
    }
}
