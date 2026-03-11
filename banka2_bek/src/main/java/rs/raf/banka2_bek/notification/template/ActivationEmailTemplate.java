package rs.raf.banka2_bek.notification.template;

import org.springframework.stereotype.Component;

@Component
public class ActivationEmailTemplate {

    public String buildSubject() {
        return "Activate your Banka 2 account";
    }

    public String buildBody(String activationLink, String firstName) {
        String greeting = (firstName != null && !firstName.isBlank()) ? firstName : "there";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Activate your account</title>
                    <style>
                        .primary-button {
                            display: inline-block;
                            padding: 12px 24px;
                            background: linear-gradient(135deg,#059669,#047857);
                            color: #ffffff !important;
                            text-decoration: none;
                            border-radius: 999px;
                            font-size: 14px;
                            font-weight: 600;
                            letter-spacing: 0.04em;
                            text-transform: uppercase;
                        }

                        .primary-button:hover {
                            background: linear-gradient(135deg,#10b981,#059669);
                        }
                    </style>
                </head>
                <body style="margin:0;padding:0;background-color:transparent;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background-color:transparent;padding:32px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:520px;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.32);border:1px solid #e5e7eb;">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#059669,#047857);padding:18px 24px;color:#e5e7eb;text-align:center;">
                                        <h1 style="margin:0;font-size:20px;font-weight:600;letter-spacing:0.02em;">Banka 2 – Activate your account</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;text-align:center;">
                                        <p style="margin:0 0 16px 0;font-size:14px;color:#4b5563;">Hi %s,</p>
                                        <p style="margin:0 0 16px 0;font-size:14px;color:#4b5563;">
                                            Your Banka 2 employee account has been created. Click the button below to activate it and set your password.
                                        </p>
                                        <p style="margin:0 0 20px 0;font-size:14px;color:#4b5563;">
                                            The activation link expires in 24 hours and can only be used once.
                                        </p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px 0;width:100%%;text-align:center;">
                                            <tr>
                                                <td>
                                                    <a href="%s"
                                                       class="primary-button">
                                                        Activate account
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 12px 0;font-size:13px;color:#6b7280;">
                                            If the button above does not work, copy and paste this link into your browser:
                                        </p>
                                        <p style="margin:0 0 20px 0;font-size:12px;color:#059669;word-break:break-all;">
                                            %s
                                        </p>
                                        <p style="margin:0 0 0 0;font-size:12px;color:#9ca3af;">
                                            If you did not expect this email, please contact your administrator.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:16px 24px;border-top:1px solid #e5e7eb;background-color:#f9fafb;">
                                        <p style="margin:0;font-size:11px;color:#9ca3af;text-align:center;">
                                            This is an automated message from Banka 2. Please do not reply to this email.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(greeting, activationLink, activationLink);
    }
}
