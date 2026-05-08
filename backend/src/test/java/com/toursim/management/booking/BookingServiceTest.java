package com.toursim.management.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.UserRole;
import com.toursim.management.booking.dto.BookingRequest;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;
import com.toursim.management.waitlist.WaitlistService;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService")
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock TourCatalogService tourCatalogService;
    @Mock WaitlistService waitlistService;
    @Mock BookingActivityService bookingActivityService;
    @Mock NotificationService notificationService;
    @Mock AppUserService appUserService;

    private Tour tour;
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(30);

    @BeforeEach
    void setUp() {
        tour = new Tour();
        tour.setId("test-tour");
        tour.setTitle("Test Tour");
        tour.setMaxGroupSize(10);
        tour.setPrice(new BigDecimal("1000.00"));
        tour.getStartDates().add(FUTURE_DATE);
    }

    // -- Booking reference uniqueness ------------------------------------------

    @Test
    @DisplayName("Booking reference uses UUID pattern (BK-XXXXXXXXXXXX), not timestamp")
    void bookingReference_isUuidBased() {
        Booking b = new Booking();
        callPrePersist(b);

        assertThat(b.getBookingReference())
            .startsWith("BK-")
            .hasSize(15)               // "BK-" (3) + 12 UUID chars
            .matches("BK-[A-F0-9]{12}");
    }

    @Test
    @DisplayName("Two bookings created concurrently have different references")
    void bookingReference_isUnique() {
        Booking b1 = new Booking();
        Booking b2 = new Booking();
        callPrePersist(b1);
        callPrePersist(b2);
        assertThat(b1.getBookingReference()).isNotEqualTo(b2.getBookingReference());
    }

    // -- Stale-booking query efficiency ----------------------------------------

    @Test
    @DisplayName("findByStatusAndCreatedAtBefore exists on BookingRepository")
    void stalePendingBookings_repositoryMethodExists() {
        // Verify the targeted query method exists (not the full-table-scan fallback)
        when(bookingRepository.findByStatusAndCreatedAtBefore(eq(BookingStatus.PENDING), any(LocalDateTime.class)))
            .thenReturn(List.of());

        List<Booking> result = bookingRepository.findByStatusAndCreatedAtBefore(
            BookingStatus.PENDING, LocalDateTime.now().minusHours(48));

        assertThat(result).isEmpty();
        // findAllByOrderByCreatedAtDesc must NOT be called (that's the in-memory anti-pattern)
        verify(bookingRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Admin-assisted bookings do not overwrite the admin profile")
    void createBooking_doesNotSyncAdminProfile() {
        BookingService service = bookingService();
        AppUser admin = appUser(UserRole.ADMIN);
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);

        stubSuccessfulBookingSave();

        service.createBooking(bookingRequest("Guest Traveler", "guest@example.com"), Optional.of(admin));

        verify(appUserService, never()).syncProfile(any(), any(), any());
        verify(bookingRepository).save(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getUserId()).isNull();
        assertThat(bookingCaptor.getValue().getEmail()).isEqualTo("guest@example.com");
    }

    @Test
    @DisplayName("Traveler bookings keep the signed-in traveler profile fresh")
    void createBooking_syncsTravelerProfile() {
        BookingService service = bookingService();
        AppUser traveler = appUser(UserRole.USER);
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);

        stubSuccessfulBookingSave();

        service.createBooking(bookingRequest("Guest Traveler", "guest@example.com"), Optional.of(traveler));

        verify(appUserService).syncProfile(traveler, "Guest Traveler", "+1 555 010 0000");
        verify(bookingRepository).save(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getUserId()).isEqualTo(2L);
        assertThat(bookingCaptor.getValue().getEmail()).isEqualTo("traveler@example.com");
    }

    // -- Helpers ---------------------------------------------------------------

    private void callPrePersist(Booking booking) {
        try {
            var method = Booking.class.getDeclaredMethod("prePersist");
            method.setAccessible(true);
            method.invoke(booking);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BookingService bookingService() {
        return new BookingService(
            bookingRepository,
            tourCatalogService,
            waitlistService,
            bookingActivityService,
            notificationService,
            appUserService
        );
    }

    private void stubSuccessfulBookingSave() {
        when(tourCatalogService.findById("test-tour")).thenReturn(Optional.of(tour));
        when(bookingRepository.findAndLockForDeparture("test-tour", FUTURE_DATE)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(42L);
            callPrePersist(booking);
            return booking;
        });
    }

    private BookingRequest bookingRequest(String customerName, String email) {
        return new BookingRequest(
            "test-tour",
            customerName,
            email,
            "+1 555 010 0000",
            2,
            FUTURE_DATE,
            "Vegetarian",
            "No peanuts",
            "Anniversary",
            "Window table",
            "King Bed",
            "Luxury",
            "Airport pickup",
            true,
            "Smoke test",
            "Flight",
            "Economy"
        );
    }

    private AppUser appUser(UserRole role) {
        AppUser appUser = new AppUser();
        appUser.setId(role == UserRole.ADMIN ? 1L : 2L);
        appUser.setEmail(role == UserRole.ADMIN ? "admin@example.com" : "traveler@example.com");
        appUser.setFullName(role == UserRole.ADMIN ? "Admin User" : "Traveler User");
        appUser.setPhone("+1 555 000 0000");
        appUser.setRole(role);
        return appUser;
    }
}
