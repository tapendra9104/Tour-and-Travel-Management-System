package com.toursim.management.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserRepository;
import com.toursim.management.auth.AuthenticationFacade;

import org.springframework.security.crypto.password.PasswordEncoder;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final AuthenticationFacade authenticationFacade;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(
        AuthenticationFacade authenticationFacade,
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.authenticationFacade = authenticationFacade;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String profile(Model model) {
        AppUser user = requireCurrentUser();
        model.addAttribute("currentPage", "profile");
        model.addAttribute("pageTitle", "My Profile");
        model.addAttribute("profileForm", new ProfileForm(user.getFullName(), user.getPhone()));
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping
    public String updateProfile(
        @Valid @ModelAttribute("profileForm") ProfileForm form,
        BindingResult bindingResult,
        Model model
    ) {
        AppUser user = requireCurrentUser();
        model.addAttribute("currentPage", "profile");
        model.addAttribute("pageTitle", "My Profile");
        model.addAttribute("user", user);

        if (bindingResult.hasErrors()) {
            return "profile";
        }

        user.setFullName(form.fullName().trim());
        user.setPhone(form.phone() == null || form.phone().isBlank() ? null : form.phone().trim());
        appUserRepository.save(user);

        model.addAttribute("profileForm", new ProfileForm(user.getFullName(), user.getPhone()));
        model.addAttribute("profileSuccess", "Your profile has been updated.");
        return "profile";
    }

    @PostMapping("/password")
    public String changePassword(
        @Valid @ModelAttribute("passwordForm") PasswordForm form,
        BindingResult bindingResult,
        Model model
    ) {
        AppUser user = requireCurrentUser();
        model.addAttribute("currentPage", "profile");
        model.addAttribute("pageTitle", "My Profile");
        model.addAttribute("user", user);
        model.addAttribute("profileForm", new ProfileForm(user.getFullName(), user.getPhone()));

        if (bindingResult.hasErrors()) {
            return "profile";
        }
        if (!passwordEncoder.matches(form.currentPassword(), user.getPasswordHash())) {
            model.addAttribute("passwordError", "Current password is incorrect.");
            return "profile";
        }
        if (!form.newPassword().equals(form.confirmPassword())) {
            model.addAttribute("passwordError", "New passwords do not match.");
            return "profile";
        }

        user.setPasswordHash(passwordEncoder.encode(form.newPassword()));
        appUserRepository.save(user);
        model.addAttribute("passwordSuccess", "Password changed successfully.");
        return "profile";
    }

    private AppUser requireCurrentUser() {
        return authenticationFacade.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    // --- Form Records ---------------------------------------------------------

    public record ProfileForm(
        @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
        @Size(max = 30) String phone
    ) {}

    public record PasswordForm(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 100)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                 message = "Password must include uppercase, lowercase, and a number")
        String newPassword,
        @NotBlank String confirmPassword
    ) {}
}
