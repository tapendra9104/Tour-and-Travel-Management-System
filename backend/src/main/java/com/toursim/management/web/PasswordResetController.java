package com.toursim.management.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.toursim.management.auth.PasswordResetService;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // --- Forgot Password ------------------------------------------------------

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Reset Password");
        model.addAttribute("forgotForm", new ForgotPasswordForm(""));
        model.addAttribute("submitted", Boolean.FALSE);
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(
        @Valid @ModelAttribute("forgotForm") ForgotPasswordForm form,
        BindingResult bindingResult,
        Model model
    ) {
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Reset Password");

        if (bindingResult.hasErrors()) {
            return "forgot-password";
        }

        // Always trigger service (it silently ignores unknown emails - no enumeration leak)
        passwordResetService.requestReset(form.email());

        // Always show the same success message regardless of whether email was found
        model.addAttribute("submitted", Boolean.TRUE);
        return "forgot-password";
    }

    // --- Reset Password -------------------------------------------------------

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Set New Password");

        if (!passwordResetService.isValidToken(token)) {
            model.addAttribute("invalidToken", Boolean.TRUE);
            model.addAttribute("resetForm", new ResetPasswordForm("", ""));
            return "reset-password";
        }

        model.addAttribute("invalidToken", Boolean.FALSE);
        model.addAttribute("token", token);
        model.addAttribute("resetForm", new ResetPasswordForm("", ""));
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(
        @RequestParam String token,
        @Valid @ModelAttribute("resetForm") ResetPasswordForm form,
        BindingResult bindingResult,
        Model model
    ) {
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Set New Password");
        model.addAttribute("token", token);

        if (bindingResult.hasErrors()) {
            return "reset-password";
        }
        if (!form.password().equals(form.confirmPassword())) {
            model.addAttribute("confirmError", "Passwords do not match.");
            return "reset-password";
        }

        try {
            passwordResetService.resetPassword(token, form.password());
            return "redirect:/login?passwordReset=1";
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            model.addAttribute("resetError", ex.getReason());
            return "reset-password";
        }
    }

    // --- Form records ---------------------------------------------------------

    public record ForgotPasswordForm(
        @NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Enter a valid email address")
        String email
    ) {}

    public record ResetPasswordForm(
        @NotBlank @Size(min = 8, max = 100)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                 message = "Password must include uppercase, lowercase, and a number")
        String password,
        @NotBlank String confirmPassword
    ) {}
}
