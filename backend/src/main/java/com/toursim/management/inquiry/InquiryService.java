package com.toursim.management.inquiry;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;

@Service
public class InquiryService {

    private static final String DEFAULT_DESTINATION = "General travel planning";
    private static final String DEFAULT_TRAVEL_WINDOW = "Flexible dates";

    private final InquiryRepository inquiryRepository;
    private final NotificationService notificationService;
    private final AppUserService appUserService;

    public InquiryService(
        InquiryRepository inquiryRepository,
        NotificationService notificationService,
        AppUserService appUserService
    ) {
        this.inquiryRepository = inquiryRepository;
        this.notificationService = notificationService;
        this.appUserService = appUserService;
    }

    @Transactional
    public Inquiry createInquiry(InquiryRequest request, Optional<AppUser> user) {
        String customerName = request.customerName().trim();
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();
        String destination = normalizeOptional(request.destination(), DEFAULT_DESTINATION);
        String travelWindow = normalizeOptional(request.travelWindow(), DEFAULT_TRAVEL_WINDOW);
        String messageText = request.message().trim();

        Inquiry inquiry = new Inquiry();
        inquiry.setUserId(user.map(AppUser::getId).orElse(null));
        inquiry.setCustomerName(customerName);
        inquiry.setEmail(email);
        inquiry.setPhone(phone);
        inquiry.setDestination(destination);
        inquiry.setTravelWindow(travelWindow);
        inquiry.setTravelers(request.travelers());
        inquiry.setMessage(messageText);
        inquiry.setStatus(InquiryStatus.NEW);
        inquiry = inquiryRepository.save(inquiry);

        user.ifPresent(appUser -> appUserService.syncProfile(appUser, customerName, phone));

        String inquiryTopic = DEFAULT_DESTINATION.equals(destination) ? "your travel plans" : destination;
        String adminSummary = DEFAULT_DESTINATION.equals(destination)
            ? inquiry.getCustomerName() + " sent a new travel support inquiry."
            : inquiry.getCustomerName() + " asked about " + inquiry.getDestination() + ".";

        String subject = "We received your travel inquiry";
        String message = "Hi " + inquiry.getCustomerName() + ",\n\n"
            + "Thanks for contacting Wanderlust about " + inquiryTopic + ". "
            + "Our travel team received your inquiry and will reach out soon.\n\nWanderlust Travels";

        if (user.isPresent()) {
            notificationService.notifyUser(user.get(), NotificationCategory.INQUIRY_RECEIVED, subject, message, null, inquiry.getId());
        } else {
            notificationService.notifyGuest(inquiry.getEmail(), inquiry.getCustomerName(), NotificationCategory.INQUIRY_RECEIVED, subject, message, null, inquiry.getId());
        }

        notificationService.notifyAdmins(
            NotificationCategory.ADMIN_ALERT,
            "New inquiry from " + inquiry.getCustomerName(),
            adminSummary,
            null,
            inquiry.getId()
        );
        return inquiry;
    }

    private String normalizeOptional(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @Transactional(readOnly = true)
    public List<Inquiry> recentInquiries() {
        return inquiryRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Inquiry> findAll() {
        return inquiryRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Inquiry updateStatus(Long id, InquiryStatus status, String adminNotes) {
        Inquiry inquiry = inquiryRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
        InquiryStatus previousStatus = inquiry.getStatus();
        inquiry.setStatus(status);
        inquiry.setAdminNotes(adminNotes == null ? null : adminNotes.trim());
        inquiry = inquiryRepository.save(inquiry);
        Inquiry savedInquiry = inquiry;

        if (previousStatus != status || (savedInquiry.getAdminNotes() != null && !savedInquiry.getAdminNotes().isBlank())) {
            String subject = "Update on your travel inquiry";
            String messageBody = "Hi " + savedInquiry.getCustomerName() + ",\n\n"
                + "Your inquiry is now marked as " + formatStatus(status) + ".";
            if (savedInquiry.getAdminNotes() != null && !savedInquiry.getAdminNotes().isBlank()) {
                messageBody += "\n\nTravel team note: " + savedInquiry.getAdminNotes();
            }
            messageBody += "\n\nWanderlust Travels";
            final String message = messageBody;

            if (savedInquiry.getUserId() != null) {
                appUserService.findById(savedInquiry.getUserId()).ifPresentOrElse(
                    user -> notificationService.notifyUser(
                        user,
                        NotificationCategory.INQUIRY_STATUS_CHANGED,
                        subject,
                        message,
                        null,
                        savedInquiry.getId()
                    ),
                    () -> notificationService.notifyGuest(
                        savedInquiry.getEmail(),
                        savedInquiry.getCustomerName(),
                        NotificationCategory.INQUIRY_STATUS_CHANGED,
                        subject,
                        message,
                        null,
                        savedInquiry.getId()
                    )
                );
            } else {
                notificationService.notifyGuest(
                    savedInquiry.getEmail(),
                    savedInquiry.getCustomerName(),
                    NotificationCategory.INQUIRY_STATUS_CHANGED,
                    subject,
                    message,
                    null,
                    savedInquiry.getId()
                );
            }
        }

        return savedInquiry;
    }

    private String formatStatus(InquiryStatus status) {
        return switch (status) {
            case NEW -> "new";
            case IN_PROGRESS -> "in progress";
            case RESOLVED -> "resolved";
        };
    }
}
