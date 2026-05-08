package com.toursim.management.newsletter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;

@Service
public class NewsletterService {

    private final NewsletterSubscriberRepository repository;
    private final NotificationService notificationService;

    public NewsletterService(NewsletterSubscriberRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    /**
     * Subscribes an email. Re-activates if previously unsubscribed.
     *
     * @return true if newly subscribed, false if already subscribed and active
     */
    @Transactional
    public boolean subscribe(String email) {
        String normalized = email.trim().toLowerCase();
        var existing = repository.findByEmailIgnoreCase(normalized);

        if (existing.isPresent()) {
            NewsletterSubscriber sub = existing.get();
            if (sub.isActive()) {
                return false; // already active - idempotent, no duplicate email
            }
            // Re-activate a previously unsubscribed email
            sub.setActive(true);
            repository.save(sub);
        } else {
            NewsletterSubscriber sub = new NewsletterSubscriber();
            sub.setEmail(normalized);
            repository.save(sub);
        }

        notificationService.notifyGuest(
            normalized,
            normalized,
            NotificationCategory.NEWSLETTER_SUBSCRIBED,
            "Welcome to Wanderlust Travels Newsletter",
            "Thank you for subscribing to the Wanderlust Travels newsletter!\n\n"
                + "You'll receive exclusive travel deals, destination highlights, and trip inspiration.\n\n"
                + "Wanderlust Travels",
            null,
            null
        );
        return true;
    }
}
