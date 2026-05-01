package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.service.OrderService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — Supervizor odbija pending order agenta.
 */
@Component
@RequiredArgsConstructor
public class DeclineOrderSupervisorActionHandler implements WriteToolHandler {

    private final OrderService orderService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "decline_order_supervisor"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor odbija pending order agenta. Razlikuje se od " +
                        "cancel_order koji moze biti pozvan od bilo kog usera (svoji orderi).")
                .param(new ToolDefinition.Param("orderId", "integer",
                        "ID order-a za odbijanje", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long orderId = support.getLong(args, "orderId");
        if (orderId == null) throw new IllegalArgumentException("orderId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Order ID", orderId);
        fields.put("Akcija", "Odbijanje (DECLINE)");
        return new PreviewResult("Odbij order #" + orderId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        OrderDto dto = orderService.declineOrder(support.getLong(args, "orderId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", dto.getId());
        result.put("status", dto.getStatus());
        return result;
    }
}
