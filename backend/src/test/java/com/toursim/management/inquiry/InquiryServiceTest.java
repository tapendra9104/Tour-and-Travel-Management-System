package com.toursim.management.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toursim.management.auth.AppUserService;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AppUserService appUserService;

    @InjectMocks
    private InquiryService inquiryService;

    @Test
    void createInquiryUsesIndustryFriendlyDefaultsForGeneralSupportRequests() {
        InquiryRequest request = new InquiryRequest(
            "Aayush",
            "traveler@example.com",
            "+998927873891",
            "   ",
            "",
            2,
            "Need help planning a family trip."
        );

        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(invocation -> {
            Inquiry inquiry = invocation.getArgument(0);
            inquiry.setId(42L);
            return inquiry;
        });

        Inquiry inquiry = inquiryService.createInquiry(request, Optional.empty());

        assertThat(inquiry.getDestination()).isEqualTo("General travel planning");
        assertThat(inquiry.getTravelWindow()).isEqualTo("Flexible dates");

        ArgumentCaptor<Inquiry> inquiryCaptor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(inquiryCaptor.capture());
        assertThat(inquiryCaptor.getValue().getDestination()).isEqualTo("General travel planning");
        assertThat(inquiryCaptor.getValue().getTravelWindow()).isEqualTo("Flexible dates");

        verify(notificationService).notifyGuest(
            eq("traveler@example.com"),
            eq("Aayush"),
            eq(NotificationCategory.INQUIRY_RECEIVED),
            eq("We received your travel inquiry"),
            contains("your travel plans"),
            isNull(),
            eq(42L)
        );
    }

    @Test
    void updateStatusSendsTravelerEmailWhenInquiryChanges() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(84L);
        inquiry.setCustomerName("Aayush");
        inquiry.setEmail("traveler@example.com");
        inquiry.setStatus(InquiryStatus.NEW);

        when(inquiryRepository.findById(84L)).thenReturn(Optional.of(inquiry));
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Inquiry updated = inquiryService.updateStatus(84L, InquiryStatus.RESOLVED, "We have shared the itinerary options.");

        assertThat(updated.getStatus()).isEqualTo(InquiryStatus.RESOLVED);
        verify(notificationService).notifyGuest(
            eq("traveler@example.com"),
            eq("Aayush"),
            eq(NotificationCategory.INQUIRY_STATUS_CHANGED),
            eq("Update on your travel inquiry"),
            contains("resolved"),
            isNull(),
            eq(84L)
        );
    }
}
