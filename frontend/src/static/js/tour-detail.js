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

  const roundCurrency = (value) => Math.round((Number(value || 0) + Number.EPSILON) * 100) / 100;

  const currentAvailability = () => availability[travelDateSelect.value] || null;

  const currentCharges = () => {
    const guests = Number(guestsSelect.value || 1);
    const subtotal = roundCurrency(pricePerPerson * guests);
    const serviceFee = roundCurrency(subtotal * 0.05);
    const total = roundCurrency(subtotal + serviceFee);
    return { guests, subtotal, serviceFee, total };
  };

  const requiresTransportClass = (transportMode) => /^(flight|plane|train|bus)$/i.test(String(transportMode || "").trim());

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

  const refreshView = () => {
    updatePricing();
    updateDateBadges();
    updateAvailabilityHint();
    updateTransportClassState();
  };

  guestsSelect.addEventListener("change", refreshView);
  travelDateSelect.addEventListener("change", refreshView);
  transportModeSelect?.addEventListener("change", refreshView);

  refreshView();

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    hideBookingError();

    const originalLabel = submitButton.textContent;
    submitButton.disabled = true;
    submitButton.textContent = "Processing...";

    const travelerPreferences = collectTravelerPreferences();

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
        bookingSuccessMessage.textContent = `${data.message} Reference: ${data.waitlistReference}. Redirecting to your dashboard...`;
      } else {
        bookingSuccessTitle.textContent = "Booking Request Received";
        bookingSuccessMessage.textContent = hasTravelerPreferences(travelerPreferences)
          ? `${data.message} Reference: ${data.booking?.bookingReference || ""}. Your food, occasion, room, and travel preferences were saved with this request.`
          : `${data.message} Reference: ${data.booking?.bookingReference || ""}.`;

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
