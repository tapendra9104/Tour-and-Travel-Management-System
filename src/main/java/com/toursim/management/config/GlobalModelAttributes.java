package com.toursim.management.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.toursim.management.auth.AuthenticationFacade;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalModelAttributes {

    private final AuthenticationFacade authenticationFacade;

    public GlobalModelAttributes(AuthenticationFacade authenticationFacade) {
        this.authenticationFacade = authenticationFacade;
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated() {
        return authenticationFacade.currentUser().isPresent();
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return authenticationFacade.isAdmin();
    }

    @ModelAttribute("currentUserName")
    public String currentUserName() {
        return authenticationFacade.currentUser()
            .map(appUser -> appUser.getFullName())
            .orElse("");
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail() {
        return authenticationFacade.currentUser()
            .map(appUser -> appUser.getEmail())
            .orElse("");
    }

    @ModelAttribute("currentUserPhone")
    public String currentUserPhone() {
        return authenticationFacade.currentUser()
            .map(appUser -> appUser.getPhone())
            .orElse("");
    }

    @ModelAttribute("requestPath")
    public String requestPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
