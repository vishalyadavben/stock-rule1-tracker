package com.rule1.tracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends invite notification emails. Deliberately best-effort: if MAIL_USERNAME isn't configured
 * (the default for local/dev use), this silently logs instead of sending — sharing itself still
 * fully works in-app the moment the recipient logs in, so a missing email provider is never a
 * hard requirement, only a convenience on top. Set MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD (e.g.
 * a Gmail App Password, or an SMTP relay from SendGrid/Mailgun/Postmark) to turn on real emails.
 */
@Service
public class EmailService {

    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** ObjectProvider instead of a direct JavaMailSender dependency: if mail autoconfiguration
     *  doesn't produce a bean (e.g. MAIL_HOST left blank), the whole app must still start up
     *  cleanly — email is a bonus feature on top of sharing, never a hard dependency. */
    public EmailService(org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null && mailUsername != null && !mailUsername.isBlank();
    }

    public void sendShareInviteEmail(String toEmail, String ownerEmail, String ticker, String permission) {
        if (!isConfigured()) {
            System.out.println("[EmailService] Mail not configured — skipping email to " + toEmail
                    + " (share invite for " + ticker + " from " + ownerEmail + ")");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(ownerEmail + " shared " + ticker + " analysis with you on Rule #1 Tracker");
            message.setText(
                    ownerEmail + " has given you " + permission.toLowerCase() + " access to their "
                    + ticker + " analysis (checklist, Sticker Price calculations, and business score) "
                    + "on Rule #1 Tracker.\n\n"
                    + "Log in (or create an account with this email address) at " + frontendUrl
                    + " and open \"Shared with me\" to see it.\n"
            );
            mailSenderProvider.getObject().send(message);
        } catch (Exception e) {
            // Never let an email failure block the actual share — access is already granted
            // in the database regardless of whether this notification succeeds.
            System.err.println("[EmailService] Failed to send share invite email: " + e.getMessage());
        }
    }
}
