package rs.raf.banka2_bek.assistant.wizard.registry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.wizard.model.SlotDefinition;
import rs.raf.banka2_bek.assistant.wizard.model.SlotType;
import rs.raf.banka2_bek.assistant.wizard.model.WizardTemplate;
import rs.raf.banka2_bek.assistant.wizard.service.SlotResolvers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 4.5 — Registry of all wizard templates for write tools.
 *
 * <p>Each entry maps a tool name to a {@link WizardTemplate} that defines the
 * full multi-step interactive flow for collecting parameters via the
 * agent_choice SSE event protocol.</p>
 *
 * <p>Templates use {@link SlotResolvers} for DB-backed option lists (user's
 * accounts, recipients, listings, cards, ...) and {@code prefill} hooks
 * to auto-fill slots that the user already specified in their natural
 * language request (e.g. "Plati Milici 100" → recipient + amount preskoceni).</p>
 *
 * <p>Wizard execution lives in {@code WizardService}; this class only owns
 * the templates.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WizardRegistry {

    private final SlotResolvers resolvers;
    private final Map<String, WizardTemplate> templates = new HashMap<>();

    public Optional<WizardTemplate> get(String toolName) {
        return Optional.ofNullable(templates.get(toolName));
    }

    public boolean has(String toolName) { return templates.containsKey(toolName); }

    @PostConstruct
    void init() {
        registerCreatePayment();
        registerCreateInternalTransfer();
        registerCreateFxTransfer();
        registerCreateBuyOrder();
        registerCreateSellOrder();
        registerCancelOrder();
        registerBlockCard();
        registerUnblockCard();
        registerInvestInFund();
        registerWithdrawFromFund();
        registerAcceptOtcOffer();
        registerDeclineOtcOffer();
        registerExerciseOtcContract();
        registerAddPaymentRecipient();
        registerChangeAccountName();

        log.info("Arbitro WizardRegistry initialized with {} templates: {}",
                templates.size(), templates.keySet());
    }

    /* ============================== HELPERS ============================== */

    private static SlotDefinition simpleChoice(String name, String prompt,
                                               java.util.function.BiFunction<rs.raf.banka2_bek.auth.util.UserContext,
                                                       Map<String, Object>, List<rs.raf.banka2_bek.assistant.wizard.model.SlotOption>> resolver) {
        return SlotDefinition.builder(name)
                .prompt(prompt)
                .type(SlotType.CHOICE)
                .options(resolver)
                .build();
    }

    private static SlotDefinition numberInput(String name, String prompt) {
        return SlotDefinition.builder(name)
                .prompt(prompt)
                .type(SlotType.NUMBER)
                .transform(s -> {
                    try { return new BigDecimal(s.trim().replace(",", ".")); }
                    catch (NumberFormatException e) { return null; }
                })
                .validate(v -> {
                    if (!(v instanceof BigDecimal bd)) return "Iznos mora biti broj.";
                    if (bd.signum() <= 0) return "Iznos mora biti veci od nule.";
                    return null;
                })
                .build();
    }

    private static SlotDefinition textInput(String name, String prompt, String defaultValue) {
        return SlotDefinition.builder(name)
                .prompt(prompt + (defaultValue != null && !defaultValue.isBlank()
                        ? " (default: " + defaultValue + ")" : ""))
                .type(SlotType.TEXT)
                .transform(s -> {
                    String v = s == null ? null : s.trim();
                    if ((v == null || v.isEmpty()) && defaultValue != null) return defaultValue;
                    return v;
                })
                .build();
    }

    /* ============================== PAYMENTS ============================== */

    private void registerCreatePayment() {
        var slots = List.of(
                simpleChoice("fromAccount", "Sa kog racuna saljemo?",
                        (u, f) -> resolvers.userAccountsWithMinBalance(u, asBd(f.get("amount")))),
                simpleChoice("toAccount", "Kome saljemo?",
                        (u, f) -> resolvers.userRecipients(u)),
                numberInput("amount", "Koji iznos zelite da posaljete?"),
                textInput("description", "Svrha placanja?", "Placanje"),
                simpleChoice("paymentCode", "Sifra placanja",
                        (u, f) -> resolvers.paymentCodeOptions())
        );
        templates.put("create_payment", new WizardTemplate(
                "create_payment", "Novo placanje", slots, List.of("CLIENT"),
                (u, filled) -> {
                    Map<String, Object> args = new HashMap<>(filled);
                    // Resolve recipient name iz toAccount value (account number)
                    return args;
                }
        ));
    }

    private void registerCreateInternalTransfer() {
        var slots = List.of(
                simpleChoice("fromAccountNumber", "Sa kog racuna prebacujemo?",
                        (u, f) -> resolvers.userAccountsWithMinBalance(u, asBd(f.get("amount")))),
                simpleChoice("toAccountNumber", "Na koji racun?",
                        (u, f) -> resolvers.userAccounts(u).stream()
                                .filter(o -> !o.value().equals(f.get("fromAccountNumber")))
                                .toList()),
                numberInput("amount", "Iznos?")
        );
        templates.put("create_transfer_internal", WizardTemplate.simple(
                "create_transfer_internal", "Transfer izmedju svojih racuna",
                slots, List.of("CLIENT")));
    }

    private void registerCreateFxTransfer() {
        var slots = List.of(
                simpleChoice("fromAccountNumber", "Sa kog racuna prebacujemo (devizni)?",
                        (u, f) -> resolvers.userAccountsWithMinBalance(u, asBd(f.get("amount")))),
                simpleChoice("toAccountNumber", "Na koji racun (drugaciju valutu)?",
                        (u, f) -> resolvers.userAccounts(u).stream()
                                .filter(o -> !o.value().equals(f.get("fromAccountNumber")))
                                .toList()),
                numberInput("amount", "Iznos u izvornoj valuti?")
        );
        templates.put("create_transfer_fx", WizardTemplate.simple(
                "create_transfer_fx", "FX konverzija (1% provizija)",
                slots, List.of("CLIENT")));
    }

    private void registerAddPaymentRecipient() {
        var slots = List.of(
                textInput("name", "Ime primaoca?", null),
                textInput("accountNumber", "Broj racuna primaoca (18 cifara)?", null)
        );
        templates.put("add_payment_recipient", WizardTemplate.simple(
                "add_payment_recipient", "Dodaj primaoca placanja",
                slots, List.of("CLIENT")));
    }

    /* ============================== ORDERS ============================== */

    private void registerCreateBuyOrder() {
        var slots = List.of(
                simpleChoice("listingId", "Koju hartiju kupujemo?",
                        (u, f) -> {
                            // Top STOCK + ako user spomenuo ticker u poruci
                            String hint = (String) f.get("__userMessage");
                            if (hint != null) {
                                var byTicker = guessTickerFromMessage(hint);
                                if (byTicker != null) {
                                    var match = resolvers.listingByTicker(byTicker);
                                    if (!match.isEmpty()) return match;
                                }
                            }
                            return resolvers.topListings("STOCK", 15);
                        }),
                numberInput("quantity", "Koliko jedinica?"),
                simpleChoice("orderType", "Koji tip naloga?",
                        (u, f) -> resolvers.orderTypeOptions()),
                SlotDefinition.builder("limitValue")
                        .prompt("Limit cena (max koliko ste spremni da platite)?")
                        .type(SlotType.NUMBER)
                        .prefill((u, f) -> {
                            // Limit cena nije potrebna za MARKET / STOP
                            String t = (String) f.get("orderType");
                            if (t == null || "MARKET".equals(t) || "STOP".equals(t)) {
                                return Optional.of((Object) "");  // skip slot
                            }
                            return Optional.empty();
                        })
                        .transform(s -> {
                            if (s == null || s.isBlank()) return null;
                            try { return new BigDecimal(s.trim().replace(",", ".")); }
                            catch (NumberFormatException e) { return null; }
                        })
                        .build(),
                SlotDefinition.builder("stopValue")
                        .prompt("Stop cena (kada se aktivira)?")
                        .type(SlotType.NUMBER)
                        .prefill((u, f) -> {
                            String t = (String) f.get("orderType");
                            if (t == null || "MARKET".equals(t) || "LIMIT".equals(t)) {
                                return Optional.of((Object) "");
                            }
                            return Optional.empty();
                        })
                        .transform(s -> {
                            if (s == null || s.isBlank()) return null;
                            try { return new BigDecimal(s.trim().replace(",", ".")); }
                            catch (NumberFormatException e) { return null; }
                        })
                        .build(),
                simpleChoice("accountId", "Sa kog racuna kupujemo?",
                        (u, f) -> resolvers.userAccountsByIdValue(u))
        );
        templates.put("create_buy_order", WizardTemplate.simple(
                "create_buy_order", "Nalog za kupovinu", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    private void registerCreateSellOrder() {
        var slots = List.of(
                simpleChoice("listingId", "Koju hartiju prodajemo?",
                        (u, f) -> resolvers.topListings("STOCK", 15)),
                numberInput("quantity", "Koliko jedinica?"),
                simpleChoice("orderType", "Tip naloga?",
                        (u, f) -> resolvers.orderTypeOptions()),
                simpleChoice("accountId", "Na koji racun ide novac?",
                        (u, f) -> resolvers.userAccountsByIdValue(u))
        );
        templates.put("create_sell_order", WizardTemplate.simple(
                "create_sell_order", "Nalog za prodaju", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    private void registerCancelOrder() {
        var slots = List.of(
                simpleChoice("orderId", "Koji nalog otkazujemo?",
                        (u, f) -> resolvers.userCancelableOrders(u))
        );
        templates.put("cancel_order", WizardTemplate.simple(
                "cancel_order", "Otkazivanje naloga", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    /* ============================== CARDS ============================== */

    private void registerBlockCard() {
        var slots = List.of(
                simpleChoice("cardId", "Koju karticu blokirate?",
                        (u, f) -> resolvers.userCards(u, true))
        );
        templates.put("block_card", WizardTemplate.simple(
                "block_card", "Blokiranje kartice", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    private void registerUnblockCard() {
        var slots = List.of(
                simpleChoice("cardId", "Koju karticu odblokirate?",
                        (u, f) -> resolvers.userCards(u, false))
        );
        templates.put("unblock_card", WizardTemplate.simple(
                "unblock_card", "Odblokiranje kartice", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    private void registerChangeAccountName() {
        var slots = List.of(
                simpleChoice("accountId", "Kome menjate naziv?",
                        (u, f) -> resolvers.userAccountsByIdValue(u)),
                textInput("newName", "Novi naziv?", null)
        );
        templates.put("change_account_name", WizardTemplate.simple(
                "change_account_name", "Promena naziva racuna", slots, List.of("CLIENT")));
    }

    /* ============================== FUNDS ============================== */

    private void registerInvestInFund() {
        var slots = List.of(
                simpleChoice("fundId", "U koji fond ulazete?",
                        (u, f) -> resolvers.investmentFunds(20)),
                simpleChoice("accountId", "Sa kog racuna?",
                        (u, f) -> resolvers.userAccountsByIdValue(u)),
                numberInput("amount", "Iznos uloga?")
        );
        templates.put("invest_in_fund", WizardTemplate.simple(
                "invest_in_fund", "Ulaganje u fond", slots, List.of("CLIENT")));
    }

    private void registerWithdrawFromFund() {
        var slots = List.of(
                simpleChoice("fundId", "Iz kog fonda povlacite?",
                        (u, f) -> resolvers.investmentFunds(20)),
                simpleChoice("destinationAccountId", "Na koji racun?",
                        (u, f) -> resolvers.userAccountsByIdValue(u)),
                numberInput("amount", "Iznos?")
        );
        templates.put("withdraw_from_fund", WizardTemplate.simple(
                "withdraw_from_fund", "Povlacenje iz fonda", slots, List.of("CLIENT")));
    }

    /* ============================== OTC ============================== */

    private void registerAcceptOtcOffer() {
        var slots = List.of(
                simpleChoice("offerId", "Koju ponudu prihvatate?",
                        (u, f) -> resolvers.userActiveOtcOffers(u)),
                simpleChoice("buyerAccountId", "Sa kog racuna placate premium?",
                        (u, f) -> resolvers.userAccountsByIdValue(u))
        );
        templates.put("accept_otc_offer", WizardTemplate.simple(
                "accept_otc_offer", "Prihvatanje OTC ponude", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    private void registerDeclineOtcOffer() {
        var slots = List.of(
                simpleChoice("offerId", "Koju ponudu odbacujete?",
                        (u, f) -> resolvers.userActiveOtcOffers(u))
        );
        templates.put("decline_otc_offer", WizardTemplate.simple(
                "decline_otc_offer", "Odbacivanje OTC ponude", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    private void registerExerciseOtcContract() {
        var slots = List.of(
                simpleChoice("contractId", "Koji ugovor iskorisivate?",
                        (u, f) -> resolvers.userExercisableOtcContracts(u)),
                simpleChoice("buyerAccountId", "Sa kog racuna placate strike?",
                        (u, f) -> resolvers.userAccountsByIdValue(u))
        );
        templates.put("exercise_otc_contract", WizardTemplate.simple(
                "exercise_otc_contract", "Iskoriscavanje OTC ugovora", slots, List.of("CLIENT", "EMPLOYEE")));
    }

    /* ============================== UTIL ============================== */

    private static BigDecimal asBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String guessTickerFromMessage(String msg) {
        if (msg == null) return null;
        // Tickers are typically all-caps 1-5 letter codes
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([A-Z]{1,5})\\b").matcher(msg);
        while (m.find()) {
            String t = m.group(1);
            if (!t.equals("RSD") && !t.equals("EUR") && !t.equals("USD")
                    && !t.equals("CHF") && !t.equals("GBP") && !t.equals("JPY")) {
                return t;
            }
        }
        return null;
    }
}
