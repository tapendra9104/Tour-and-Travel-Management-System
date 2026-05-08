package com.toursim.management.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.auth.RegistrationRequest;
import com.toursim.management.auth.UserRole;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;

class AuthPageControllerTest {

    private AppUserService appUserService;
    private AuthenticationFacade authenticationFacade;
    private NotificationService notificationService;
    private AuthPageController authPageController;

    @BeforeEach
    void setUp() {
        appUserService = mock(AppUserService.class);
        authenticationFacade = mock(AuthenticationFacade.class);
        notificationService = mock(NotificationService.class);
        authPageController = new AuthPageController(appUserService, authenticationFacade, notificationService);
    }

    @Test
    void loginRedirectsAuthenticatedUsersToDashboard() {
        when(authenticationFacade.currentUser()).thenReturn(Optional.of(authenticatedUser()));

        String viewName = authPageController.login(null, null, null, new ExtendedModelMap());

        assertThat(viewName).isEqualTo("redirect:/dashboard");
    }

    @Test
    void loginRedirectsAuthenticatedAdminsToAdminDashboard() {
        AppUser admin = authenticatedUser();
        admin.setRole(UserRole.ADMIN);
        admin.setEmail("admin@wanderlust.com");
        when(authenticationFacade.currentUser()).thenReturn(Optional.of(admin));
        when(authenticationFacade.isAdmin()).thenReturn(true);

        String viewName = authPageController.login(null, null, null, new ExtendedModelMap());

        assertThat(viewName).isEqualTo("redirect:/admin");
    }

    @Test
    void registerFormRedirectsAuthenticatedUsersToDashboard() {
        when(authenticationFacade.currentUser()).thenReturn(Optional.of(authenticatedUser()));

        String viewName = authPageController.registerForm(new ExtendedModelMap());

        assertThat(viewName).isEqualTo("redirect:/dashboard");
    }

    @Test
    void registerDoesNotCreateAccountWhenSessionIsAlreadyAuthenticated() {
        when(authenticationFacade.currentUser()).thenReturn(Optional.of(authenticatedUser()));
        RegistrationRequest registrationRequest = new RegistrationRequest(
            "Fresh Traveler",
            "fresh@example.com",
            "+911234567890",
            "Traveler@123"
        );

        String viewName = authPageController.register(
            registrationRequest,
            new BeanPropertyBindingResult(registrationRequest, "registrationForm"),
            new ExtendedModelMap()
        );

        assertThat(viewName).isEqualTo("redirect:/dashboard");
        verifyNoInteractions(appUserService);
    }

    @Test
    void loginPageRemainsAvailableForGuests() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(authenticationFacade.currentUser()).thenReturn(Optional.empty());

        String viewName = authPageController.login(null, "1", null, model);

        assertThat(viewName).isEqualTo("login");
        assertThat(model.get("pageTitle")).isEqualTo("Sign In");
        assertThat(model.get("hasRegistered")).isEqualTo(true);
    }

    @Test
    void registerSendsWelcomeNotificationForNewTraveler() {
        RegistrationRequest registrationRequest = new RegistrationRequest(
            "Fresh Traveler",
            "fresh@example.com",
            "+911234567890",
            "Traveler@123"
        );
        AppUser createdUser = authenticatedUser();
        createdUser.setEmail("fresh@example.com");
        createdUser.setFullName("Fresh Traveler");

        when(authenticationFacade.currentUser()).thenReturn(Optional.empty());
        when(appUserService.registerUser(registrationRequest)).thenReturn(createdUser);

        String viewName = authPageController.register(
            registrationRequest,
            new BeanPropertyBindingResult(registrationRequest, "registrationForm"),
            new ExtendedModelMap()
        );

        assertThat(viewName).isEqualTo("redirect:/login?registered=1");
        verify(notificationService).notifyUser(
            eq(createdUser),
            eq(NotificationCategory.ACCOUNT_CREATED),
            eq("Welcome to Wanderlust Travels"),
            org.mockito.ArgumentMatchers.contains("Your Wanderlust account is ready"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull()
        );
    }

    private AppUser authenticatedUser() {
        AppUser appUser = new AppUser();
        appUser.setId(1L);
        appUser.setFullName("Existing Traveler");
        appUser.setEmail("traveler@example.com");
        appUser.setRole(UserRole.USER);
        appUser.setEnabled(true);
        return appUser;
    }
}
