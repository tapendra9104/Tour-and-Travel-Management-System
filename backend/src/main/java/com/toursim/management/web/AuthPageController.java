package com.toursim.management.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.auth.RegistrationRequest;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;

import jakarta.validation.Valid;

@Controller
public class AuthPageController {

    private final AppUserService appUserService;
    private final AuthenticationFacade authenticationFacade;
    private final NotificationService notificationService;

    public AuthPageController(
        AppUserService appUserService,
        AuthenticationFacade authenticationFacade,
        NotificationService notificationService
    ) {
        this.appUserService = appUserService;
        this.authenticationFacade = authenticationFacade;
        this.notificationService = notificationService;
    }

    @GetMapping("/login")
    public String login(
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String registered,
        @RequestParam(required = false) String logout,
        Model model
    ) {
        if (authenticationFacade.currentUser().isPresent()) {
            return authenticationFacade.isAdmin() ? "redirect:/admin" : "redirect:/dashboard";
        }
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Sign In");
        model.addAttribute("hasError", error != null);
        model.addAttribute("hasRegistered", registered != null);
        model.addAttribute("hasLogout", logout != null);
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (authenticationFacade.currentUser().isPresent()) {
            return authenticationFacade.isAdmin() ? "redirect:/admin" : "redirect:/dashboard";
        }
        populateRegisterModel(model, new RegistrationRequest("", "", "", ""), false);
        return "register";
    }

    @PostMapping("/register")
    public String register(
        @Valid @ModelAttribute("registrationForm") RegistrationRequest registrationRequest,
        BindingResult bindingResult,
        Model model
    ) {
        if (authenticationFacade.currentUser().isPresent()) {
            return authenticationFacade.isAdmin() ? "redirect:/admin" : "redirect:/dashboard";
        }
        populateRegisterModel(model, registrationRequest, bindingResult.hasErrors());

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            AppUser appUser = appUserService.registerUser(registrationRequest);
            notificationService.notifyUser(
                appUser,
                NotificationCategory.ACCOUNT_CREATED,
                "Welcome to Wanderlust Travels",
                "Hi " + appUser.getFullName() + ",\n\n"
                    + "Your Wanderlust account is ready. You can now sign in to manage bookings, save traveler preferences, track payments, and receive trip updates.\n\n"
                    + "Wanderlust Travels",
                null,
                null
            );
            return "redirect:/login?registered=1";
        } catch (ResponseStatusException exception) {
            model.addAttribute("formError", exception.getReason());
            model.addAttribute("hasValidationErrors", true);
            return "register";
        }
    }

    private void populateRegisterModel(Model model, RegistrationRequest registrationRequest, boolean hasValidationErrors) {
        model.addAttribute("currentPage", "register");
        model.addAttribute("pageTitle", "Create Account");
        model.addAttribute("registrationForm", registrationRequest);
        model.addAttribute("hasValidationErrors", hasValidationErrors);
    }
}
