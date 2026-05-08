package com.toursim.management.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.UserRole;
import com.toursim.management.tour.TourCatalogService;

/**
 * Seeds local/demo data after the full application context is ready.
 * Production disables demo credentials through profile-specific configuration.
 */
@Component
@Order(1)
public class ApplicationDataInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationDataInitializer.class);

    private final AppUserService appUserService;
    private final TourCatalogService tourCatalogService;

    @Value("${app.seed.admin.enabled:true}")
    private boolean adminSeedEnabled;

    @Value("${app.seed.admin.email:admin@wanderlust.com}")
    private String adminEmail;

    @Value("${app.seed.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.seed.admin.name:Wanderlust Admin}")
    private String adminName;

    @Value("${app.seed.traveler.enabled:true}")
    private boolean travelerSeedEnabled;

    @Value("${app.seed.traveler.email:traveler@wanderlust.com}")
    private String travelerEmail;

    @Value("${app.seed.traveler.password:Traveler@123}")
    private String travelerPassword;

    @Value("${app.seed.traveler.name:Demo Traveler}")
    private String travelerName;

    public ApplicationDataInitializer(AppUserService appUserService, TourCatalogService tourCatalogService) {
        this.appUserService = appUserService;
        this.tourCatalogService = tourCatalogService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seedUser(adminSeedEnabled, adminEmail, adminPassword, adminName, "+1 (555) 010-0001", UserRole.ADMIN);
        seedUser(travelerSeedEnabled, travelerEmail, travelerPassword, travelerName, "+1 (555) 010-0002", UserRole.USER);
        tourCatalogService.syncSeedCatalog();
    }

    private void seedUser(boolean enabled, String email, String password, String name, String phone, UserRole role) {
        if (!enabled) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            LOGGER.info("Skipping {} seed user because email or password is not configured.", role);
            return;
        }
        appUserService.ensureSeedUser(email, password, name, phone, role);
    }
}
