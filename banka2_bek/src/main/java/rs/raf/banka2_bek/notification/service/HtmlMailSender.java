package rs.raf.banka2_bek.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

final class HtmlMailSender {

    private HtmlMailSender() {
    }

    static void sendHtmlMail(JavaMailSender mailSender,
                             String fromAddress,
                             String toEmail,
                             String subject,
                             String html) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        }
        catch (MessagingException e) {
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }
}

