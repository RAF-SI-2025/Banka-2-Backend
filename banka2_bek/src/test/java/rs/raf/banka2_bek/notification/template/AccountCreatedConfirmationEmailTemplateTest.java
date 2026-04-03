package rs.raf.banka2_bek.notification.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountCreatedConfirmationEmailTemplateTest {

    private AccountCreatedConfirmationEmailTemplate template;

    @BeforeEach
    void setUp() {
        template = new AccountCreatedConfirmationEmailTemplate();
    }

    @Test
    void buildSubject_returnsNonEmptyString() {
        assertThat(template.buildSubject()).isNotBlank();
    }

    @Test
    void buildSubject_containsBanka2() {
        assertThat(template.buildSubject()).contains("Banka 2");
    }

    @Test
    void buildSubject_mentionsAccount() {
        assertThat(template.buildSubject()).containsIgnoringCase("račun");
    }

    @Test
    void buildBody_containsFirstName() {
        String body = template.buildBody("Marko", "2220001000000011", "Tekući");
        assertThat(body).contains("Marko");
    }

    @Test
    void buildBody_containsAccountNumber() {
        String body = template.buildBody("Ana", "2220001000000021", "Devizni");
        assertThat(body).contains("2220001000000021");
    }

    @Test
    void buildBody_containsAccountType() {
        String body = template.buildBody("Petar", "1234567890123456", "Štedni");
        assertThat(body).contains("Štedni");
    }

    @Test
    void buildBody_nullFirstName_usesDefaultGreeting() {
        String body = template.buildBody(null, "1234567890123456", "Tekući");
        assertThat(body).contains("Poštovani");
    }

    @Test
    void buildBody_blankFirstName_usesDefaultGreeting() {
        String body = template.buildBody("   ", "1234567890123456", "Tekući");
        assertThat(body).contains("Poštovani");
    }

    @Test
    void buildBody_containsHtmlStructure() {
        String body = template.buildBody("Test", "1234567890", "Tekući");
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("<html lang=\"sr\">");
        assertThat(body).contains("</html>");
    }

    @Test
    void buildBody_containsGradientHeader() {
        String body = template.buildBody("Test", "1234567890", "Tekući");
        assertThat(body).contains("linear-gradient(135deg,#6366f1,#7c3aed)");
    }

    @Test
    void buildBody_containsBanka2Branding() {
        String body = template.buildBody("Test", "1234567890", "Tekući");
        assertThat(body).contains("Banka 2");
    }

    @Test
    void buildBody_containsAutoMessageFooter() {
        String body = template.buildBody("Test", "1234567890", "Tekući");
        assertThat(body).contains("automatska poruka");
    }

    @Test
    void buildBody_containsCheckmarkIcon() {
        String body = template.buildBody("Test", "1234567890", "Tekući");
        assertThat(body).contains("&#9745;");
    }

    @Test
    void buildBody_containsTipRacuna() {
        String body = template.buildBody("Test", "1234567890", "Poslovni");
        assertThat(body).contains("Tip računa");
        assertThat(body).contains("Broj računa");
    }
}
