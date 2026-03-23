package com.toursim.management.auth;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEvents {

    private final AppUserService appUserService;

    public AuthenticationEvents(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        appUserService.markLastLogin(event.getAuthentication().getName());
    }
}
