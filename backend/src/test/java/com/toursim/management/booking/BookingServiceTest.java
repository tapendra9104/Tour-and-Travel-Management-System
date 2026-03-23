package com.toursim.management.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toursim.management.auth.AppUserService;
import com.toursim.management.booking.dto.BookingPreferenceUpdateRequest;
import com.toursim.management.booking.dto.BookingRequest;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;
import com.toursim.management.waitlist.WaitlistEntry;
import com.toursim.management.waitlist.WaitlistService;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 4, 15);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TourCatalogService tourCatalogService;

    @Mock
    private WaitlistService waitlistService;

    @Mock
    private BookingActivityService bookingActivityService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AppUserService appUserService;

    @InjectMocks
    private BookingService bookingService;

    private Tour tour;

    @BeforeEach
    void setUp() {
        tour = new Tour();
        tour.setId("1");
        tour.setTitle("Majestic Swiss Alps Adventure");
        tour.setPrice(new BigDecimal("2499.00"));
        tour.setMaxGroupSize(12);
        tour.setStartDates(List.of(TRAVEL_DATE));
    }

    @Test
    void createBookingCreatesWaitlistEntryWhenDepartureIsFull() {
        BookingRequest request = new BookingRequest("1", "Waitlist Guest", "wait@example.com", "+1", 2, TRAVEL_DATE, null, null, null, null, null, null, null, false, null, null, null);
        WaitlistEntry waitlistEntry = new WaitlistEntry();
        waitlistEntry.setWaitlistReference("WL-1001");

        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));
        when(bookingRepository.totalGuestsForDeparture("1", TRAVEL_DATE)).thenReturn(12);
        when(waitlistService.createWaitlistEntry(any(), any(), any())).thenReturn(waitlistEntry);

        BookingSubmissionResult result = bookingService.createBooking(request, Optional.empty());

        assertThat(result.outcome()).isEqualTo("waitlisted");
        assertThat(result.waitlistEntry()).isSameAs(waitlistEntry);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBookingStoresPendingBookingWhenSeatsAreAvailable() {
        BookingRequest request = new BookingRequest(
            "1",
            "Booked Guest",
            "booked@example.com",
            "+1",
            2,
            TRAVEL_DATE,
            "Vegetarian",
            "Nut allergy",
            "Honeymoon",
            "Private dinner and room decor",
            "Sea View Suite",
            "Romantic",
            "Wheelchair boarding support",
            true,
            "Anniversary trip",
            "Flight",
            "Business"
        );

        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));
        when(bookingRepository.totalGuestsForDeparture("1", TRAVEL_DATE)).thenReturn(4);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(99L);
            booking.setBookingReference("BK-1001");
            return booking;
        });

        BookingSubmissionResult result = bookingService.createBooking(request, Optional.empty());

        assertThat(result.outcome()).isEqualTo("booked");
        assertThat(result.booking()).isNotNull();
        assertThat(result.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.booking().getStatusReason()).isEqualTo("Awaiting travel team confirmation");

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getTourId()).isEqualTo("1");
        assertThat(bookingCaptor.getValue().getGuests()).isEqualTo(2);
        assertThat(bookingCaptor.getValue().getTotalPrice()).isEqualByComparingTo("5247.90");
        assertThat(bookingCaptor.getValue().getMealPreference()).isEqualTo("Vegetarian");
        assertThat(bookingCaptor.getValue().getDietaryRestrictions()).isEqualTo("Nut allergy");
        assertThat(bookingCaptor.getValue().getOccasionType()).isEqualTo("Honeymoon");
        assertThat(bookingCaptor.getValue().getOccasionNotes()).isEqualTo("Private dinner and room decor");
        assertThat(bookingCaptor.getValue().getRoomPreference()).isEqualTo("Sea View Suite");
        assertThat(bookingCaptor.getValue().getTripStyle()).isEqualTo("Romantic");
        assertThat(bookingCaptor.getValue().isTransferRequired()).isTrue();
        assertThat(bookingCaptor.getValue().getAssistanceNotes()).isEqualTo("Wheelchair boarding support");
        assertThat(bookingCaptor.getValue().getTravelerNotes()).isEqualTo("Anniversary trip");
        assertThat(bookingCaptor.getValue().getTransportMode()).isEqualTo("Flight");
        assertThat(bookingCaptor.getValue().getTransportClass()).isEqualTo("Business");
        assertThat(bookingCaptor.getValue().getTransportStatus()).isEqualTo("Requested");
        assertThat(bookingCaptor.getValue().getOperationsPriority()).isEqualTo("High");
    }

    @Test
    void updateTravelerPreferencesAllowsMealAndTravelEditingWithoutClearingOtherPreferences() {
        Booking booking = new Booking();
        booking.setId(77L);
        booking.setTourId("1");
        booking.setBookingReference("BK-7700");
        booking.setCustomerName("Edit Guest");
        booking.setEmail("edit@example.com");
        booking.setPhone("+1");
        booking.setGuests(2);
        booking.setDate(TRAVEL_DATE);
        booking.setTotalPrice(new BigDecimal("5247.90"));
        booking.setServiceFee(new BigDecimal("249.90"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setMealPreference("Vegetarian");
        booking.setDietaryRestrictions("Nut allergy");
        booking.setOccasionType("Honeymoon");
        booking.setOccasionNotes("Room decor");
        booking.setRoomPreference("Suite");
        booking.setTripStyle("Luxury");
        booking.setTransportMode("Flight");
        booking.setTransportClass("Business");
        booking.setTransportStatus("Confirmed");
        booking.setTravelerNotes("Window seat");

        BookingPreferenceUpdateRequest request = new BookingPreferenceUpdateRequest(
            "BK-7700",
            "edit@example.com",
            "Jain",
            "No onion garlic",
            null,
            null,
            null,
            null,
            "Wheelchair boarding support",
            true,
            "Aisle seat near front",
            "Train",
            "Sleeper"
        );

        when(bookingRepository.findById(77L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));

        Booking updated = bookingService.updateTravelerPreferences(77L, request, Optional.empty());

        assertThat(updated.getMealPreference()).isEqualTo("Jain");
        assertThat(updated.getDietaryRestrictions()).isEqualTo("No onion garlic");
        assertThat(updated.getTransportMode()).isEqualTo("Train");
        assertThat(updated.getTransportClass()).isEqualTo("Sleeper");
        assertThat(updated.getTransportStatus()).isEqualTo("Requested");
        assertThat(updated.isTransferRequired()).isTrue();
        assertThat(updated.getAssistanceNotes()).isEqualTo("Wheelchair boarding support");
        assertThat(updated.getTravelerNotes()).isEqualTo("Aisle seat near front");
        assertThat(updated.getOccasionType()).isEqualTo("Honeymoon");
        assertThat(updated.getOccasionNotes()).isEqualTo("Room decor");
        assertThat(updated.getRoomPreference()).isEqualTo("Suite");
        assertThat(updated.getTripStyle()).isEqualTo("Luxury");
    }
}
