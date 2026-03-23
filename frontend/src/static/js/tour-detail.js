document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("bookingForm");
  const bookingCard = document.getElementById("bookingCard");
  const bookingSuccess = document.getElementById("bookingSuccess");
  const bookingSuccessTitle = document.getElementById("bookingSuccessTitle");
  const bookingSuccessMessage = document.getElementById("bookingSuccessMessage");
  const bookingError = document.getElementById("bookingError");
  const guestsSelect = document.getElementById("guests");
  const travelDateSelect = document.getElementById("travelDate");
  const subtotalLabel = document.getElementById("bookingSubtotalLabel");
  const subtotalValue = document.getElementById("bookingSubtotal");
  const feeValue = document.getElementById("bookingFee");
  const totalValue = document.getElementById("bookingTotal");
  const availabilityHint = document.getElementById("availabilityHint");
  const availabilityDataNode = document.getElementById("tour-availability-data");
  const mealPreferenceSelect = document.getElementById("mealPreference");
  const dietaryRestrictionsInput = document.getElementById("dietaryRestrictions");
  const occasionTypeSelect = document.getElementById("occasionType");
  const occasionNotesInput = document.getElementById("occasionNotes");
  const roomPreferenceSelect = document.getElementById("roomPreference");
  const tripStyleSelect = document.getElementById("tripStyle");
  const transferRequiredInput = document.getElementById("transferRequired");
  const assistanceNotesInput = document.getElementById("assistanceNotes");
  const travelerNotesInput = document.getElementById("travelerNotes");
  const transportModeSelect = document.getElementById("transportMode");
  const transportClassGroup = document.getElementById("transportClassGroup");
  const transportClassSelect = document.getElementById("transportClass");
  const payNowCheckbox = document.getElementById("payNow");
  const paymentFields = document.getElementById("paymentFields");
  const paymentMethodSelect = document.getElementById("paymentMethod");
  const paymentAmountInput = document.getElementById("paymentAmount");
  const paymentNoteInput = document.getElementById("paymentNote");
  const paymentPlanHint = document.getElementById("paymentPlanHint");
  const paymentDueNowValue = document.getElementById("paymentDueNowValue");
  const paymentScheduleNote = document.getElementById("paymentScheduleNote");
  const paymentInlineHint = document.getElementById("paymentInlineHint");

  if (
    !form ||
    !bookingCard ||
    !bookingSuccess ||
    !bookingSuccessTitle ||
    !bookingSuccessMessage ||
    !bookingError ||
    !guestsSelect ||
    !travelDateSelect ||
    !subtotalLabel ||
    !subtotalValue ||
    !feeValue ||
    !totalValue ||
    !availabilityHint
  ) {
    return;
  }

  const {
    formatCurrency,
    escapeHtml,
    csrfHeaders,
    readJsonResponse,
    apiErrorMessage,
    parseEmbeddedJson,
  } = window.WanderlustUI;

  const submitButton = form.querySelector('button[type="submit"]');
  const pricePerPerson = Number(form.dataset.tourPrice || 0);
  const availability = parseEmbeddedJson(availabilityDataNode, {});
  const paymentCurrency = form.dataset.paymentCurrency || "USD";
  const depositRate = Number(form.dataset.paymentDepositRate || 0.3);
  const fullPaymentWindowDays = Number(form.dataset.paymentFullPaymentWindowDays || 45);
  const balanceDueDaysBeforeDeparture = Number(form.dataset.paymentBalanceDueDaysBeforeDeparture || 21);
  let lastSuggestedPaymentAmount = 0;

  const roundCurrency = (value) => Math.round((Number(value || 0) + Number.EPSILON) * 100) / 100;

  const parseLocalDate = (value) => {
    if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      return null;
    }
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, day);
  };

  const formatFriendlyDate = (value) => {
    const date = value instanceof Date ? value : parseLocalDate(value);
    if (!date) {
      return "";
    }
    return new Intl.DateTimeFormat("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    }).format(date);
  };

  const daysUntilDeparture = (value) => {
    const departureDate = parseLocalDate(value);
    if (!departureDate) {
      return null;
    }

    const today = new Date();
    const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    return Math.round((departureDate.getTime() - startOfToday.getTime()) / 86400000);
  };

  const currentAvailability = () => availability[travelDateSelect.value] || null;

  const currentCharges = () => {
    const guests = Number(guestsSelect.value || 1);
    const subtotal = roundCurrency(pricePerPerson * guests);
    const serviceFee = roundCurrency(subtotal * 0.05);
    const total = roundCurrency(subtotal + serviceFee);
    return { guests, subtotal, serviceFee, total };
  };

  const requiresTransportClass = (transportMode) => /^(flight|plane|train|bus)$/i.test(String(transportMode || "").trim());

  const currentPaymentPlan = () => {
    const { guests, total } = currentCharges();
    const details = currentAvailability();

    if (details && (details.soldOut || guests > details.remaining)) {
      return {
        paymentAllowed: false,
        dueNow: 0,
        outstandingTotal: total,
        nextStage: "Waitlist",
        summary: "This departure will be handled as a waitlist request, so no payment is collected now.",
        hint: "Waitlist requests are saved first. Payment is only collected after seats open and the trip is confirmed.",
      };
    }

    const remainingDays = daysUntilDeparture(travelDateSelect.value);
    if (remainingDays === null) {
      return {
        paymentAllowed: true,
        dueNow: 0,
        outstandingTotal: total,
        nextStage: "Payment",
        summary: "Choose a travel date to see the suggested payment schedule.",
        hint: "Pick a departure date to estimate the deposit or full payment amount.",
      };
    }

    if (remainingDays <= fullPaymentWindowDays) {
      return {
        paymentAllowed: true,
        dueNow: total,
        outstandingTotal: total,
        nextStage: "Full Payment",
        summary: `This trip departs within ${fullPaymentWindowDays} days, so the full amount is due today.`,
        hint: `Suggested payment today: ${formatCurrency(total)}. No additional balance remains after full payment.`,
      };
    }

    const depositAmount = roundCurrency(total * depositRate);
    const balanceDueDate = parseLocalDate(travelDateSelect.value);
    if (balanceDueDate) {
      balanceDueDate.setDate(balanceDueDate.getDate() - balanceDueDaysBeforeDeparture);
    }

    return {
      paymentAllowed: true,
      dueNow: depositAmount,
      outstandingTotal: total,
      nextStage: "Deposit",
      summary: `A ${Math.round(depositRate * 100)}% deposit is suggested today to secure the booking request.`,
      hint: `Suggested payment today: ${formatCurrency(depositAmount)}. The remaining balance is due by ${formatFriendlyDate(balanceDueDate)}.`,
    };
  };

  const showBookingError = (message) => {
    bookingError.innerHTML = `<span>${escapeHtml(message)}</span>`;
    bookingError.classList.remove("hidden");
    bookingError.classList.add("flex");
  };

  const hideBookingError = () => {
    bookingError.textContent = "";
    bookingError.classList.add("hidden");
    bookingError.classList.remove("flex");
  };

  const collectTravelerPreferences = () => {
    const transportMode = transportModeSelect?.value?.trim() || "";
    return {
      mealPreference: mealPreferenceSelect?.value?.trim() || "",
      dietaryRestrictions: dietaryRestrictionsInput?.value?.trim() || "",
      occasionType: occasionTypeSelect?.value?.trim() || "",
      occasionNotes: occasionNotesInput?.value?.trim() || "",
      roomPreference: roomPreferenceSelect?.value?.trim() || "",
      tripStyle: tripStyleSelect?.value?.trim() || "",
      assistanceNotes: assistanceNotesInput?.value?.trim() || "",
      transferRequired: Boolean(transferRequiredInput?.checked),
      travelerNotes: travelerNotesInput?.value?.trim() || "",
      transportMode,
      transportClass: requiresTransportClass(transportMode) ? (transportClassSelect?.value?.trim() || "") : "",
    };
  };

  const hasTravelerPreferences = (preferences) => Boolean(
    preferences.mealPreference ||
    preferences.dietaryRestrictions ||
    preferences.occasionType ||
    preferences.occasionNotes ||
    preferences.roomPreference ||
    preferences.tripStyle ||
    preferences.transferRequired ||
    preferences.assistanceNotes ||
    preferences.travelerNotes ||
    preferences.transportMode ||
    preferences.transportClass
  );

  const collectPaymentRequest = (paymentPlan) => {
    if (!payNowCheckbox?.checked || !paymentPlan.paymentAllowed) {
      return null;
    }

    const method = paymentMethodSelect?.value?.trim() || "";
    if (!method) {
      showBookingError("Choose a payment method or leave the payment section unchecked to pay later.");
      return undefined;
    }

    const amount = Number.parseFloat(paymentAmountInput?.value || "");
    if (!Number.isFinite(amount) || amount <= 0) {
      showBookingError("Enter a valid payment amount greater than zero.");
      return undefined;
    }

    if (amount > paymentPlan.outstandingTotal) {
      showBookingError(`Payment amount cannot exceed ${formatCurrency(paymentPlan.outstandingTotal)} for this booking.`);
      return undefined;
    }

    return {
      method,
      amount: roundCurrency(amount),
      note: paymentNoteInput?.value?.trim() || paymentPlan.nextStage,
    };
  };

  const updateDateBadges = () => {
    document.querySelectorAll("[data-availability-date]").forEach((card) => {
      const date = card.dataset.availabilityDate;
      const badge = card.querySelector("[data-availability-badge]");
      if (!badge) {
        return;
      }

      const details = availability[date];
      if (!details) {
        badge.textContent = "Available";
        badge.className = "inline-flex items-center rounded-full bg-card px-3 py-1 text-xs font-medium";
        return;
      }

      if (details.soldOut) {
        badge.textContent = "Sold Out";
        badge.className = "inline-flex items-center rounded-full bg-destructive/10 px-3 py-1 text-xs font-medium text-destructive";
        return;
      }

      if (details.remaining <= 3) {
        badge.textContent = `${details.remaining} left`;
        badge.className = "inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary";
        return;
      }

      badge.textContent = "Available";
      badge.className = "inline-flex items-center rounded-full bg-card px-3 py-1 text-xs font-medium";
    });
  };

  const updatePricing = () => {
    const { guests, subtotal, serviceFee, total } = currentCharges();

    subtotalLabel.textContent = `${formatCurrency(pricePerPerson)} x ${guests} ${guests === 1 ? "traveler" : "travelers"}`;
    subtotalValue.textContent = formatCurrency(subtotal);
    feeValue.textContent = formatCurrency(serviceFee);
    totalValue.textContent = formatCurrency(total);
  };

  const updateAvailabilityHint = () => {
    const details = currentAvailability();
    const { guests } = currentCharges();

    if (!details) {
      availabilityHint.textContent = "Live availability is checked before your booking request is submitted.";
      submitButton.textContent = "Book Now";
      return;
    }

    if (details.soldOut) {
      availabilityHint.textContent = "This departure is sold out. You can still submit your request to join the waitlist.";
      submitButton.textContent = "Join Waitlist";
      return;
    }

    if (guests > details.remaining) {
      availabilityHint.textContent = `Only ${details.remaining} seat${details.remaining === 1 ? "" : "s"} remain on this date. Larger groups will be saved to the waitlist.`;
      submitButton.textContent = "Request & Waitlist";
      return;
    }

    availabilityHint.textContent = `${details.remaining} seat${details.remaining === 1 ? "" : "s"} currently available on this departure.`;
    submitButton.textContent = "Book Now";
  };

  const updateTransportClassState = () => {
    if (!transportClassGroup || !transportClassSelect || !transportModeSelect) {
      return;
    }

    const needsClass = requiresTransportClass(transportModeSelect.value);
    transportClassGroup.classList.toggle("hidden", !needsClass);
    transportClassSelect.disabled = !needsClass;
    if (!needsClass) {
      transportClassSelect.value = "";
    }
  };

  const updatePaymentSection = () => {
    if (!paymentPlanHint || !paymentDueNowValue || !paymentScheduleNote) {
      return;
    }

    const paymentPlan = currentPaymentPlan();
    paymentPlanHint.textContent = paymentPlan.summary;
    paymentDueNowValue.textContent = paymentPlan.paymentAllowed ? formatCurrency(paymentPlan.dueNow) : `${paymentCurrency} 0`;
    paymentScheduleNote.textContent = paymentPlan.hint;

    if (paymentInlineHint) {
      paymentInlineHint.textContent = paymentPlan.paymentAllowed
        ? `Suggested ${paymentPlan.nextStage.toLowerCase()}: ${formatCurrency(paymentPlan.dueNow)}. You may enter a custom amount up to ${formatCurrency(paymentPlan.outstandingTotal)}.`
        : "Waitlist requests are saved first. No payment will be collected unless seats become available.";
    }

    if (payNowCheckbox) {
      if (!paymentPlan.paymentAllowed) {
        payNowCheckbox.checked = false;
        payNowCheckbox.disabled = true;
      } else {
        payNowCheckbox.disabled = false;
      }
    }

    if (paymentAmountInput) {
      const currentValue = paymentAmountInput.value.trim();
      const numericValue = currentValue ? Number.parseFloat(currentValue) : NaN;
      const shouldReplace = !currentValue || Number.isNaN(numericValue) || Math.abs(numericValue - lastSuggestedPaymentAmount) < 0.009;
      if (shouldReplace) {
        paymentAmountInput.value = paymentPlan.paymentAllowed && paymentPlan.dueNow > 0
          ? paymentPlan.dueNow.toFixed(2)
          : "";
      }
      paymentAmountInput.max = paymentPlan.outstandingTotal > 0 ? paymentPlan.outstandingTotal.toFixed(2) : "";
      lastSuggestedPaymentAmount = paymentPlan.paymentAllowed ? paymentPlan.dueNow : 0;
    }

    const paymentFieldsVisible = Boolean(payNowCheckbox?.checked) && paymentPlan.paymentAllowed;
    if (paymentFields) {
      paymentFields.classList.toggle("hidden", !paymentFieldsVisible);
    }
    if (paymentMethodSelect) {
      paymentMethodSelect.disabled = !paymentFieldsVisible;
    }
    if (paymentAmountInput) {
      paymentAmountInput.disabled = !paymentFieldsVisible;
    }
    if (paymentNoteInput) {
      paymentNoteInput.disabled = !paymentFieldsVisible;
    }
  };

  const refreshView = () => {
    updatePricing();
    updateDateBadges();
    updateAvailabilityHint();
    updateTransportClassState();
    updatePaymentSection();
  };

  guestsSelect.addEventListener("change", refreshView);
  travelDateSelect.addEventListener("change", refreshView);
  transportModeSelect?.addEventListener("change", refreshView);
  payNowCheckbox?.addEventListener("change", refreshView);
  paymentAmountInput?.addEventListener("input", () => {
    hideBookingError();
  });

  refreshView();

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    hideBookingError();

    const originalLabel = submitButton.textContent;
    submitButton.disabled = true;
    submitButton.textContent = "Processing...";

    const travelerPreferences = collectTravelerPreferences();
    const paymentPlan = currentPaymentPlan();
    const paymentRequest = collectPaymentRequest(paymentPlan);

    if (paymentRequest === undefined) {
      submitButton.disabled = false;
      submitButton.textContent = originalLabel;
      return;
    }

    const payload = {
      tourId: form.dataset.tourId,
      customerName: form.customerName.value,
      email: form.email.value,
      phone: form.phone.value,
      guests: Number(form.guests.value),
      date: form.date.value,
      mealPreference: travelerPreferences.mealPreference,
      dietaryRestrictions: travelerPreferences.dietaryRestrictions,
      occasionType: travelerPreferences.occasionType,
      occasionNotes: travelerPreferences.occasionNotes,
      roomPreference: travelerPreferences.roomPreference,
      tripStyle: travelerPreferences.tripStyle,
      assistanceNotes: travelerPreferences.assistanceNotes,
      transferRequired: travelerPreferences.transferRequired,
      travelerNotes: travelerPreferences.travelerNotes,
      transportMode: travelerPreferences.transportMode,
      transportClass: travelerPreferences.transportClass,
    };

    try {
      const response = await fetch("/api/bookings", {
        method: "POST",
        headers: csrfHeaders({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify(payload),
      });

      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(apiErrorMessage(data, "Failed to submit booking"));
      }

      bookingCard.classList.add("hidden");
      bookingSuccess.classList.remove("hidden");

      if (data.outcome === "waitlisted") {
        bookingSuccessTitle.textContent = "You're on the Waitlist";
        bookingSuccessMessage.textContent = `${data.message} Reference: ${data.waitlistReference}. Payment is not collected for waitlist requests. Redirecting to your dashboard...`;
      } else {
        bookingSuccessTitle.textContent = "Booking Request Received";
        bookingSuccessMessage.textContent = hasTravelerPreferences(travelerPreferences)
          ? `${data.message} Reference: ${data.booking?.bookingReference || ""}. Your food, occasion, room, and travel preferences were saved with this request.`
          : `${data.message} Reference: ${data.booking?.bookingReference || ""}.`;

        if (paymentRequest) {
          const paymentResponse = await fetch(`/api/bookings/${data.booking.id}/payments`, {
            method: "POST",
            headers: csrfHeaders({
              "Content-Type": "application/json",
            }),
            body: JSON.stringify({
              bookingReference: data.booking.bookingReference,
              email: data.booking.email || form.email.value,
              ...paymentRequest,
            }),
          });

          const paymentData = await readJsonResponse(paymentResponse);
          if (paymentResponse.ok) {
            bookingSuccessMessage.textContent += ` Payment captured successfully. Receipt: ${paymentData.receiptNumber}.`;
          } else {
            bookingSuccessMessage.textContent += ` Payment was not captured yet: ${apiErrorMessage(paymentData, "Unable to process payment")}. You can complete it from your dashboard.`;
          }
        } else {
          bookingSuccessMessage.textContent += " You can complete payment from your dashboard any time.";
        }

        bookingSuccessMessage.textContent += " Redirecting to your dashboard...";
      }

      window.setTimeout(() => {
        window.location.href = "/dashboard";
      }, 1800);
    } catch (error) {
      showBookingError(error.message);
      submitButton.disabled = false;
      submitButton.textContent = originalLabel;
      return;
    }

    submitButton.disabled = false;
    refreshView();
  });
});
