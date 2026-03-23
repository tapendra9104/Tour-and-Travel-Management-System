package com.toursim.management.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.UserRole;
import com.toursim.management.booking.dto.BookingOperationsUpdateRequest;
import com.toursim.management.booking.dto.BookingRequest;
import com.toursim.management.booking.dto.BookingPreferenceUpdateRequest;
import com.toursim.management.booking.dto.BookingSelfServiceRequest;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourAvailability;
import com.toursim.management.tour.TourCatalogService;
import com.toursim.management.waitlist.WaitlistEntry;
import com.toursim.management.waitlist.WaitlistService;

@Service
public class BookingService {

    private static final BigDecimal SERVICE_FEE_RATE = new BigDecimal("0.05");
    private static final String TRANSPORT_STATUS_NOT_REQUIRED = "Not Required";
    private static final String TRANSPORT_STATUS_REQUESTED = "Requested";
    private static final String TRANSPORT_STATUS_QUOTED = "Quoted";
    private static final String TRANSPORT_STATUS_CONFIRMED = "Confirmed";

    private final BookingRepository bookingRepository;
    private final TourCatalogService tourCatalogService;
    private final WaitlistService waitlistService;
    private final BookingActivityService bookingActivityService;
    private final NotificationService notificationService;
    private final AppUserService appUserService;

    public BookingService(
        BookingRepository bookingRepository,
        TourCatalogService tourCatalogService,
        WaitlistService waitlistService,
        BookingActivityService bookingActivityService,
        NotificationService notificationService,
        AppUserService appUserService
    ) {
        this.bookingRepository = bookingRepository;
        this.tourCatalogService = tourCatalogService;
        this.waitlistService = waitlistService;
        this.bookingActivityService = bookingActivityService;
        this.notificationService = notificationService;
        this.appUserService = appUserService;
    }

