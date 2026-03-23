package com.toursim.management.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.UserRole;
import com.toursim.management.booking.BookingActivity;
import com.toursim.management.booking.BookingActivityService;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingStatus;
import com.toursim.management.booking.BookingService;
import com.toursim.management.booking.dto.BookingResponse;
import com.toursim.management.inquiry.Inquiry;
import com.toursim.management.inquiry.InquiryResponse;
import com.toursim.management.inquiry.InquiryService;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.payment.PaymentService;
import com.toursim.management.payment.PaymentSummary;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;
import com.toursim.management.tour.TourViewService;
import com.toursim.management.waitlist.WaitlistEntry;
import com.toursim.management.waitlist.WaitlistService;

@Service
public class DashboardService {

    private final BookingService bookingService;
    private final TourCatalogService tourCatalogService;
    private final NotificationService notificationService;
    private final InquiryService inquiryService;
    private final TourViewService tourViewService;
    private final WaitlistService waitlistService;
    private final BookingActivityService bookingActivityService;
    private final PaymentService paymentService;

    public DashboardService(
        BookingService bookingService,
        TourCatalogService tourCatalogService,
        NotificationService notificationService,
        InquiryService inquiryService,
        TourViewService tourViewService,
        WaitlistService waitlistService,
        BookingActivityService bookingActivityService,
        PaymentService paymentService
    ) {
        this.bookingService = bookingService;
        this.tourCatalogService = tourCatalogService;
        this.notificationService = notificationService;
        this.inquiryService = inquiryService;
        this.tourViewService = tourViewService;
        this.waitlistService = waitlistService;
        this.bookingActivityService = bookingActivityService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(Optional<AppUser> actor, Optional<Booking> guestLookupBooking) {
        List<Tour> allTours = tourCatalogService.findAll();
        Map<String, Tour> toursById = allTours.stream().collect(Collectors.toMap(Tour::getId, Function.identity()));

        if (actor.isPresent() && actor.get().getRole() == UserRole.ADMIN) {
            List<Booking> bookings = bookingService.findAll();
            Map<Long, PaymentSummary> paymentSummaries = paymentService.summarize(bookings);
            List<Inquiry> inquiries = inquiryService.findAll();
            List<WaitlistEntry> waitlists = waitlistService.recentActiveEntries();
            return new DashboardResponse(
                "admin",
                true,
                true,
                "Admin Dashboard",
                toStats(bookings, paymentSummaries, true),
                toResponses(bookings, toursById, paymentSummaries),
                adminNotifications(bookings, inquiries, waitlists, toursById, paymentSummaries),
                toInquiryResponses(inquiries),
                toCustomerSummaries(bookings),
                tourCatalogService.featuredTours(3)
            );
        }

        if (actor.isPresent()) {
            List<Booking> bookings = bookingService.findVisibleBookings(actor);
            Map<Long, PaymentSummary> paymentSummaries = paymentService.summarize(bookings);
            return new DashboardResponse(
                "user",
                true,
                false,
                "My Bookings",
                toStats(bookings, paymentSummaries, false),
                toResponses(bookings, toursById, paymentSummaries),
                travelerNotifications(actor, bookings, toursById, paymentSummaries),
                List.of(),
                List.of(),
                personalizedRecommendations(actor.get(), bookings, allTours, toursById)
            );
        }

        if (guestLookupBooking.isPresent()) {
            List<Booking> bookings = List.of(guestLookupBooking.get());
            Map<Long, PaymentSummary> paymentSummaries = paymentService.summarize(bookings);
            return new DashboardResponse(
                "guest",
                false,
                false,
                "Booking Lookup",
                toStats(bookings, paymentSummaries, false),
                toResponses(bookings, toursById, paymentSummaries),
                travelerNotifications(Optional.empty(), bookings, toursById, paymentSummaries),
                List.of(),
                List.of(),
                allTours.isEmpty()
                    ? List.of()
                    : tourCatalogService.relatedTours(
                        toursById.getOrDefault(guestLookupBooking.get().getTourId(), allTours.get(0)),
                        3
                    )
            );
        }

        return new DashboardResponse(
            "anonymous",
            false,
            false,
            "My Bookings",
            new DashboardStats(0, 0, 0, 0, BigDecimal.ZERO),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            tourCatalogService.featuredTours(3)
        );
    }

    private DashboardStats toStats(List<Booking> bookings, Map<Long, PaymentSummary> paymentSummaries, boolean adminView) {
        int total = bookings.size();
        int pending = (int) bookings.stream().filter(booking -> booking.getStatus().name().equals("PENDING")).count();
        int confirmed = (int) bookings.stream().filter(booking -> booking.getStatus().name().equals("CONFIRMED")).count();
        int cancelled = (int) bookings.stream().filter(booking -> booking.getStatus().name().equals("CANCELLED")).count();
        BigDecimal revenue = bookings.stream()
            .map(booking -> adminView
                ? paymentSummaries.getOrDefault(booking.getId(), paymentService.summarize(booking)).netPaidAmount()
                : (booking.getStatus() == BookingStatus.CANCELLED ? BigDecimal.ZERO : booking.getTotalPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardStats(total, pending, confirmed, cancelled, revenue);
    }

    private List<BookingResponse> toResponses(List<Booking> bookings, Map<String, Tour> toursById, Map<Long, PaymentSummary> paymentSummaries) {
        return bookings.stream()
            .map(booking -> BookingResponse.from(
                booking,
                Optional.ofNullable(toursById.get(booking.getTourId()))
                    .map(Tour::getTitle)
                    .orElse(booking.getBookingReference()),
                paymentSummaries.getOrDefault(booking.getId(), paymentService.summarize(booking))
            ))
            .toList();
    }

    private List<CustomerSummaryResponse> toCustomerSummaries(List<Booking> bookings) {
        return bookings.stream()
            .collect(Collectors.groupingBy(Booking::getEmail))
            .values()
            .stream()
            .map(customerBookings -> {
                Booking latestBooking = customerBookings.stream()
                    .max(Comparator.comparing(Booking::getCreatedAt))
                    .orElseThrow();
                BigDecimal totalSpend = customerBookings.stream()
                    .map(Booking::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                LocalDate latestTravelDate = customerBookings.stream()
                    .map(Booking::getDate)
                    .max(LocalDate::compareTo)
                    .orElse(latestBooking.getDate());
                return new CustomerSummaryResponse(
                    latestBooking.getCustomerName(),
                    latestBooking.getEmail(),
                    latestBooking.getPhone(),
                    customerBookings.size(),
                    totalSpend,
                    latestTravelDate,
                    customerSegment(customerBookings.size(), totalSpend)
                );
            })
            .sorted(Comparator.comparing(CustomerSummaryResponse::totalSpend).reversed())
            .limit(8)
            .toList();
    }

    private List<NotificationResponse> travelerNotifications(
        Optional<AppUser> actor,
        List<Booking> bookings,
        Map<String, Tour> toursById,
        Map<Long, PaymentSummary> paymentSummaries
    ) {
        List<NotificationResponse> stored = actor.isPresent()
            ? notificationService.recentForUser(actor.get()).stream().map(NotificationResponse::from).toList()
            : List.of();

        List<NotificationResponse> generated = bookings.stream()
            .flatMap(booking -> travelerAlertsForBooking(
                booking,
                toursById,
                paymentSummaries.getOrDefault(booking.getId(), paymentService.summarize(booking))
            ).stream())
            .toList();

        return java.util.stream.Stream.concat(generated.stream(), stored.stream())
            .sorted(Comparator.comparing(NotificationResponse::createdAt).reversed())
            .limit(10)
            .toList();
    }

    private List<InquiryResponse> toInquiryResponses(List<Inquiry> inquiries) {
        return inquiries.stream()
            .sorted(Comparator.comparingInt(this::inquiryPriorityRank)
                .thenComparing(Inquiry::getCreatedAt, Comparator.reverseOrder()))
            .limit(10)
            .map(inquiry -> InquiryResponse.from(inquiry, inquiryPriority(inquiry), inquiryAgeHours(inquiry)))
            .toList();
    }

    private List<NotificationResponse> adminNotifications(
        List<Booking> bookings,
        List<Inquiry> inquiries,
        List<WaitlistEntry> waitlists,
        Map<String, Tour> toursById,
        Map<Long, PaymentSummary> paymentSummaries
    ) {
        List<NotificationResponse> stored = notificationService.recentAdminAlerts().stream()
            .map(NotificationResponse::from)
            .toList();

        List<NotificationResponse> generated = new java.util.ArrayList<>();
        long activeWaitlistCount = waitlistService.activeWaitlistCount();

        List<Booking> stalePending = bookingService.stalePendingBookings(24);
        if (!stalePending.isEmpty()) {
            generated.add(NotificationResponse.operational(
                "sla",
                stalePending.size() + " booking" + (stalePending.size() == 1 ? "" : "s") + " pending over 24 hours",
                "Review pending reservations before they slip past the customer-response SLA.",
                "warning",
                stalePending.get(0).getCreatedAt()
            ));
        }

        long criticalInquiries = inquiries.stream()
            .filter(inquiry -> !"resolved".equals(inquiry.getStatus().name().toLowerCase()))
            .filter(inquiry -> inquiryPriority(inquiry).equals("high"))
            .count();
        if (criticalInquiries > 0) {
            generated.add(NotificationResponse.operational(
                "support",
                criticalInquiries + " " + (criticalInquiries == 1 ? "inquiry needs" : "inquiries need") + " urgent follow-up",
                "High-priority inquiries are approaching or exceeding the response target.",
                "warning",
                LocalDateTime.now()
            ));
        }

        if (activeWaitlistCount > 0 && !waitlists.isEmpty()) {
            WaitlistEntry hottest = waitlists.get(0);
            String title = Optional.ofNullable(toursById.get(hottest.getTourId()))
                .map(Tour::getTitle)
                .orElse(hottest.getTourId());
            generated.add(NotificationResponse.operational(
                "waitlist",
                activeWaitlistCount + " active waitlist request" + (activeWaitlistCount == 1 ? "" : "s"),
                "Newest active waitlist demand is for " + title + " on " + hottest.getTravelDate() + ".",
                "info",
                hottest.getCreatedAt()
            ));
        }

        long transportQueue = bookings.stream()
            .filter(booking -> requiresTransportFollowUp(booking))
            .count();
        if (transportQueue > 0) {
            generated.add(NotificationResponse.operational(
                "transport",
                transportQueue + " booking" + (transportQueue == 1 ? "" : "s") + " need transport action",
                "Upcoming departures still need flight, train, or bus arrangements confirmed.",
                "warning",
                LocalDateTime.now()
            ));
        }

        long documentsQueue = bookings.stream()
            .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
            .filter(booking -> !booking.isDocumentsVerified())
            .filter(booking -> !booking.getDate().isBefore(LocalDate.now()))
            .filter(booking -> java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), booking.getDate()) <= 21)
            .count();
        if (documentsQueue > 0) {
            generated.add(NotificationResponse.operational(
                "documents",
                documentsQueue + " confirmed booking" + (documentsQueue == 1 ? "" : "s") + " still need document checks",
                "Verify passport or ID readiness before departure operations lock in.",
                "warning",
                LocalDateTime.now()
            ));
        }

        long specialServicesQueue = bookings.stream()
            .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
            .filter(booking -> hasText(booking.getMealPreference())
                || hasText(booking.getDietaryRestrictions())
                || hasText(booking.getOccasionType())
                || hasText(booking.getOccasionNotes())
                || hasText(booking.getRoomPreference())
                || hasText(booking.getTripStyle())
                || hasText(booking.getAssistanceNotes())
                || booking.isTransferRequired())
            .filter(booking -> !booking.getDate().isBefore(LocalDate.now()))
            .count();
        if (specialServicesQueue > 0) {
            generated.add(NotificationResponse.operational(
                "guest-services",
                specialServicesQueue + " booking" + (specialServicesQueue == 1 ? "" : "s") + " include special service requests",
                "Meals, dietary notes, honeymoon or celebration setups, room requests, and pickups are ready for the operations team to coordinate.",
                "info",
                LocalDateTime.now()
            ));
        }

        long paymentQueue = bookings.stream()
            .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
            .map(booking -> paymentSummaries.getOrDefault(booking.getId(), paymentService.summarize(booking)))
            .filter(summary -> summary.outstandingAmount().compareTo(BigDecimal.ZERO) > 0)
            .count();
        if (paymentQueue > 0) {
            generated.add(NotificationResponse.operational(
                "payments",
                paymentQueue + " booking" + (paymentQueue == 1 ? "" : "s") + " still need payment follow-up",
                "Deposits or balances are still open across active reservations.",
                "warning",
                LocalDateTime.now()
            ));
        }

        long overduePayments = bookings.stream()
            .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
            .map(booking -> paymentSummaries.getOrDefault(booking.getId(), paymentService.summarize(booking)))
            .filter(PaymentSummary::overdue)
            .count();
        if (overduePayments > 0) {
            generated.add(NotificationResponse.operational(
                "overdue-payments",
                overduePayments + " booking" + (overduePayments == 1 ? "" : "s") + " have overdue payment balances",
                "Review overdue deposit and balance collections before departure deadlines pass.",
                "warning",
                LocalDateTime.now()
            ));
        }

        long refundsDue = bookings.stream()
            .map(booking -> paymentSummaries.getOrDefault(booking.getId(), paymentService.summarize(booking)))
            .filter(summary -> summary.refundableAmount().compareTo(BigDecimal.ZERO) > 0)
            .count();
        if (refundsDue > 0) {
            generated.add(NotificationResponse.operational(
                "refunds",
                refundsDue + " cancelled booking" + (refundsDue == 1 ? "" : "s") + " still have refund balance due",
                "Refund-eligible bookings are waiting for finance processing.",
                "info",
                LocalDateTime.now()
            ));
        }

        List<BookingActivity> recentActivity = bookingActivityService.recentActivityFeed().stream().limit(3).toList();
        recentActivity.forEach(activity -> generated.add(NotificationResponse.operational(
            "activity",
            activity.getActionType().replace('_', ' '),
            activity.getActorName() + " updated booking workflow: " + Optional.ofNullable(activity.getNote()).orElse("No note added."),
            "info",
            activity.getCreatedAt()
        )));

        generated.addAll(lowInventoryAlerts(toursById.values().stream().toList()));

        return java.util.stream.Stream.concat(generated.stream(), stored.stream())
            .sorted(Comparator.comparing(NotificationResponse::createdAt).reversed())
            .limit(12)
            .toList();
    }

    private List<NotificationResponse> lowInventoryAlerts(List<Tour> tours) {
        return tours.stream()
            .flatMap(tour -> bookingService.availabilityForTour(tour).values().stream()
                .filter(availability -> !availability.soldOut() && availability.remaining() <= 2)
                .map(availability -> NotificationResponse.operational(
                    "inventory",
                    "Low inventory for " + tour.getTitle(),
                    availability.remaining() + " seat" + (availability.remaining() == 1 ? "" : "s")
                        + " left on " + availability.date() + ".",
                    "warning",
                    LocalDateTime.now()
                ))
            )
            .limit(3)
            .toList();
    }

    private List<NotificationResponse> travelerAlertsForBooking(
        Booking booking,
        Map<String, Tour> toursById,
        PaymentSummary paymentSummary
    ) {
        Tour tour = Optional.ofNullable(toursById.get(booking.getTourId())).orElse(null);
        String title = tour == null ? booking.getTourId() : tour.getTitle();
        LocalDate today = LocalDate.now();
        long daysUntilDeparture = java.time.temporal.ChronoUnit.DAYS.between(today, booking.getDate());
        List<NotificationResponse> alerts = new java.util.ArrayList<>();

        if (booking.getStatus() == BookingStatus.CONFIRMED && daysUntilDeparture >= 0 && daysUntilDeparture <= 7) {
            alerts.add(NotificationResponse.operational(
                "trip-prep",
                "Upcoming trip to " + title,
                "Your departure is on " + booking.getDate() + ". Review your booking details and traveler preferences before you go.",
                "info",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() == BookingStatus.PENDING && booking.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            alerts.add(NotificationResponse.operational(
                "booking-review",
                "Booking still under review",
                "Your request for " + title + " is still being reviewed by the travel team. We will update you as soon as it is confirmed.",
                "warning",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() != BookingStatus.CANCELLED && paymentSummary.outstandingAmount().compareTo(BigDecimal.ZERO) > 0) {
            String subject = paymentSummary.overdue() ? "Payment overdue for " + title : "Payment due for " + title;
            String dueDate = paymentSummary.dueDate() == null ? "your dashboard" : paymentSummary.dueDate().toString();
            alerts.add(NotificationResponse.operational(
                "payment",
                subject,
                "Outstanding amount: " + paymentSummary.currency() + " " + paymentSummary.outstandingAmount()
                    + ". Use the booking actions menu to complete the payment by " + dueDate + ".",
                paymentSummary.overdue() ? "warning" : "info",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() == BookingStatus.CANCELLED && paymentSummary.refundableAmount().compareTo(BigDecimal.ZERO) > 0) {
            alerts.add(NotificationResponse.operational(
                "refund",
                "Refund is being prepared for " + title,
                "A refundable balance of " + paymentSummary.currency() + " " + paymentSummary.refundableAmount()
                    + " is available for this cancelled booking.",
                "info",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() != BookingStatus.CANCELLED && daysUntilDeparture >= 0 && daysUntilDeparture <= 30 && !hasText(booking.getMealPreference())) {
            alerts.add(NotificationResponse.operational(
                "preferences",
                "Add your meal preference for " + title,
                "Use the booking actions menu to save food choices, honeymoon or celebration details, room requests, or pickup needs before departure.",
                "info",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() != BookingStatus.CANCELLED
            && daysUntilDeparture >= 0
            && daysUntilDeparture <= 30
            && (hasText(booking.getOccasionType())
                || hasText(booking.getDietaryRestrictions())
                || hasText(booking.getRoomPreference())
                || hasText(booking.getTripStyle()))) {
            alerts.add(NotificationResponse.operational(
                "customization",
                "Your custom trip preferences are on file for " + title,
                "We saved your" + buildCustomizationSummary(booking)
                    + ". You can update them from the booking actions menu any time before departure.",
                "info",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() != BookingStatus.CANCELLED && booking.isTransferRequired() && daysUntilDeparture >= 0 && daysUntilDeparture <= 21) {
            alerts.add(NotificationResponse.operational(
                "transfer",
                "Pickup request saved for " + title,
                "Your airport or hotel transfer request is on file. Our team will confirm logistics before departure.",
                "info",
                LocalDateTime.now()
            ));
        }

        if (requiresTransportFollowUp(booking) && daysUntilDeparture >= 0 && daysUntilDeparture <= 14) {
            alerts.add(NotificationResponse.operational(
                "transport",
                "Transport booking still in progress for " + title,
                "Your " + booking.getTransportMode() + " request is still marked " + booking.getTransportStatus() + ". Our team is working on it.",
                "warning",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED
            && hasText(booking.getTransportMode())
            && "Confirmed".equalsIgnoreCase(booking.getTransportStatus())) {
            alerts.add(NotificationResponse.operational(
                "transport",
                booking.getTransportMode() + " arrangements confirmed",
                "Your " + booking.getTransportMode().toLowerCase() + " planning is confirmed for " + title + ".",
                "info",
                LocalDateTime.now()
            ));
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED
            && !booking.isDocumentsVerified()
            && daysUntilDeparture >= 0
            && daysUntilDeparture <= 21) {
            alerts.add(NotificationResponse.operational(
                "documents",
                "Travel documents still pending for " + title,
                "Please complete any passport or ID checks with the travel team before departure.",
                "warning",
                LocalDateTime.now()
            ));
        }

        return alerts;
    }

    private String inquiryPriority(Inquiry inquiry) {
        long ageHours = inquiryAgeHours(inquiry);
        if (inquiry.getTravelers() >= 6 || ageHours >= 48) {
            return "high";
        }
        if (inquiry.getTravelers() >= 4 || ageHours >= 12) {
            return "medium";
        }
        return "normal";
    }

    private long inquiryAgeHours(Inquiry inquiry) {
        return java.time.Duration.between(inquiry.getCreatedAt(), LocalDateTime.now()).toHours();
    }

    private int inquiryPriorityRank(Inquiry inquiry) {
        return switch (inquiryPriority(inquiry)) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private String customerSegment(int bookingCount, BigDecimal totalSpend) {
        if (bookingCount >= 4 || totalSpend.compareTo(new BigDecimal("10000")) >= 0) {
            return "VIP";
        }
        if (bookingCount >= 2 || totalSpend.compareTo(new BigDecimal("5000")) >= 0) {
            return "Loyal";
        }
        return "Growing";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean requiresTransportFollowUp(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED
            && hasText(booking.getTransportMode())
            && ("Flight".equals(booking.getTransportMode())
                || "Train".equals(booking.getTransportMode())
                || "Bus".equals(booking.getTransportMode()))
            && !"Confirmed".equalsIgnoreCase(booking.getTransportStatus());
    }

    private String buildCustomizationSummary(Booking booking) {
        List<String> parts = new java.util.ArrayList<>();
        if (hasText(booking.getOccasionType())) {
            parts.add(booking.getOccasionType().toLowerCase());
        }
        if (hasText(booking.getRoomPreference())) {
            parts.add("room preference " + booking.getRoomPreference().toLowerCase());
        }
        if (hasText(booking.getTripStyle())) {
            parts.add("trip style " + booking.getTripStyle().toLowerCase());
        }
        if (hasText(booking.getMealPreference())) {
            parts.add("meal choice " + booking.getMealPreference().toLowerCase());
        }
        if (hasText(booking.getDietaryRestrictions())) {
            parts.add("dietary notes");
        }
        return parts.isEmpty() ? " traveler preferences" : " " + String.join(", ", parts);
    }

    private List<Tour> personalizedRecommendations(AppUser actor, List<Booking> bookings, List<Tour> allTours, Map<String, Tour> toursById) {
        Set<String> bookedTourIds = bookings.stream().map(Booking::getTourId).collect(Collectors.toSet());
        Set<String> preferredCategories = new LinkedHashSet<>();

        bookings.stream()
            .map(Booking::getTourId)
            .map(toursById::get)
            .filter(java.util.Objects::nonNull)
            .map(Tour::getCategory)
            .forEach(preferredCategories::add);

        tourViewService.recentViews(actor.getId()).stream()
            .map(event -> toursById.get(event.getTourId()))
            .filter(java.util.Objects::nonNull)
            .map(Tour::getCategory)
            .forEach(preferredCategories::add);

        List<Tour> recommendations = allTours.stream()
            .filter(tour -> !bookedTourIds.contains(tour.getId()))
            .filter(tour -> preferredCategories.isEmpty() || preferredCategories.contains(tour.getCategory()))
            .sorted(Comparator.comparing(Tour::getRating).reversed())
            .limit(3)
            .toList();

        if (!recommendations.isEmpty()) {
            return recommendations;
        }
        return tourCatalogService.featuredTours(3);
    }
}
