package com.toursim.management.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.UserRole;

@Component
public class ApplicationDataInitializer implements ApplicationRunner {

    private final AppUserService appUserService;

    public ApplicationDataInitializer(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @Override
    public void run(ApplicationArguments args) {
        appUserService.ensureSeedUser("admin@wanderlust.com", "Admin@123", "Wanderlust Admin", "+1 (555) 010-0001", UserRole.ADMIN);
        appUserService.ensureSeedUser("traveler@wanderlust.com", "Traveler@123", "Demo Traveler", "+1 (555) 010-0002", UserRole.USER);
    }
}
