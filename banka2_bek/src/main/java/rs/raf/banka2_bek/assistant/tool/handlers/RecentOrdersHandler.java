package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * get_recent_orders(limit) -> {orderCount, orders[]}
 *
 * Vraca poslednjih N ordera korisnika (sortirano po lastModification desc).
 * Klijenti i zaposleni dobijaju samo SVOJE ordere (filtriranje po userId+userRole
 * je ugrađeno u OrderRepository pretragu).
 */
@Component
@RequiredArgsConstructor
public class RecentOrdersHandler implements ToolHandler {

    private final OrderRepository orderRepository;
    private final AssistantProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.getTools().getInternal().isRecentOrders();
    }

    @Override
    public String name() {
        return "get_recent_orders";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Vraca poslednjih N ordera korisnika sa osnovnim " +
                        "informacijama (ticker, smer, kolicina, status, datum). " +
                        "Pozovi kad korisnik pita 'koje ordere imam', 'sta sam " +
                        "kupio', 'sta mi je status'.")
                .param(new ToolDefinition.Param("limit", "integer",
                        "Broj poslednjih ordera (1-20).", false, 5, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        int limit = Math.min(Math.max(parseInt(args.get("limit"), 5), 1), 20);
        var page = orderRepository.findByUserId(
                user.userId(),
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastModification"))
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : page.getContent()) {
            // Filtriraj jos i po userRole — nije obuhvaceno u pretrazi
            if (!user.userRole().equalsIgnoreCase(o.getUserRole())) continue;
            rows.add(Map.of(
                    "id", o.getId(),
                    "ticker", o.getListing() != null ? o.getListing().getTicker() : "?",
                    "direction", o.getDirection() != null ? o.getDirection().name() : "?",
                    "type", o.getOrderType() != null ? o.getOrderType().name() : "?",
                    "quantity", o.getQuantity() != null ? o.getQuantity() : 0,
                    "status", o.getStatus() != null ? o.getStatus().name() : "?",
                    "isDone", o.isDone(),
                    "lastModification", o.getLastModification() != null
                            ? o.getLastModification().toString() : "-"
            ));
        }
        return Map.of(
                "orderCount", rows.size(),
                "orders", rows
        );
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
