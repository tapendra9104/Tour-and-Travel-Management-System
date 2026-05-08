package com.toursim.management.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingService;
import com.toursim.management.booking.dto.AdminReminderRequest;
import com.toursim.management.booking.dto.BookingActivityResponse;
import com.toursim.management.booking.dto.BookingOperationsUpdateRequest;
import com.toursim.management.booking.dto.BookingResponse;
import com.toursim.management.booking.dto.BookingStatusUpdateRequest;
import com.toursim.management.inquiry.InquiryResponse;
import com.toursim.management.inquiry.InquiryService;
import com.toursim.management.inquiry.InquiryStatusUpdateRequest;
import com.toursim.management.payment.PaymentService;
import com.toursim.management.payment.PaymentSummary;
import com.toursim.management.payment.PaymentTransaction;
import com.toursim.management.payment.dto.AdminPaymentRequest;
import com.toursim.management.payment.dto.AdminRefundRequest;
import com.toursim.management.payment.dto.PaymentActionResponse;
import com.toursim.management.newsletter.NewsletterSubscriberRepository;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;
import com.toursim.management.tour.dto.TourAdminRequest;
import com.toursim.management.waitlist.WaitlistActionRequest;
import com.toursim.management.waitlist.WaitlistEntry;
import com.toursim.management.waitlist.WaitlistEntryResponse;
import com.toursim.management.waitlist.WaitlistService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiController {

    private final InquiryService inquiryService;
    private final BookingService bookingService;
    private final TourCatalogService tourCatalogService;
    private final WaitlistService waitlistService;
    private final AuthenticationFacade authenticationFacade;
    private final PaymentService paymentService;
    private final NewsletterSubscriberRepository newsletterRepository;

    public AdminApiController(
        InquiryService inquiryService,
        BookingService bookingService,
        TourCatalogService tourCatalogService,
        WaitlistService waitlistService,
        AuthenticationFacade authenticationFacade,
        PaymentService paymentService,
        NewsletterSubscriberRepository newsletterRepository
    ) {
        this.inquiryService = inquiryService;
        this.bookingService = bookingService;
        this.tourCatalogService = tourCatalogService;
        this.waitlistService = waitlistService;
        this.authenticationFacade = authenticationFacade;
        this.paymentService = paymentService;
        this.newsletterRepository = newsletterRepository;
    }

    // --- Stats overview -------------------------------------------------------

    @GetMapping("/stats")
    public java.util.Map<String, Object> stats() {
        java.util.List<com.toursim.management.booking.Booking> allBookings = bookingService.findAll();
        long totalRevenue = allBookings.stream()
            .filter(b -> b.getStatus() != com.toursim.management.booking.BookingStatus.CANCELLED)
            .mapToLong(b -> b.getTotalPrice() == null ? 0 : b.getTotalPrice().longValue())
            .sum();
        return java.util.Map.of(
            "totalBookings",     allBookings.size(),
            "pendingBookings",   allBookings.stream().filter(b -> b.getStatus() == com.toursim.management.booking.BookingStatus.PENDING).count(),
            "confirmedBookings", allBookings.stream().filter(b -> b.getStatus() == com.toursim.management.booking.BookingStatus.CONFIRMED).count(),
            "cancelledBookings", allBookings.stream().filter(b -> b.getStatus() == com.toursim.management.booking.BookingStatus.CANCELLED).count(),
            "totalRevenue",      totalRevenue,
            "openInquiries",     inquiryService.findAll().stream().filter(i -> i.getStatus().name().equals("NEW") || i.getStatus().name().equals("IN_PROGRESS")).count(),
            "waitlistEntries",   waitlistService.recentActiveEntries().size(),
            "newsletterCount",   newsletterRepository.count(),
            "totalTours",        tourCatalogService.findAll().size()
        );
    }

    // --- Newsletter subscribers -----------------------------------------------

    @GetMapping("/newsletter/subscribers")
    public java.util.List<java.util.Map<String, Object>> newsletterSubscribers() {
        return newsletterRepository.findAll().stream()
            .map(s -> java.util.Map.<String, Object>of(
                "id",           s.getId(),
                "email",        s.getEmail(),
                "subscribedAt", s.getSubscribedAt() != null ? s.getSubscribedAt().toString() : "",
                "active",       s.isActive()
            ))
            .toList();
    }

    @GetMapping("/newsletter/subscribers/export")
    public ResponseEntity<byte[]> exportNewsletterSubscribers() {
        StringBuilder csv = new StringBuilder("Email,SubscribedAt,Active\n");
        newsletterRepository.findAll().forEach(subscriber -> csv.append(escape(subscriber.getEmail())).append(',')
            .append(escape(String.valueOf(subscriber.getSubscribedAt()))).append(',')
            .append(subscriber.isActive())
            .append('\n'));

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=newsletter-subscribers-export.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    // --- All bookings (admin) -------------------------------------------------

    @GetMapping("/bookings")
    public java.util.List<BookingResponse> allBookings() {
        java.util.Map<String, String> tourTitles = tourCatalogService.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(
                com.toursim.management.tour.Tour::getId,
                com.toursim.management.tour.Tour::getTitle));
        return bookingService.findAll().stream()
            .map(b -> BookingResponse.from(b,
                tourTitles.getOrDefault(b.getTourId(), b.getTourId()),
                paymentService.summarize(b)))
            .toList();
    }

    @PatchMapping("/bookings/{id}/status")
    public BookingResponse updateBookingStatus(
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest request) {
        return toResponse(bookingService.updateStatus(id, request.status(), request.note(), requireAdmin()));
    }

    // --- Inquiries ------------------------------------------------------------

    @GetMapping("/inquiries")
    public java.util.List<InquiryResponse> allInquiries() {
        return inquiryService.findAll().stream().map(InquiryResponse::from).toList();
    }

    @PatchMapping("/inquiries/{id}")
    public InquiryResponse updateInquiry(@PathVariable Long id, @Valid @RequestBody InquiryStatusUpdateRequest request) {
        return InquiryResponse.from(inquiryService.updateStatus(id, request.status(), request.adminNotes()));
    }

    @GetMapping("/bookings/{id}/activity")
    public List<BookingActivityResponse> bookingActivity(@PathVariable Long id) {
        return bookingService.activityForBooking(id, requireAdmin()).stream()
            .map(BookingActivityResponse::from)
            .toList();
    }

    @PostMapping("/bookings/{id}/reminder")
    public ResponseEntity<Void> sendBookingReminder(@PathVariable Long id, @Valid @RequestBody AdminReminderRequest request) {
        bookingService.sendReminder(id, request.message(), requireAdmin());
        return ResponseEntity.accepted().build();
    }

    @PatchMapping("/bookings/{id}/operations")
    public BookingResponse updateBookingOperations(@PathVariable Long id, @Valid @RequestBody BookingOperationsUpdateRequest request) {
        return toResponse(bookingService.updateOperations(id, request, requireAdmin()));
    }

    @PostMapping("/bookings/{id}/payments")
    public PaymentActionResponse recordPayment(@PathVariable Long id, @Valid @RequestBody AdminPaymentRequest request) {
        return PaymentActionResponse.from(paymentService.collectAdminPayment(id, request, requireAdmin()));
    }

    @PostMapping("/bookings/{id}/refund")
    public PaymentActionResponse refundPayment(@PathVariable Long id, @Valid @RequestBody AdminRefundRequest request) {
        return PaymentActionResponse.from(paymentService.refundPayment(id, request, requireAdmin()));
    }

    @GetMapping("/bookings/export")
    public ResponseEntity<byte[]> exportBookings() {
        List<Booking> bookings = bookingService.findAll();
        // Pre-load all tour titles in one query - eliminates N+1 (one SELECT per booking)
        java.util.Map<String, String> tourTitles = tourCatalogService.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(Tour::getId, Tour::getTitle));
        java.util.Map<Long, PaymentSummary> paymentSummaries = paymentService.summarize(bookings);
        StringBuilder csv = new StringBuilder("Reference,Tour,Customer,Email,Phone,TravelDate,Travelers,Total,Status,Reason,PaymentStatus,PaidAmount,OutstandingAmount,DueNowAmount,DueDate,RefundableAmount,TransportMode,TransportClass,TransportStatus,DocumentsVerified,Priority,MealPreference,DietaryRestrictions,OccasionType,OccasionNotes,RoomPreference,TripStyle,TransferRequired,AssistanceNotes,TravelerNotes,OperationsNotes\n");
        for (Booking booking : bookings) {
            String tourTitle = tourTitles.getOrDefault(booking.getTourId(), booking.getTourId());
            PaymentSummary paymentSummary = paymentSummaries.get(booking.getId());
            csv.append(escape(booking.getBookingReference())).append(',')
                .append(escape(tourTitle)).append(',')
                .append(escape(booking.getCustomerName())).append(',')
                .append(escape(booking.getEmail())).append(',')
                .append(escape(booking.getPhone())).append(',')
                .append(escape(String.valueOf(booking.getDate()))).append(',')
                .append(booking.getGuests()).append(',')
                .append(booking.getTotalPrice()).append(',')
                .append(booking.getStatus()).append(',')
                .append(escape(booking.getStatusReason())).append(',')
                .append(escape(paymentSummary.status().label())).append(',')
                .append(paymentSummary.paidAmount()).append(',')
                .append(paymentSummary.outstandingAmount()).append(',')
                .append(paymentSummary.dueNowAmount()).append(',')
                .append(escape(String.valueOf(paymentSummary.dueDate()))).append(',')
                .append(paymentSummary.refundableAmount()).append(',')
                .append(escape(booking.getTransportMode())).append(',')
                .append(escape(booking.getTransportClass())).append(',')
                .append(escape(booking.getTransportStatus())).append(',')
                .append(booking.isDocumentsVerified()).append(',')
                .append(escape(booking.getOperationsPriority())).append(',')
                .append(escape(booking.getMealPreference())).append(',')
                .append(escape(booking.getDietaryRestrictions())).append(',')
                .append(escape(booking.getOccasionType())).append(',')
                .append(escape(booking.getOccasionNotes())).append(',')
                .append(escape(booking.getRoomPreference())).append(',')
                .append(escape(booking.getTripStyle())).append(',')
                .append(booking.isTransferRequired()).append(',')
                .append(escape(booking.getAssistanceNotes())).append(',')
                .append(escape(booking.getTravelerNotes())).append(',')
                .append(escape(booking.getOperationsNotes()))
                .append('\n');
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bookings-export.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/bookings/operations-export")
    public ResponseEntity<byte[]> exportOperations() {
        List<Booking> bookings = bookingService.findAll();
        // Pre-load all tour titles in one query
        java.util.Map<String, String> tourTitlesOps = tourCatalogService.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(Tour::getId, Tour::getTitle));
        java.util.Map<Long, PaymentSummary> paymentSummaries = paymentService.summarize(bookings);
        StringBuilder csv = new StringBuilder("Reference,Tour,Customer,TravelDate,Travelers,PaymentStatus,DueNowAmount,DueDate,RefundableAmount,TransportMode,TransportClass,TransportStatus,DocumentsVerified,Priority,MealPreference,DietaryRestrictions,OccasionType,OccasionNotes,RoomPreference,TripStyle,TransferRequired,AssistanceNotes,TravelerNotes,OperationsNotes\n");
        for (Booking booking : bookings) {
            String tourTitle = tourTitlesOps.getOrDefault(booking.getTourId(), booking.getTourId());
            PaymentSummary paymentSummary = paymentSummaries.get(booking.getId());
            csv.append(escape(booking.getBookingReference())).append(',')
                .append(escape(tourTitle)).append(',')
                .append(escape(booking.getCustomerName())).append(',')
                .append(escape(String.valueOf(booking.getDate()))).append(',')
                .append(booking.getGuests()).append(',')
                .append(escape(paymentSummary.status().label())).append(',')
                .append(paymentSummary.dueNowAmount()).append(',')
                .append(escape(String.valueOf(paymentSummary.dueDate()))).append(',')
                .append(paymentSummary.refundableAmount()).append(',')
                .append(escape(booking.getTransportMode())).append(',')
                .append(escape(booking.getTransportClass())).append(',')
                .append(escape(booking.getTransportStatus())).append(',')
                .append(booking.isDocumentsVerified()).append(',')
                .append(escape(booking.getOperationsPriority())).append(',')
                .append(escape(booking.getMealPreference())).append(',')
                .append(escape(booking.getDietaryRestrictions())).append(',')
                .append(escape(booking.getOccasionType())).append(',')
                .append(escape(booking.getOccasionNotes())).append(',')
                .append(escape(booking.getRoomPreference())).append(',')
                .append(escape(booking.getTripStyle())).append(',')
                .append(booking.isTransferRequired()).append(',')
                .append(escape(booking.getAssistanceNotes())).append(',')
                .append(escape(booking.getTravelerNotes())).append(',')
                .append(escape(booking.getOperationsNotes()))
                .append('\n');
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operations-export.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/inquiries/export")
    public ResponseEntity<byte[]> exportInquiries() {
        StringBuilder csv = new StringBuilder("Customer,Email,Phone,Destination,TravelWindow,Travelers,Status,CreatedAt,Message\n");
        inquiryService.findAll().forEach(inquiry -> csv.append(escape(inquiry.getCustomerName())).append(',')
            .append(escape(inquiry.getEmail())).append(',')
            .append(escape(inquiry.getPhone())).append(',')
            .append(escape(inquiry.getDestination())).append(',')
            .append(escape(inquiry.getTravelWindow())).append(',')
            .append(inquiry.getTravelers()).append(',')
            .append(inquiry.getStatus()).append(',')
            .append(escape(String.valueOf(inquiry.getCreatedAt()))).append(',')
            .append(escape(inquiry.getMessage()))
            .append('\n'));

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inquiries-export.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/waitlist")
    public List<WaitlistEntryResponse> waitlist() {
        return waitlistService.recentActiveEntries().stream()
            .map(this::toWaitlistResponse)
            .toList();
    }

    @PatchMapping("/waitlist/{id}")
    public WaitlistEntryResponse updateWaitlist(@PathVariable Long id, @Valid @RequestBody WaitlistActionRequest request) {
        WaitlistEntry existing = waitlistService.requireEntry(id);
        Tour tour = tourCatalogService.findById(existing.getTourId())
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Tour not found"));
        return toWaitlistResponse(waitlistService.updateStatus(tour, id, request.status(), request.message()));
    }

    @GetMapping("/waitlist/export")
    public ResponseEntity<byte[]> exportWaitlist() {
        StringBuilder csv = new StringBuilder("Reference,Tour,Customer,Email,Phone,TravelDate,Travelers,Status,CreatedAt\n");
        waitlistService.activeEntries().forEach(entry -> {
            String tourTitle = tourCatalogService.findById(entry.getTourId()).map(Tour::getTitle).orElse(entry.getTourId());
            csv.append(escape(entry.getWaitlistReference())).append(',')
                .append(escape(tourTitle)).append(',')
                .append(escape(entry.getCustomerName())).append(',')
                .append(escape(entry.getEmail())).append(',')
                .append(escape(entry.getPhone())).append(',')
                .append(escape(String.valueOf(entry.getTravelDate()))).append(',')
                .append(entry.getGuests()).append(',')
                .append(entry.getStatus()).append(',')
                .append(escape(String.valueOf(entry.getCreatedAt())))
                .append('\n');
        });

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=waitlist-export.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/bookings/payments-export")
    public ResponseEntity<byte[]> exportPayments() {
        List<Booking> bookings = bookingService.findAll();
        java.util.Map<Long, Booking> bookingsById = bookings.stream().collect(java.util.stream.Collectors.toMap(Booking::getId, java.util.function.Function.identity()));
        java.util.Map<String, String> tourTitles = tourCatalogService.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(Tour::getId, Tour::getTitle));
        StringBuilder csv = new StringBuilder("TransactionReference,ReceiptNumber,BookingReference,Tour,Customer,Method,Stage,Amount,Currency,Provider,ProviderReference,CreatedAt,Actor,ActorRole,Note\n");
        for (PaymentTransaction transaction : paymentService.findAllTransactions()) {
            Booking booking = bookingsById.get(transaction.getBookingId());
            if (booking == null) {
                continue;
            }
            String tourTitle = tourTitles.getOrDefault(booking.getTourId(), booking.getTourId());
            csv.append(escape(transaction.getTransactionReference())).append(',')
                .append(escape(transaction.getReceiptNumber())).append(',')
                .append(escape(booking.getBookingReference())).append(',')
                .append(escape(tourTitle)).append(',')
                .append(escape(booking.getCustomerName())).append(',')
                .append(escape(transaction.getMethod().label())).append(',')
                .append(escape(transaction.getStage().label())).append(',')
                .append(transaction.getAmount()).append(',')
                .append(escape(transaction.getCurrency())).append(',')
                .append(escape(transaction.getProviderName())).append(',')
                .append(escape(transaction.getProviderReference())).append(',')
                .append(escape(String.valueOf(transaction.getCreatedAt()))).append(',')
                .append(escape(transaction.getActorName())).append(',')
                .append(escape(transaction.getActorRole())).append(',')
                .append(escape(transaction.getNote()))
                .append('\n');
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments-export.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/tours")
    public List<Tour> tours() {
        return tourCatalogService.findAll();
    }

    @PostMapping("/tours")
    public Tour createTour(@Valid @RequestBody TourAdminRequest request) {
        return tourCatalogService.upsert(request);
    }

    @PutMapping("/tours/{id}")
    public Tour updateTour(@PathVariable String id, @Valid @RequestBody TourAdminRequest request) {
        TourAdminRequest resolvedRequest = new TourAdminRequest(
            id,
            request.title(),
            request.destination(),
            request.country(),
            request.duration(),
            request.price(),
            request.originalPrice(),
            request.rating(),
            request.reviews(),
            request.image(),
            request.category(),
            request.description(),
            request.difficulty(),
            request.maxGroupSize(),
            request.highlights(),
            request.included(),
            request.startDates()
        );
        return tourCatalogService.upsert(resolvedRequest);
    }

    @DeleteMapping("/tours/{id}")
    public ResponseEntity<Void> deleteTour(@PathVariable String id) {
        tourCatalogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AppUser requireAdmin() {
        return authenticationFacade.currentUser()
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "You must be signed in"));
    }

    private WaitlistEntryResponse toWaitlistResponse(WaitlistEntry entry) {
        String tourTitle = tourCatalogService.findById(entry.getTourId()).map(Tour::getTitle).orElse(entry.getTourId());
        String priority = entry.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusHours(24)) ? "high" : "normal";
        return WaitlistEntryResponse.from(entry, tourTitle, priority);
    }

    private BookingResponse toResponse(Booking booking) {
        String tourTitle = tourCatalogService.findById(booking.getTourId())
            .map(Tour::getTitle)
            .orElse(booking.getBookingReference());
        return BookingResponse.from(booking, tourTitle, paymentService.summarize(booking));
    }

    private String escape(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
