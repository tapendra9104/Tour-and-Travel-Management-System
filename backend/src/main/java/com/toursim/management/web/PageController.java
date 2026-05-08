package com.toursim.management.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.booking.BookingService;
import com.toursim.management.dashboard.DashboardService;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;
import com.toursim.management.tour.TourViewService;

@Controller
public class PageController {

    private final TourCatalogService tourCatalogService;
    private final ObjectMapper objectMapper;
    private final AuthenticationFacade authenticationFacade;
    private final DashboardService dashboardService;
    private final TourViewService tourViewService;
    private final BookingService bookingService;
    private final String paymentCurrency;
    private final String paymentDepositRate;
    private final int paymentFullPaymentWindowDays;
    private final int paymentBalanceDueDaysBeforeDeparture;

    public PageController(
        TourCatalogService tourCatalogService,
        ObjectMapper objectMapper,
        AuthenticationFacade authenticationFacade,
        DashboardService dashboardService,
        TourViewService tourViewService,
        BookingService bookingService,
        @Value("${app.payments.currency:USD}") String paymentCurrency,
        @Value("${app.payments.deposit-rate:0.30}") String paymentDepositRate,
        @Value("${app.payments.full-payment-window-days:45}") int paymentFullPaymentWindowDays,
        @Value("${app.payments.balance-due-days-before-departure:21}") int paymentBalanceDueDaysBeforeDeparture
    ) {
        this.tourCatalogService = tourCatalogService;
        this.objectMapper = objectMapper;
        this.authenticationFacade = authenticationFacade;
        this.dashboardService = dashboardService;
        this.tourViewService = tourViewService;
        this.bookingService = bookingService;
        this.paymentCurrency = paymentCurrency;
        this.paymentDepositRate = paymentDepositRate;
        this.paymentFullPaymentWindowDays = paymentFullPaymentWindowDays;
        this.paymentBalanceDueDaysBeforeDeparture = paymentBalanceDueDaysBeforeDeparture;
    }

    @GetMapping("/")
    public String home(Model model) {
        addCommonModel(model, "home", "Discover Your Next Adventure");
        model.addAttribute("featuredTours", tourCatalogService.featuredTours(4));
        model.addAttribute("categories", tourCatalogService.categories());
        return "index";
    }

    @GetMapping("/tours")
    public String tours(
        @RequestParam(required = false, defaultValue = "") String search,
        @RequestParam(required = false, defaultValue = "All") String category,
        @RequestParam(required = false, defaultValue = "") String guests,
        Model model
    ) {
        addCommonModel(model, "tours", "Explore Our Tours");
        // Call findAll() once - previously called twice (model + JSON)
        java.util.List<com.toursim.management.tour.Tour> allTours = tourCatalogService.findAll();
        model.addAttribute("tours", allTours);
        model.addAttribute("categories", tourCatalogService.categories());
        model.addAttribute("initialSearch", search);
        model.addAttribute("initialCategory", category);
        model.addAttribute("initialGuests", guests);
        model.addAttribute("toursJson", writeJson(allTours));
        return "tours";
    }

    @GetMapping("/tours/{id}")
    public String tourDetail(@PathVariable String id, Model model) {
        Tour tour = tourCatalogService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));

        addCommonModel(model, "tours", tour.getTitle());
        model.addAttribute("tour", tour);
        model.addAttribute("relatedTours", tourCatalogService.relatedTours(tour, 3));
        model.addAttribute("tourJson", writeJson(tour));
        model.addAttribute("availabilityJson", writeJson(bookingService.availabilityForTour(tour)));
        model.addAttribute("paymentCurrency", paymentCurrency);
        model.addAttribute("paymentDepositRate", paymentDepositRate);
        model.addAttribute("paymentFullPaymentWindowDays", paymentFullPaymentWindowDays);
        model.addAttribute("paymentBalanceDueDaysBeforeDeparture", paymentBalanceDueDaysBeforeDeparture);

        Optional<AppUser> currentUser = authenticationFacade.currentUser();
        currentUser.ifPresent(appUser -> {
            tourViewService.recordView(appUser.getId(), tour.getId());
            model.addAttribute("bookingName", appUser.getFullName());
            model.addAttribute("bookingEmail", appUser.getEmail());
            model.addAttribute("bookingPhone", appUser.getPhone());
        });

        return "tour-detail";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        var snapshot = dashboardService.build(authenticationFacade.currentUser(), Optional.empty());
        addCommonModel(model, "dashboard", snapshot.heading());
        model.addAttribute("dashboardHeading", snapshot.heading());
        model.addAttribute("dashboardMode", snapshot.mode());
        model.addAttribute("recommendedTours", snapshot.recommendations());
        return "dashboard";
    }

    @GetMapping("/destinations")
    public String destinations(Model model) {
        addCommonModel(model, "destinations", "Destinations");
        model.addAttribute("regions", tourCatalogService.destinationRegions());
        model.addAttribute("featuredTours", tourCatalogService.featuredTours(6));
        return "destinations";
    }

    @GetMapping("/about")
    public String about(Model model) {
        addCommonModel(model, "about", "About Wanderlust");
        model.addAttribute("stats", Map.of(
            "travelers", "25K+",
            "destinations", "60+",
            "rating", "4.9/5",
            "support", "24/7"
        ));
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        addCommonModel(model, "contact", "Contact Us");
        return "contact";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminPanel(Model model) {
        addCommonModel(model, "admin", "Admin Control Panel");
        return "admin";
    }

    private void addCommonModel(Model model, String currentPage, String pageTitle) {
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("pageTitle", pageTitle);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize page data", exception);
        }
    }
}
