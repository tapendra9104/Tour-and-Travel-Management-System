package com.toursim.management.notification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.toursim.management.auth.AppUser;

import jakarta.mail.internet.MimeMessage;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationEmailRenderer notificationEmailRenderer;
    private final String fromEmail;
    private final String adminEmail;
    private final String mailHost;
    private final String supportEmail;
    private final String brandName;

    public NotificationService(
        NotificationRepository notificationRepository,
        ObjectProvider<JavaMailSender> mailSenderProvider,
        NotificationEmailRenderer notificationEmailRenderer,
        @Value("${app.notifications.from-email}") String fromEmail,
        @Value("${app.notifications.admin-email}") String adminEmail,
        @Value("${spring.mail.host:}") String mailHost,
        @Value("${app.notifications.support-email:hello@wanderlust.com}") String supportEmail,
        @Value("${app.notifications.brand-name:Wanderlust Travels}") String brandName
    ) {
        this.notificationRepository = notificationRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.notificationEmailRenderer = notificationEmailRenderer;
        this.fromEmail = fromEmail;
        this.adminEmail = adminEmail;
        this.mailHost = mailHost;
        this.supportEmail = supportEmail;
        this.brandName = brandName;
    }

    @Transactional
    public Notification notifyUser(
        AppUser appUser,
        NotificationCategory category,
        String subject,
        String message,
        Long relatedBookingId,
        Long relatedInquiryId
    ) {
        return send(
            appUser.getId(),
            appUser.getEmail(),
            appUser.getFullName(),
            false,
            category,
            subject,
            message,
            relatedBookingId,
            relatedInquiryId
        );
    }

    @Transactional
    public Notification notifyGuest(
        String email,
        String name,
        NotificationCategory category,
        String subject,
        String message,
        Long relatedBookingId,
        Long relatedInquiryId
    ) {
        return send(null, email, name, false, category, subject, message, relatedBookingId, relatedInquiryId);
    }

    @Transactional
    public Notification notifyAdmins(NotificationCategory category, String subject, String message, Long relatedBookingId, Long relatedInquiryId) {
        Notification firstNotification = null;
        for (String recipient : adminRecipients()) {
            Notification notification = send(
                null,
                recipient,
                "Wanderlust Admin",
                true,
                category,
                subject,
                message,
                relatedBookingId,
                relatedInquiryId
            );
            if (firstNotification == null) {
                firstNotification = notification;
            }
        }
        return firstNotification == null
            ? send(null, adminEmail, "Wanderlust Admin", true, category, subject, message, relatedBookingId, relatedInquiryId)
            : firstNotification;
    }

    @Transactional(readOnly = true)
    public List<Notification> recentForUser(AppUser appUser) {
        return notificationRepository.findTop8ByRecipientUserIdOrderByCreatedAtDesc(appUser.getId());
    }

    @Transactional(readOnly = true)
    public List<Notification> recentAdminAlerts() {
        return notificationRepository.findTop10ByAdminVisibleTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public boolean hasRecentBookingEmail(NotificationCategory category, Long relatedBookingId, String recipientEmail, LocalDateTime createdAfter) {
        if (!StringUtils.hasText(recipientEmail) || relatedBookingId == null) {
            return false;
        }
        return notificationRepository.existsByCategoryAndRelatedBookingIdAndRecipientEmailIgnoreCaseAndCreatedAtAfter(
            category,
            relatedBookingId,
            recipientEmail.trim(),
            createdAfter
        );
    }

    private Notification send(
        Long recipientUserId,
        String recipientEmail,
        String recipientName,
        boolean adminVisible,
        NotificationCategory category,
        String subject,
        String message,
        Long relatedBookingId,
        Long relatedInquiryId
    ) {
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setRecipientEmail(recipientEmail);
        notification.setRecipientName(recipientName);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(category);
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAdminVisible(adminVisible);
        notification.setRelatedBookingId(relatedBookingId);
        notification.setRelatedInquiryId(relatedInquiryId);
        notification = notificationRepository.save(notification);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(mailHost) || !StringUtils.hasText(recipientEmail)) {
            notification.setStatus(NotificationStatus.STORED);
            return notificationRepository.save(notification);
        }

        try {
            NotificationEmailContent emailContent = notificationEmailRenderer.render(
                category,
                recipientName,
                adminVisible,
                subject,
                message,
                relatedBookingId,
                relatedInquiryId
            );
            MimeMessage mailMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mailMessage, "UTF-8");
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(emailContent.plainText(), emailContent.htmlText());
            helper.setFrom(fromEmail, brandName);
            if (StringUtils.hasText(supportEmail)) {
                helper.setReplyTo(supportEmail);
            }
            mailSender.send(mailMessage);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } catch (Exception exception) {
            LOGGER.warn("Failed to send notification email to {}", recipientEmail, exception);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(exception.getMessage());
        }

        return notificationRepository.save(notification);
    }

    private List<String> adminRecipients() {
        if (!StringUtils.hasText(adminEmail)) {
            return List.of();
        }
        return Arrays.stream(adminEmail.split("[,;]"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.toList());
    }
}
