package com.toursim.management.web;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingService;
import com.toursim.management.dashboard.DashboardResponse;
import com.toursim.management.dashboard.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;
    private final BookingService bookingService;
    private final AuthenticationFacade authenticationFacade;

    public DashboardApiController(
        DashboardService dashboardService,
        BookingService bookingService,
        AuthenticationFacade authenticationFacade
    ) {
        this.dashboardService = dashboardService;
        this.bookingService = bookingService;
        this.authenticationFacade = authenticationFacade;
    }

    @GetMapping
    public DashboardResponse dashboard(
        @RequestParam(required = false) String reference,
        @RequestParam(required = false) String email
    ) {
        Optional<Booking> guestLookup = Optional.empty();
        if (authenticationFacade.currentUser().isEmpty() && reference != null && !reference.isBlank() && email != null && !email.isBlank()) {
            guestLookup = bookingService.lookupBooking(reference, email);
            if (guestLookup.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found for that reference and email");
            }
        }
        return dashboardService.build(authenticationFacade.currentUser(), guestLookup);
    }
}
