package com.toursim.management.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.toursim.management.newsletter.NewsletterService;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(
        @Valid @RequestBody SubscribeRequest request
    ) {
        boolean isNew = newsletterService.subscribe(request.email());
        String message = isNew
            ? "You're subscribed! Welcome to Wanderlust Travels."
            : "You're already subscribed. Thank you!";
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK)
            .body(Map.of("message", message));
    }

    public record SubscribeRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email
    ) {}
}