    @Transactional(readOnly = true)
    public List<Booking> findAll() {
        return bookingRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Booking> findVisibleBookings(Optional<AppUser> actor) {
        if (actor.isEmpty()) {
            return List.of();
        }
        if (actor.get().getRole() == UserRole.ADMIN) {
            return findAll();
        }
        return bookingRepository.findVisibleToUser(actor.get().getId(), actor.get().getEmail());
    }

    @Transactional(readOnly = true)
    public Optional<Booking> lookupBooking(String bookingReference, String email) {
        return bookingRepository.findByBookingReferenceAndEmailIgnoreCase(normalizeReference(bookingReference), normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, TourAvailability> availabilityForTour(Tour tour) {
        Map<LocalDate, TourAvailability> availability = new LinkedHashMap<>();
        for (LocalDate startDate : tour.getStartDates()) {
            int booked = bookingRepository.totalGuestsForDeparture(tour.getId(), startDate);
            int remaining = Math.max(0, tour.getMaxGroupSize() - booked);
            availability.put(startDate, new TourAvailability(startDate, tour.getMaxGroupSize(), booked, remaining, remaining == 0));
        }
        return availability;
    }

    @Transactional
    public BookingSubmissionResult createBooking(BookingRequest request, Optional<AppUser> actor) {
        Tour tour = requireTour(request.tourId());
        BookingRequestContext requestContext = toRequestContext(request, actor);

        if (requestContext.guests() > tour.getMaxGroupSize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest count exceeds the tour group size");
        }

        if (!tour.getStartDates().contains(requestContext.date())) {
            recordBookingFailure(tour, requestContext, "Selected travel date is not available");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected travel date is not available");
        }

        int bookedSeats = bookingRepository.totalGuestsForDeparture(tour.getId(), requestContext.date());
        int remainingSeats = Math.max(0, tour.getMaxGroupSize() - bookedSeats);

        if (requestContext.guests() > remainingSeats) {
            WaitlistEntry waitlistEntry = waitlistService.createWaitlistEntry(tour, requestContext, actor);
            return new BookingSubmissionResult(
                "waitlisted",
                "This departure is full, so we saved your place on the waitlist.",
                null,
                waitlistEntry
            );
        }

        BigDecimal subtotal = tour.getPrice().multiply(BigDecimal.valueOf(requestContext.guests()));
        BigDecimal serviceFee = subtotal.multiply(SERVICE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(serviceFee);

        Booking booking = new Booking();
        booking.setTourId(tour.getId());
        booking.setUserId(actor.map(AppUser::getId).orElse(null));
        booking.setCustomerName(requestContext.customerName());
        booking.setEmail(requestContext.email());
        booking.setPhone(requestContext.phone());
        booking.setGuests(requestContext.guests());
        booking.setDate(requestContext.date());
        booking.setServiceFee(serviceFee);
        booking.setTotalPrice(total);
        applyTravelerPreferences(
            booking,
            request.mealPreference(),
            request.dietaryRestrictions(),
            request.occasionType(),
            request.occasionNotes(),
            request.roomPreference(),
            request.tripStyle(),
            request.transferRequired(),
            request.assistanceNotes(),
            request.travelerNotes(),
            request.transportMode(),
            request.transportClass()
        );
        booking.setDocumentsVerified(false);
        booking.setOperationsPriority(defaultOperationsPriority(booking, total));
        booking.setStatus(BookingStatus.PENDING);
        booking.setStatusReason("Awaiting travel team confirmation");
        booking = bookingRepository.save(booking);

        actor.ifPresent(appUser -> appUserService.syncProfile(appUser, requestContext.customerName(), requestContext.phone()));

        bookingActivityService.record(
            booking,
            actor.map(AppUser::getId).orElse(null),
            actor.map(AppUser::getFullName).orElse(requestContext.customerName()),
            actor.map(appUser -> appUser.getRole().name()).orElse("GUEST"),
            "BOOKING_CREATED",
            null,
            booking.getStatus(),
            bookingCreatedActivityNote(booking)
        );

        String customerMessage = "Hi " + booking.getCustomerName() + ",\n\n"
            + "We received your booking request for " + tour.getTitle() + " on " + booking.getDate() + ". "
            + "Your booking reference is " + booking.getBookingReference() + ". "
            + "Our travel team will confirm the request shortly.";
        if (hasTravelerPreferences(booking)) {
            customerMessage += "\n\nTraveler preferences saved: " + travelerPreferenceSummary(booking) + ".";
        }
        customerMessage += "\n\nWanderlust Travels";

        if (actor.isPresent()) {
            notificationService.notifyUser(actor.get(), NotificationCategory.BOOKING_RECEIVED, "Booking request received", customerMessage, booking.getId(), null);
        } else {
            notificationService.notifyGuest(booking.getEmail(), booking.getCustomerName(), NotificationCategory.BOOKING_RECEIVED, "Booking request received", customerMessage, booking.getId(), null);
        }

        notificationService.notifyAdmins(
            NotificationCategory.ADMIN_ALERT,
            "New booking request",
            booking.getCustomerName() + " booked " + tour.getTitle() + " for " + booking.getDate() + "."
                + (hasTravelerPreferences(booking) ? " " + travelerPreferenceSummary(booking) + "." : ""),
            booking.getId(),
            null
        );

        return new BookingSubmissionResult(
            "booked",
            "Your booking request was received. We will confirm it shortly.",
            booking,
            null
        );
    }

    @Transactional
    public Booking updateStatus(Long id, BookingStatus status, String note, AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can update booking status");
        }

        Booking booking = requireBooking(id);
        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(status);
        booking.setStatusReason(cleanNote(note, defaultStatusReason(status)));
        booking = bookingRepository.save(booking);

        bookingActivityService.record(
            booking,
            actor.getId(),
            actor.getFullName(),
            actor.getRole().name(),
            "BOOKING_STATUS_UPDATED",
            previousStatus,
            status,
            booking.getStatusReason()
        );

        notifyBookingStatusChange(booking);
        notifyWaitlistIfSeatOpened(booking, previousStatus);
        return booking;
    }

    @Transactional(readOnly = true)
    public List<BookingActivity> activityForBooking(Long id, AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can view booking activity");
        }
        requireBooking(id);
        return bookingActivityService.recentForBooking(id);
    }

    @Transactional(readOnly = true)
    public List<BookingActivity> activityForTraveler(Long id, String bookingReference, String email, Optional<AppUser> actor) {
        requireAuthorizedBooking(id, bookingReference, email, actor);
        return bookingActivityService.recentForBooking(id);
    }

    @Transactional
    public void sendReminder(Long id, String message, AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can send reminders");
        }

        Booking booking = requireBooking(id);
        Tour tour = requireTour(booking.getTourId());
        String body = message == null || message.isBlank()
            ? "Hi " + booking.getCustomerName() + ",\n\nThis is a reminder that your booking "
                + booking.getBookingReference() + " for " + tour.getTitle() + " on " + booking.getDate()
                + " is currently " + booking.getStatus().name().toLowerCase() + ". "
                + "If you need any help, please reply to this message or contact Wanderlust support.\n\nWanderlust Travels"
            : message.trim();

        notifyBookingRecipient(
            booking,
            NotificationCategory.BOOKING_STATUS_CHANGED,
            "Booking reminder for " + tour.getTitle(),
            body
        );

        bookingActivityService.record(
            booking,
            actor.getId(),
            actor.getFullName(),
            actor.getRole().name(),
            "BOOKING_REMINDER_SENT",
            booking.getStatus(),
            booking.getStatus(),
            body
        );
    }

    @Transactional(readOnly = true)
    public List<Booking> stalePendingBookings(int olderThanHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(olderThanHours);
        return bookingRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(booking -> booking.getStatus() == BookingStatus.PENDING)
            .filter(booking -> booking.getCreatedAt().isBefore(cutoff))
            .toList();
    }

    @Transactional
    public Booking updateTravelerPreferences(Long id, BookingPreferenceUpdateRequest request, Optional<AppUser> actor) {
        Booking booking = requireAuthorizedBooking(id, request.bookingReference(), request.email(), actor);
        mergeTravelerPreferences(booking, request);
        booking = bookingRepository.save(booking);

        String summary = activitySafeNote(travelerPreferenceSummary(booking));
        bookingActivityService.record(
            booking,
            actor.map(AppUser::getId).orElse(null),
            actor.map(AppUser::getFullName).orElse(booking.getCustomerName()),
            actor.map(appUser -> appUser.getRole().name()).orElse("GUEST"),
            "TRAVELER_PREFERENCES_UPDATED",
            booking.getStatus(),
            booking.getStatus(),
            summary
        );

        String subject = "Traveler preferences saved for " + requireTour(booking.getTourId()).getTitle();
        String message = "Hi " + booking.getCustomerName() + ",\n\nWe saved your traveler preferences for booking "
            + booking.getBookingReference() + ". "
            + (summary == null ? "You can update them again any time before departure." : summary + ".")
            + "\n\nWanderlust Travels";

        if (actor.isPresent()) {
            notificationService.notifyUser(
                actor.get(),
                NotificationCategory.TRAVELER_PREFERENCES_UPDATED,
                subject,
                message,
                booking.getId(),
                null
            );
        } else {
            notificationService.notifyGuest(
                booking.getEmail(),
                booking.getCustomerName(),
                NotificationCategory.TRAVELER_PREFERENCES_UPDATED,
                subject,
                message,
                booking.getId(),
                null
            );
        }

        if (booking.isTransferRequired()
            || hasText(booking.getAssistanceNotes())
            || requiresTransportArrangement(booking.getTransportMode())
            || hasConciergePreference(booking)) {
            notificationService.notifyAdmins(
                NotificationCategory.ADMIN_ALERT,
                "Traveler logistics updated",
                booking.getCustomerName() + " updated travel support preferences for booking "
                    + booking.getBookingReference() + ". " + (summary == null ? "" : summary + "."),
                booking.getId(),
                null
            );
        }

        return booking;
    }

    @Transactional
    public Booking updateOperations(Long id, BookingOperationsUpdateRequest request, AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can update booking operations");
        }

        Booking booking = requireBooking(id);
        String previousTransportStatus = booking.getTransportStatus();
        boolean previousDocumentsVerified = booking.isDocumentsVerified();

        String normalizedTransportMode = normalizeTransportMode(request.transportMode(), booking.getTransportMode());
        booking.setTransportMode(normalizedTransportMode);
        booking.setTransportClass(normalizeTransportClass(request.transportClass(), normalizedTransportMode));
        booking.setTransportStatus(resolveAdminTransportStatus(request.transportStatus(), normalizedTransportMode, booking.getTransportStatus()));
        booking.setDocumentsVerified(request.documentsVerified() != null ? request.documentsVerified() : booking.isDocumentsVerified());
        booking.setOperationsPriority(normalizeOperationsPriority(request.operationsPriority(), booking.getOperationsPriority()));
        booking.setOperationsNotes(cleanOptionalNote(request.operationsNotes()));
        booking = bookingRepository.save(booking);

        String operationsSummary = activitySafeNote(adminOperationsSummary(booking));
        bookingActivityService.record(
            booking,
            actor.getId(),
            actor.getFullName(),
            actor.getRole().name(),
            "BOOKING_OPERATIONS_UPDATED",
            booking.getStatus(),
            booking.getStatus(),
            operationsSummary
        );

        if (transportStatusChanged(previousTransportStatus, booking.getTransportStatus())
            || previousDocumentsVerified != booking.isDocumentsVerified()) {
            Tour tour = requireTour(booking.getTourId());
            String subject = "Travel operations updated for " + tour.getTitle();
            String message = "Hi " + booking.getCustomerName() + ",\n\nYour transport and trip-readiness details were updated for booking "
                + booking.getBookingReference() + ". "
                + travelerOperationsMessage(booking) + "\n\nWanderlust Travels";
            notifyBookingRecipient(booking, NotificationCategory.TRIP_REMINDER, subject, message);
        }

        return booking;
    }

