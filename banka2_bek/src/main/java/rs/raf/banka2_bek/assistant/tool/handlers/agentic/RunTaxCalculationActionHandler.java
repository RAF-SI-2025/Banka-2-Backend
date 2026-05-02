package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.tax.service.TaxService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — supervizor pokrece obracun poreza na kapitalnu dobit (15% od prodaje akcija).
 */
@Component
@RequiredArgsConstructor
public class RunTaxCalculationActionHandler implements WriteToolHandler {

    private final TaxService taxService;

    @Override
    public String name() { return "run_tax_calculation"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor pokrece obracun poreza na kapitalnu dobit za sve " +
                        "korisnike. 15% od profita od prodaje akcija (berza + OTC). " +
                        "Konvertuje se u RSD i salje na drzavni racun. Inace auto-pokreta " +
                        "kraj svakog meseca.")
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Akcija", "Pokretanje obracuna poreza za sve korisnike");
        fields.put("Stopa", "15% od kapitalne dobiti");
        return new PreviewResult("Pokretanje obracuna poreza", fields,
                List.of("Iznos ce biti odmah skinut sa svih korisnikih racuna i prebacen na drzavni RSD racun. Akcija je ireverzibilna."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        taxService.calculateTaxForAllUsers();
        return Map.of("status", "OK", "message", "Tax calculation triggered for all users");
    }
}
