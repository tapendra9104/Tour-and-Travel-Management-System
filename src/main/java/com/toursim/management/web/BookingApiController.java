package com.toursim.management.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingService;
import com.toursim.management.booking.BookingSubmissionResult;
import com.toursim.management.booking.dto.BookingActivityResponse;
import com.toursim.management.booking.dto.BookingLookupRequest;
import com.toursim.management.booking.dto.BookingPreferenceUpdateRequest;
import com.toursim.management.booking.dto.BookingRequest;
import com.toursim.management.booking.dto.BookingResponse;
import com.toursim.management.booking.dto.BookingSelfServiceRequest;
import com.toursim.management.booking.dto.BookingStatusUpdateRequest;
import com.toursim.management.booking.dto.BookingSubmissionResponse;
import com.toursim.management.payment.PaymentService;
import com.toursim.management.payment.dto.PaymentActionResponse;
import com.toursim.management.payment.dto.BookingPaymentRequest;
import com.toursim.management.tour.TourCatalogService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/bookings")
public class BookingApiController {

    private final BookingService bookingService;
    private final TourCatalogService tourCatalogService;
    private final AuthenticationFacade authenticationFacade;
    private final PaymentService paymentService;

    public BookingApiController(
        BookingService bookingService,
        TourCatalogService tourCatalogService,
        AuthenticationFacade authenticationFacade,
        PaymentService paymentService
    ) {
        this.bookingService = bookingService;
        this.tourCatalogService = tourCatalogService;
        this.authenticationFacade = authenticationFacade;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<BookingResponse> bookings() {
        return bookingService.findVisibleBookings(authenticationFacade.currentUser()).stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping
    public ResponseEntity<BookingSubmissionResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        BookingSubmissionResult result = bookingService.createBooking(request, authenticationFacade.currentUser());
        BookingSubmissionResponse response = new BookingSubmissionResponse(
            result.outcome(),
            result.message(),
            result.booking() == null ? null : toResponse(result.booking()),
            result.waitlistEntry() == null ? null : result.waitlistEntry().getWaitlistReference()
        );
        HttpStatus status = "waitlisted".equals(result.outcome()) ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/lookup")
    public BookingResponse lookupBooking(@Valid @RequestBody BookingLookupRequest request) {
        return bookingService.lookupBooking(request.bookingReference(), request.email())
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    @PostMapping("/{id}/activity")
    public List<BookingActivityResponse> travelerActivity(@PathVariable Long id, @Valid @RequestBody BookingLookupRequest request) {
        return bookingService.activityForTraveler(id, request.bookingReference(), request.email(), authenticationFacade.currentUser()).stream()
            .map(BookingActivityResponse::from)
            .toList();
    }

    @PostMapping("/{id}/payments")
    public PaymentActionResponse collectPayment(@PathVariable Long id, @Valid @RequestBody BookingPaymentRequest request) {
        return PaymentActionResponse.from(paymentService.collectTravelerPayment(id, request, authenticationFacade.currentUser()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BookingResponse updateStatus(@PathVariable Long id, @Valid @RequestBody BookingStatusUpdateRequest request) {
        AppUser actor = authenticationFacade.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "You must be signed in"));
        return toResponse(bookingService.updateStatus(id, request.status(), request.note(), actor));
    }

    @PostMapping("/{id}/preferences")
    public BookingResponse updateTravelerPreferences(@PathVariable Long id, @Valid @RequestBody BookingPreferenceUpdateRequest request) {
        return toResponse(bookingService.updateTravelerPreferences(id, request, authenticationFacade.currentUser()));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long id, @Valid @RequestBody BookingSelfServiceRequest request) {
        return toResponse(bookingService.cancelBooking(id, request, authenticationFacade.currentUser()));
    }

    @PostMapping("/{id}/reschedule")
    public BookingResponse rescheduleBooking(@PathVariable Long id, @Valid @RequestBody BookingSelfServiceRequest request) {
        return toResponse(bookingService.rescheduleBooking(id, request, authenticationFacade.currentUser()));
    }

    private BookingResponse toResponse(Booking booking) {
        String tourTitle = tourCatalogService.findById(booking.getTourId())
            .map(com.toursim.management.tour.Tour::getTitle)
            .orElse(booking.getBookingReference());
        return BookingResponse.from(booking, tourTitle, paymentService.summarize(booking));
    }
}
