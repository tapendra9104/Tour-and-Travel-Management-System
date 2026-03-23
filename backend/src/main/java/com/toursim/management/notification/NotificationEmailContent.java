package com.toursim.management.notification;

public record NotificationEmailContent(
    String plainText,
    String htmlText
) {
}
