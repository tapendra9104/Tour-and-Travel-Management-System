package com.toursim.management.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toursim.management.auth.AuthenticationFacade;
import com.toursim.management.inquiry.InquiryRequest;
import com.toursim.management.inquiry.InquiryResponse;
import com.toursim.management.inquiry.InquiryService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/contact")
public class InquiryApiController {

    private final InquiryService inquiryService;
    private final AuthenticationFacade authenticationFacade;

    public InquiryApiController(InquiryService inquiryService, AuthenticationFacade authenticationFacade) {
        this.inquiryService = inquiryService;
        this.authenticationFacade = authenticationFacade;
    }

    @PostMapping
    public ResponseEntity<InquiryResponse> createInquiry(@Valid @RequestBody InquiryRequest request) {
        InquiryResponse response = InquiryResponse.from(inquiryService.createInquiry(request, authenticationFacade.currentUser()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