    @Transactional
    public Booking cancelBooking(Long id, BookingSelfServiceRequest request, Optional<AppUser> actor) {
        Booking booking = requireAuthorizedBooking(id, request.bookingReference(), request.email(), actor);
        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setStatusReason(cleanNote(request.note(), "Cancelled by traveler"));
        booking = bookingRepository.save(booking);

        bookingActivityService.record(
            booking,
            actor.map(AppUser::getId).orElse(null),
            actor.map(AppUser::getFullName).orElse(booking.getCustomerName()),
            actor.map(appUser -> appUser.getRole().name()).orElse("GUEST"),
            "BOOKING_CANCELLED",
            previousStatus,
            booking.getStatus(),
            booking.getStatusReason()
        );

        notifyBookingStatusChange(booking);
        notifyWaitlistIfSeatOpened(booking, previousStatus);
        return booking;
    }

    @Transactional
    public Booking rescheduleBooking(Long id, BookingSelfServiceRequest request, Optional<AppUser> actor) {
        if (request.date() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A new travel date is required");
        }

        Booking booking = requireAuthorizedBooking(id, request.bookingReference(), request.email(), actor);
        Tour tour = requireTour(booking.getTourId());
        LocalDate previousDate = booking.getDate();

        if (!tour.getStartDates().contains(request.date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected travel date is not available");
        }

        if (!request.date().equals(previousDate)) {
            int bookedSeats = bookingRepository.totalGuestsForDeparture(tour.getId(), request.date());
            int remainingSeats = Math.max(0, tour.getMaxGroupSize() - bookedSeats);
            if (booking.getGuests() > remainingSeats) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected date is already full");
            }
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setDate(request.date());
        booking.setStatus(BookingStatus.PENDING);
        booking.setStatusReason(cleanNote(request.note(), "Reschedule requested"));
        booking = bookingRepository.save(booking);

        bookingActivityService.record(
            booking,
            actor.map(AppUser::getId).orElse(null),
            actor.map(AppUser::getFullName).orElse(booking.getCustomerName()),
            actor.map(appUser -> appUser.getRole().name()).orElse("GUEST"),
            "BOOKING_RESCHEDULED",
            previousStatus,
            booking.getStatus(),
            "Moved from " + previousDate + " to " + booking.getDate() + ". " + booking.getStatusReason()
        );

        notifyBookingStatusChange(booking);
        if (!previousDate.equals(booking.getDate())) {
            waitlistService.notifyNextEligibleTraveler(tour, previousDate);
        }
        return booking;
    }

    private Booking requireAuthorizedBooking(Long id, String bookingReference, String email, Optional<AppUser> actor) {
        Booking booking = requireBooking(id);
        if (actor.isPresent()) {
            AppUser appUser = actor.get();
            if (appUser.getRole() == UserRole.ADMIN) {
                return booking;
            }
            boolean ownsBooking = (booking.getUserId() != null && booking.getUserId().equals(appUser.getId()))
                || booking.getEmail().equalsIgnoreCase(appUser.getEmail());
            if (ownsBooking) {
                return booking;
            }
        }

        boolean guestAuthorized = booking.getBookingReference().equalsIgnoreCase(normalizeReference(bookingReference))
            && booking.getEmail().equalsIgnoreCase(normalizeEmail(email));
        if (!guestAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this booking");
        }
        return booking;
    }

    private void notifyBookingStatusChange(Booking booking) {
        Tour tour = requireTour(booking.getTourId());
        String subject = "Booking update for " + tour.getTitle();
        String message = "Hi " + booking.getCustomerName() + ",\n\n"
            + "Your booking " + booking.getBookingReference() + " for " + tour.getTitle() + " is now "
            + booking.getStatus().name().toLowerCase() + ".\n"
            + booking.getStatusReason() + "\n\nWanderlust Travels";
        notifyBookingRecipient(booking, NotificationCategory.BOOKING_STATUS_CHANGED, subject, message);
    }

    private void notifyWaitlistIfSeatOpened(Booking booking, BookingStatus previousStatus) {
        if (previousStatus == BookingStatus.CANCELLED || booking.getStatus() != BookingStatus.CANCELLED) {
            return;
        }
        Tour tour = requireTour(booking.getTourId());
        waitlistService.notifyNextEligibleTraveler(tour, booking.getDate());
    }

    private void recordBookingFailure(Tour tour, BookingRequestContext requestContext, String reason) {
        notificationService.notifyAdmins(
            NotificationCategory.BOOKING_FAILED,
            "Booking attempt failed",
            requestContext.customerName() + " could not book " + tour.getTitle() + " for " + requestContext.date() + ": " + reason,
            null,
            null
        );
    }

    private Booking requireBooking(Long id) {
        return bookingRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private Tour requireTour(String tourId) {
        return tourCatalogService.findById(tourId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));
    }

    private BookingRequestContext toRequestContext(BookingRequest request, Optional<AppUser> actor) {
        return new BookingRequestContext(
            request.customerName().trim(),
            actor.map(AppUser::getEmail).orElse(normalizeEmail(request.email())),
            request.phone().trim(),
            request.guests(),
            request.date()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeReference(String bookingReference) {
        return bookingReference == null ? "" : bookingReference.trim();
    }

    private void notifyBookingRecipient(Booking booking, NotificationCategory category, String subject, String message) {
        if (booking.getUserId() != null) {
            Optional<AppUser> appUser = appUserService.findById(booking.getUserId());
            if (appUser.isPresent()) {
                notificationService.notifyUser(appUser.get(), category, subject, message, booking.getId(), null);
                return;
            }
        }

        notificationService.notifyGuest(
            booking.getEmail(),
            booking.getCustomerName(),
            category,
            subject,
            message,
            booking.getId(),
            null
        );
    }

    private void applyTravelerPreferences(
        Booking booking,
        String mealPreference,
        String dietaryRestrictions,
        String occasionType,
        String occasionNotes,
        String roomPreference,
        String tripStyle,
        Boolean transferRequired,
        String assistanceNotes,
        String travelerNotes,
        String transportMode,
        String transportClass
    ) {
        booking.setMealPreference(cleanSelection(mealPreference));
        booking.setDietaryRestrictions(cleanOptionalNote(dietaryRestrictions));
        booking.setOccasionType(cleanSelection(occasionType));
        booking.setOccasionNotes(cleanOptionalNote(occasionNotes));
        booking.setRoomPreference(cleanSelection(roomPreference));
        booking.setTripStyle(cleanSelection(tripStyle));
        booking.setTransferRequired(Boolean.TRUE.equals(transferRequired));
        booking.setAssistanceNotes(cleanOptionalNote(assistanceNotes));
        booking.setTravelerNotes(cleanOptionalNote(travelerNotes));
        String normalizedTransportMode = normalizeTransportMode(transportMode, booking.getTransportMode());
        booking.setTransportMode(normalizedTransportMode);
        booking.setTransportClass(normalizeTransportClass(transportClass, normalizedTransportMode));
        booking.setTransportStatus(resolveTravelerTransportStatus(normalizedTransportMode, booking.getTransportStatus()));
    }

    private void mergeTravelerPreferences(Booking booking, BookingPreferenceUpdateRequest request) {
        if (request.mealPreference() != null) {
            booking.setMealPreference(cleanSelection(request.mealPreference()));
        }
        if (request.dietaryRestrictions() != null) {
            booking.setDietaryRestrictions(cleanOptionalNote(request.dietaryRestrictions()));
        }
        if (request.occasionType() != null) {
            booking.setOccasionType(cleanSelection(request.occasionType()));
        }
        if (request.occasionNotes() != null) {
            booking.setOccasionNotes(cleanOptionalNote(request.occasionNotes()));
        }
        if (request.roomPreference() != null) {
            booking.setRoomPreference(cleanSelection(request.roomPreference()));
        }
        if (request.tripStyle() != null) {
            booking.setTripStyle(cleanSelection(request.tripStyle()));
        }
        if (request.transferRequired() != null) {
            booking.setTransferRequired(request.transferRequired());
        }
        if (request.assistanceNotes() != null) {
            booking.setAssistanceNotes(cleanOptionalNote(request.assistanceNotes()));
        }
        if (request.travelerNotes() != null) {
            booking.setTravelerNotes(cleanOptionalNote(request.travelerNotes()));
        }

        String previousTransportMode = booking.getTransportMode();
        String previousTransportClass = booking.getTransportClass();
        String effectiveTransportMode = booking.getTransportMode();
        boolean transportModeUpdated = request.transportMode() != null;
        boolean transportClassUpdated = request.transportClass() != null;

        if (transportModeUpdated) {
            effectiveTransportMode = normalizeTransportMode(request.transportMode(), booking.getTransportMode());
            booking.setTransportMode(effectiveTransportMode);
        }

        if (transportModeUpdated || transportClassUpdated) {
            String rawTransportClass = transportClassUpdated ? request.transportClass() : booking.getTransportClass();
            String effectiveTransportClass = normalizeTransportClass(rawTransportClass, effectiveTransportMode);
            booking.setTransportClass(effectiveTransportClass);
            booking.setTransportStatus(resolveTravelerTransportStatusAfterEdit(
                previousTransportMode,
                previousTransportClass,
                effectiveTransportMode,
                effectiveTransportClass,
                booking.getTransportStatus()
            ));
        }
    }

    private String cleanNote(String note, String fallback) {
        return note == null || note.isBlank() ? fallback : note.trim();
    }

    private String cleanSelection(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase();
        if (normalized.equals("none")
            || normalized.equals("n/a")
            || normalized.equals("na")
            || normalized.equals("no")
            || normalized.equals("clear")
            || normalized.equals("remove")
            || normalized.equals("not needed")
            || normalized.equals("no preference")) {
            return null;
        }

        return toLabelCase(trimmed);
    }

    private String cleanOptionalNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private boolean hasTravelerPreferences(Booking booking) {
        return hasText(booking.getMealPreference())
            || hasText(booking.getDietaryRestrictions())
            || hasText(booking.getOccasionType())
            || hasText(booking.getOccasionNotes())
            || hasText(booking.getRoomPreference())
            || hasText(booking.getTripStyle())
            || booking.isTransferRequired()
            || hasText(booking.getAssistanceNotes())
            || hasText(booking.getTravelerNotes())
            || hasText(booking.getTransportMode())
            || hasText(booking.getTransportClass());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String travelerPreferenceSummary(Booking booking) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (hasText(booking.getOccasionType())) {
            parts.add("Occasion: " + booking.getOccasionType());
        }
        if (hasText(booking.getOccasionNotes())) {
            parts.add("Celebration: " + booking.getOccasionNotes());
        }
        if (hasText(booking.getRoomPreference())) {
            parts.add("Room: " + booking.getRoomPreference());
        }
        if (hasText(booking.getTripStyle())) {
            parts.add("Style: " + booking.getTripStyle());
        }
        if (hasText(booking.getTransportMode())) {
            String transportLabel = booking.getTransportMode();
            if (hasText(booking.getTransportClass())) {
                transportLabel += " / " + booking.getTransportClass();
            }
            parts.add("Transport: " + transportLabel);
        }
        if (hasText(booking.getMealPreference())) {
            parts.add("Meal: " + booking.getMealPreference());
        }
        if (hasText(booking.getDietaryRestrictions())) {
            parts.add("Dietary: " + booking.getDietaryRestrictions());
        }
        if (booking.isTransferRequired()) {
            parts.add("Transfer requested");
        }
        if (hasText(booking.getAssistanceNotes())) {
            parts.add("Assistance: " + booking.getAssistanceNotes());
        }
        if (hasText(booking.getTravelerNotes())) {
            parts.add("Notes: " + booking.getTravelerNotes());
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String adminOperationsSummary(Booking booking) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (hasText(booking.getTransportMode())) {
            String transport = booking.getTransportMode();
            if (hasText(booking.getTransportClass())) {
                transport += " / " + booking.getTransportClass();
            }
            parts.add("Transport: " + transport);
        }
        parts.add("Transport status: " + booking.getTransportStatus());
        parts.add("Documents verified: " + yesNo(booking.isDocumentsVerified()));
        parts.add("Priority: " + booking.getOperationsPriority());
        if (hasText(booking.getOperationsNotes())) {
            parts.add("Ops note: " + booking.getOperationsNotes());
        }
        return String.join(" | ", parts);
    }

    private String bookingCreatedActivityNote(Booking booking) {
        String preferenceSummary = travelerPreferenceSummary(booking);
        if (preferenceSummary == null) {
            return booking.getStatusReason();
        }
        return activitySafeNote(booking.getStatusReason() + ". " + preferenceSummary);
    }

    private String activitySafeNote(String note) {
        if (note == null || note.length() <= 500) {
            return note;
        }
        return note.substring(0, 497) + "...";
    }

    private String normalizeTransportMode(String transportMode, String currentValue) {
        if (transportMode == null) {
            return currentValue;
        }
        if (transportMode.isBlank()) {
            return null;
        }

        String normalized = transportMode.trim().toLowerCase();
        return switch (normalized) {
            case "plane", "flight", "air", "airplane" -> "Flight";
            case "train", "rail" -> "Train";
            case "bus", "coach" -> "Bus";
            case "self", "self arranged", "self-arranged", "own", "own transport" -> "Self Arranged";
            case "none", "no" -> null;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported transport option");
        };
    }

    private String normalizeTransportClass(String transportClass, String transportMode) {
        if (!hasText(transportMode) || "Self Arranged".equals(transportMode)) {
            return null;
        }
        if (transportClass == null || transportClass.isBlank()) {
            return null;
        }
        return toLabelCase(transportClass);
    }

    private String resolveTravelerTransportStatus(String transportMode, String currentStatus) {
        if (!requiresTransportArrangement(transportMode)) {
            return TRANSPORT_STATUS_NOT_REQUIRED;
        }
        if (!hasText(currentStatus) || TRANSPORT_STATUS_NOT_REQUIRED.equalsIgnoreCase(currentStatus)) {
            return TRANSPORT_STATUS_REQUESTED;
        }
        return currentStatus;
    }

    private String resolveTravelerTransportStatusAfterEdit(
        String previousTransportMode,
        String previousTransportClass,
        String transportMode,
        String transportClass,
        String currentStatus
    ) {
        if (!requiresTransportArrangement(transportMode)) {
            return TRANSPORT_STATUS_NOT_REQUIRED;
        }

        boolean logisticsChanged = !java.util.Objects.equals(previousTransportMode, transportMode)
            || !java.util.Objects.equals(previousTransportClass, transportClass);
        if (logisticsChanged) {
            return TRANSPORT_STATUS_REQUESTED;
        }

        return resolveTravelerTransportStatus(transportMode, currentStatus);
    }

    private String resolveAdminTransportStatus(String requestedStatus, String transportMode, String currentStatus) {
        if (!requiresTransportArrangement(transportMode)) {
            return TRANSPORT_STATUS_NOT_REQUIRED;
        }
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return hasText(currentStatus) ? currentStatus : TRANSPORT_STATUS_REQUESTED;
        }
        String normalized = requestedStatus.trim().toLowerCase();
        return switch (normalized) {
            case "requested" -> TRANSPORT_STATUS_REQUESTED;
            case "quoted", "quote ready" -> TRANSPORT_STATUS_QUOTED;
            case "confirmed", "booked" -> TRANSPORT_STATUS_CONFIRMED;
            case "not required", "none" -> TRANSPORT_STATUS_NOT_REQUIRED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported transport status");
        };
    }

    private String normalizeOperationsPriority(String requestedPriority, String currentPriority) {
        if (requestedPriority == null || requestedPriority.isBlank()) {
            return hasText(currentPriority) ? currentPriority : "Normal";
        }
        String normalized = requestedPriority.trim().toLowerCase();
        return switch (normalized) {
            case "normal" -> "Normal";
            case "high" -> "High";
            case "vip" -> "VIP";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported operations priority");
        };
    }

    private String defaultOperationsPriority(Booking booking, BigDecimal totalPrice) {
        if (totalPrice.compareTo(new BigDecimal("10000")) >= 0) {
            return "VIP";
        }
        if (booking.getGuests() >= 4
            || hasConciergePreference(booking)
            || booking.isTransferRequired()
            || hasText(booking.getAssistanceNotes())
            || requiresTransportArrangement(booking.getTransportMode())) {
            return "High";
        }
        return "Normal";
    }

    private boolean hasConciergePreference(Booking booking) {
        return hasText(booking.getDietaryRestrictions())
            || hasText(booking.getOccasionType())
            || hasText(booking.getOccasionNotes())
            || hasText(booking.getRoomPreference())
            || hasText(booking.getTripStyle());
    }

    private boolean requiresTransportArrangement(String transportMode) {
        return "Flight".equals(transportMode) || "Train".equals(transportMode) || "Bus".equals(transportMode);
    }

    private boolean transportStatusChanged(String previousStatus, String currentStatus) {
        return !java.util.Objects.equals(previousStatus, currentStatus);
    }

    private String travelerOperationsMessage(Booking booking) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (hasText(booking.getTransportMode())) {
            String transport = booking.getTransportMode();
            if (hasText(booking.getTransportClass())) {
                transport += " / " + booking.getTransportClass();
            }
            parts.add("Transport option: " + transport + ".");
        }
        parts.add("Transport status: " + booking.getTransportStatus() + ".");
        parts.add("Documents verified: " + yesNo(booking.isDocumentsVerified()) + ".");
        return String.join(" ", parts);
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private String toLabelCase(String value) {
        String[] words = value.trim().toLowerCase().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
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

    private String defaultStatusReason(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirmed by travel team";
            case CANCELLED -> "Cancelled by travel team";
            case PENDING -> "Booking moved back to pending review";
        };
    }
}
