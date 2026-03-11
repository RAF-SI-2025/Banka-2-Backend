package rs.raf.banka2_bek.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.notification.template.ActivationEmailTemplate;
import rs.raf.banka2_bek.notification.template.PasswordResetEmailTemplate;

@Service
public class MailNotificationService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String passwordResetUrlBase;
    private final String passwordResetPagePath;
    private final String activationUrlBase;
    private final String activationPagePath;
    private final PasswordResetEmailTemplate passwordResetEmailTemplate;
    private final ActivationEmailTemplate activationEmailTemplate;

    public MailNotificationService(JavaMailSender mailSender,
                                   PasswordResetEmailTemplate passwordResetEmailTemplate,
                                   ActivationEmailTemplate activationEmailTemplate,
                                   @Value("${spring.mail.username}") String fromAddress,
                                   @Value("${notification.password-reset-url-base}") String passwordResetUrlBase,
                                   @Value("${notification.password-reset-page-path:/reset-password}") String passwordResetPagePath,
                                   @Value("${notification.activation-url-base}") String activationUrlBase,
                                   @Value("${notification.activation-page-path:/activate}") String activationPagePath) {
        this.mailSender = mailSender;
        this.passwordResetEmailTemplate = passwordResetEmailTemplate;
        this.activationEmailTemplate = activationEmailTemplate;
        this.fromAddress = fromAddress;
        this.passwordResetUrlBase = passwordResetUrlBase;
        this.passwordResetPagePath = passwordResetPagePath;
        this.activationUrlBase = activationUrlBase;
        this.activationPagePath = activationPagePath;
    }

    public void sendPasswordResetMail(String toEmail, String token) {
        String resetLink = passwordResetUrlBase + passwordResetPagePath + "?token=" + token;
        String subject = passwordResetEmailTemplate.buildSubject();
        String html = passwordResetEmailTemplate.buildBody(resetLink);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendActivationMail(String toEmail, String firstName, String token) {
        String activationLink = activationUrlBase + activationPagePath + "?token=" + token;
        String subject = activationEmailTemplate.buildSubject();
        String html = activationEmailTemplate.buildBody(activationLink, firstName);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }
}

