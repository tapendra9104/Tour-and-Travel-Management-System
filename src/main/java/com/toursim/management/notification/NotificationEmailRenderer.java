package com.toursim.management.notification;

import java.time.Year;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationEmailRenderer {

    private final String brandName;
    private final String supportEmail;
    private final String baseUrl;

    public NotificationEmailRenderer(
        @Value("${app.notifications.brand-name:Wanderlust Travels}") String brandName,
        @Value("${app.notifications.support-email:hello@wanderlust.com}") String supportEmail,
        @Value("${app.notifications.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.brandName = brandName;
        this.supportEmail = supportEmail;
        this.baseUrl = baseUrl;
    }

    public NotificationEmailContent render(
        NotificationCategory category,
        String recipientName,
        boolean adminVisible,
        String subject,
        String message,
        Long relatedBookingId,
        Long relatedInquiryId
    ) {
        String greetingName = hasText(recipientName) ? recipientName.trim() : "Traveler";
        String categoryLabel = toTitleCase(category.name().replace('_', ' '));
        EmailAction action = resolveAction(category, adminVisible, relatedBookingId, relatedInquiryId);
        String plainText = buildPlainText(greetingName, categoryLabel, subject, message, action);
        String htmlText = buildHtml(greetingName, categoryLabel, subject, message, action);
        return new NotificationEmailContent(plainText, htmlText);
    }

    private String buildPlainText(String greetingName, String categoryLabel, String subject, String message, EmailAction action) {
        StringBuilder builder = new StringBuilder();
        builder.append(subject).append("\n");
        builder.append(categoryLabel).append("\n\n");
        builder.append("Hi ").append(greetingName).append(",\n\n");
        builder.append(message == null ? "" : message.trim()).append("\n\n");
        if (action != null) {
            builder.append(action.label()).append(": ").append(action.url()).append("\n\n");
        }
        builder.append("Need help? Reply to ").append(supportEmail).append(".\n\n");
        builder.append(brandName);
        return builder.toString();
    }

    private String buildHtml(String greetingName, String categoryLabel, String subject, String message, EmailAction action) {
        String paragraphs = toHtmlParagraphs(message);
        String actionHtml = action == null
            ? ""
            : """
                <div style="margin-top:24px;">
                  <a href="%s" style="display:inline-block;background:#0f9d58;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:10px;font-weight:600;">%s</a>
                </div>
                <p style="margin:12px 0 0;color:#64748b;font-size:13px;">If the button does not work, use this link: <a href="%s" style="color:#0f9d58;">%s</a></p>
                """.formatted(
                escapeHtml(action.url()),
                escapeHtml(action.label()),
                escapeHtml(action.url()),
                escapeHtml(action.url())
            );

        return """
            <!DOCTYPE html>
            <html lang="en">
            <body style="margin:0;background:#f5f7fb;font-family:Arial,sans-serif;color:#0f172a;">
              <div style="max-width:680px;margin:0 auto;padding:32px 16px;">
                <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:20px;overflow:hidden;">
                  <div style="padding:28px 32px;background:linear-gradient(135deg,#0f9d58 0%%,#0b7a45 100%%);color:#ffffff;">
                    <div style="font-size:14px;letter-spacing:0.08em;text-transform:uppercase;opacity:0.9;">%s</div>
                    <h1 style="margin:12px 0 0;font-size:28px;line-height:1.2;">%s</h1>
                  </div>
                  <div style="padding:32px;">
                    <div style="display:inline-block;padding:6px 12px;border-radius:999px;background:#ecfdf3;color:#0f9d58;font-size:12px;font-weight:700;letter-spacing:0.04em;text-transform:uppercase;">%s</div>
                    <p style="margin:24px 0 0;font-size:16px;line-height:1.6;">Hi %s,</p>
                    %s
                    %s
                    <div style="margin-top:28px;padding-top:20px;border-top:1px solid #e2e8f0;">
                      <p style="margin:0;color:#475569;font-size:14px;line-height:1.6;">Need help? Contact <a href="mailto:%s" style="color:#0f9d58;">%s</a>.</p>
                      <p style="margin:8px 0 0;color:#94a3b8;font-size:12px;">%s (C) %d</p>
                    </div>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(
            escapeHtml(brandName),
            escapeHtml(subject),
            escapeHtml(categoryLabel),
            escapeHtml(greetingName),
            paragraphs,
            actionHtml,
            escapeHtml(supportEmail),
            escapeHtml(supportEmail),
            escapeHtml(brandName),
            Year.now().getValue()
        );
    }

    private EmailAction resolveAction(NotificationCategory category, boolean adminVisible, Long relatedBookingId, Long relatedInquiryId) {
        if (adminVisible) {
            return new EmailAction("Open Admin Dashboard", normalizeBaseUrl() + "/dashboard");
        }

        return switch (category) {
            case ACCOUNT_CREATED -> new EmailAction("Sign In", normalizeBaseUrl() + "/login");
            case INQUIRY_RECEIVED, INQUIRY_STATUS_CHANGED -> new EmailAction("Contact Support", normalizeBaseUrl() + "/contact");
            case WAITLIST_JOINED, WAITLIST_AVAILABLE, BOOKING_RECEIVED, BOOKING_STATUS_CHANGED,
                TRAVELER_PREFERENCES_UPDATED, PAYMENT_RECEIVED, PAYMENT_DUE, REFUND_PROCESSED, TRIP_REMINDER ->
                new EmailAction("View My Bookings", normalizeBaseUrl() + "/dashboard");
            case ADMIN_ALERT, BOOKING_FAILED -> new EmailAction("Open Dashboard", normalizeBaseUrl() + "/dashboard");
        };
    }

    private String normalizeBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String toHtmlParagraphs(String message) {
        List<String> paragraphs = List.of((message == null ? "" : message).trim().split("\\r?\\n\\r?\\n"));
        StringBuilder builder = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (!hasText(paragraph)) {
                continue;
            }
            String escaped = escapeHtml(paragraph.trim()).replace("\n", "<br>");
            builder.append("<p style=\"margin:16px 0 0;font-size:15px;line-height:1.7;color:#334155;\">")
                .append(escaped)
                .append("</p>");
        }
        return builder.toString();
    }

    private String toTitleCase(String value) {
        String[] words = value.toLowerCase().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!hasText(word)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private String escapeHtml(String value) {
        return (value == null ? "" : value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record EmailAction(String label, String url) {
    }
}
