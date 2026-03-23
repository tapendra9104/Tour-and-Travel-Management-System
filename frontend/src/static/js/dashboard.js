document.addEventListener("DOMContentLoaded", () => {
  const page = document.getElementById("dashboardPage");
  const loading = document.getElementById("dashboardLoading");
  const content = document.getElementById("dashboardContent");
  const emptyState = document.getElementById("dashboardEmpty");
  const tableBody = document.getElementById("bookingsTableBody");
  const refreshButton = document.getElementById("refreshBookings");
  const exportControls = document.getElementById("exportControls");
  const exportButton = document.getElementById("exportBookings");
  const exportTypeSelect = document.getElementById("exportType");
  const tabButtons = Array.from(document.querySelectorAll("[data-dashboard-tab]"));
  const lookupPanel = document.getElementById("lookupPanel");
  const lookupForm = document.getElementById("lookupForm");
  const lookupError = document.getElementById("lookupError");
  const notificationsList = document.getElementById("notificationsList");
  const customersSection = document.getElementById("customersSection");
  const customersList = document.getElementById("customersList");
  const inquiriesSection = document.getElementById("inquiriesSection");
  const inquiriesList = document.getElementById("inquiriesList");
  const heading = document.getElementById("dashboardHeading");
  const intro = document.getElementById("dashboardIntro");
  const actionWorkspace = document.getElementById("actionWorkspace");
  const actionWorkspaceTitle = document.getElementById("actionWorkspaceTitle");
  const actionWorkspaceDescription = document.getElementById("actionWorkspaceDescription");
  const actionWorkspaceContent = document.getElementById("actionWorkspaceContent");
  const actionWorkspaceClose = document.getElementById("actionWorkspaceClose");
  const actionFeedback = document.getElementById("actionFeedback");

  if (!page || !loading || !content || !emptyState || !tableBody) {
    return;
  }

  const {
    formatCurrency,
    formatDate,
    formatDateTime,
    escapeHtml,
    csrfHeaders,
    readJsonResponse,
    apiErrorMessage,
  } = window.WanderlustUI;

  const state = {
    currentTab: "all",
    snapshot: null,
    lookup: null,
  };

  const statElements = {
    total: document.getElementById("statTotal"),
    pending: document.getElementById("statPending"),
    confirmed: document.getElementById("statConfirmed"),
    cancelled: document.getElementById("statCancelled"),
    revenue: document.getElementById("statRevenue"),
  };

  const exportUrls = {
    bookings: "/api/admin/bookings/export",
    operations: "/api/admin/bookings/operations-export",
    payments: "/api/admin/bookings/payments-export",
    inquiries: "/api/admin/inquiries/export",
    waitlist: "/api/admin/waitlist/export",
  };

  const paymentMethodOptions = [
    "Card",
    "Credit Card",
    "Debit Card",
    "UPI",
    "Net Banking",
    "Bank Transfer",
    "PayPal",
    "Google Pay",
    "Apple Pay",
    "PhonePe",
    "Paytm",
    "Amazon Pay",
    "Venmo",
    "Cash App",
    "Zelle",
    "Wallet",
    "EMI",
    "BNPL",
    "Cash",
    "Cheque",
  ];

  const mealOptions = [
    "Standard",
    "Vegetarian",
    "Vegan",
    "Halal",
    "Kosher",
    "Gluten Free",
    "Jain",
    "Kids Meal",
    "Diabetic Friendly",
  ];

  const transportOptions = ["Flight", "Train", "Bus", "Self Arranged"];
  const transportClassOptions = ["Economy", "Premium Economy", "Business", "First", "Standard", "Sleeper", "Premium"];
  const occasionOptions = ["Honeymoon", "Anniversary", "Birthday", "Proposal", "Babymoon", "Family Vacation"];
  const roomOptions = ["King Bed", "Twin Beds", "Suite", "Sea View", "Private Villa", "Connecting Rooms"];
  const tripStyleOptions = ["Romantic", "Luxury", "Family Friendly", "Adventure", "Wellness", "Cultural", "Slow Paced"];
  const transportStatusOptions = ["Requested", "Quoted", "Confirmed", "Not Required"];
  const operationsPriorityOptions = ["Normal", "High", "VIP"];

  const badge = (text, classes) =>
    `<span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-medium ${classes}">${escapeHtml(text)}</span>`;

  const statusBadge = (status) => {
    if (status === "confirmed") {
      return badge("Confirmed", "bg-primary/10 text-primary");
    }
    if (status === "cancelled") {
      return badge("Cancelled", "bg-destructive/10 text-destructive");
    }
    return badge("Pending", "bg-secondary text-foreground");
  };

  const severityBadge = (severity) => {
    if (severity === "warning") {
      return badge("Warning", "bg-amber-500/10 text-amber-700");
    }
    if (severity === "critical") {
      return badge("Critical", "bg-destructive/10 text-destructive");
    }
    return badge("Info", "bg-card text-muted-foreground");
  };

  const priorityBadge = (priority) => {
    if (priority === "high") {
      return badge("High Priority", "bg-destructive/10 text-destructive");
    }
    if (priority === "medium") {
      return badge("Medium Priority", "bg-amber-500/10 text-amber-700");
    }
    return badge("Normal Priority", "bg-card text-muted-foreground");
  };

  const segmentBadge = (segment) => {
    if (segment === "VIP") {
      return badge("VIP", "bg-primary/10 text-primary");
    }
    if (segment === "Loyal") {
      return badge("Loyal", "bg-emerald-500/10 text-emerald-700");
    }
    return badge("Growing", "bg-card text-muted-foreground");
  };

  const currentMode = () => state.snapshot?.mode || page.dataset.dashboardMode || "anonymous";
  const isAdmin = () => state.snapshot?.admin === true;
  const isAuthenticated = () => state.snapshot?.authenticated === true;

  const filteredBookings = () => {
    if (!state.snapshot || state.currentTab === "all") {
      return state.snapshot?.bookings || [];
    }
    return (state.snapshot?.bookings || []).filter((booking) => booking.status === state.currentTab);
  };

  const findBooking = (id) => (state.snapshot?.bookings || []).find((booking) => String(booking.id) === String(id));
  const findInquiry = (id) => (state.snapshot?.inquiries || []).find((inquiry) => String(inquiry.id) === String(id));

  const bookingAccessPayload = (booking) => ({
    bookingReference: state.lookup?.reference || booking.bookingReference,
    email: state.lookup?.email || booking.email,
  });

  const needsTransportClass = (transportMode) => ["Flight", "Train", "Bus"].includes(String(transportMode || "").trim());

  const selectOptions = (options, selectedValue, emptyLabel = "Select an option") => {
    const currentValue = String(selectedValue ?? "");
    const renderedOptions = options.map((option) => `
      <option value="${escapeHtml(option)}"${option === currentValue ? " selected" : ""}>${escapeHtml(option)}</option>
    `);
    return [`<option value="">${escapeHtml(emptyLabel)}</option>`, ...renderedOptions].join("");
  };

  const inputField = ({ label, name, value = "", type = "text", placeholder = "", helpText = "", required = false, min = "", step = "", maxlength = "" }) => `
    <div class="space-y-2">
      <label class="text-sm font-medium" for="${escapeHtml(name)}">${escapeHtml(label)}</label>
      <input
        id="${escapeHtml(name)}"
        name="${escapeHtml(name)}"
        type="${escapeHtml(type)}"
        value="${escapeHtml(value)}"
        placeholder="${escapeHtml(placeholder)}"
        class="h-11 w-full rounded-lg border border-border bg-background px-4"
        ${required ? "required" : ""}
        ${min ? `min="${escapeHtml(min)}"` : ""}
        ${step ? `step="${escapeHtml(step)}"` : ""}
        ${maxlength ? `maxlength="${escapeHtml(maxlength)}"` : ""}
      >
      ${helpText ? `<p class="text-xs text-muted-foreground">${escapeHtml(helpText)}</p>` : ""}
    </div>
  `;

  const textareaField = ({ label, name, value = "", placeholder = "", helpText = "", rows = 4, maxlength = 500 }) => `
    <div class="space-y-2">
      <label class="text-sm font-medium" for="${escapeHtml(name)}">${escapeHtml(label)}</label>
      <textarea
        id="${escapeHtml(name)}"
        name="${escapeHtml(name)}"
        rows="${rows}"
        maxlength="${maxlength}"
        placeholder="${escapeHtml(placeholder)}"
        class="w-full rounded-lg border border-border bg-background p-4"
      >${escapeHtml(value)}</textarea>
      ${helpText ? `<p class="text-xs text-muted-foreground">${escapeHtml(helpText)}</p>` : ""}
    </div>
  `;

  const selectField = ({ label, name, value = "", options = [], emptyLabel = "Select an option", helpText = "" }) => `
    <div class="space-y-2">
      <label class="text-sm font-medium" for="${escapeHtml(name)}">${escapeHtml(label)}</label>
      <select id="${escapeHtml(name)}" name="${escapeHtml(name)}" class="h-11 w-full rounded-lg border border-border bg-background px-4">
        ${selectOptions(options, value, emptyLabel)}
      </select>
      ${helpText ? `<p class="text-xs text-muted-foreground">${escapeHtml(helpText)}</p>` : ""}
    </div>
  `;

  const checkboxField = ({ label, name, checked = false, helpText = "" }) => `
    <div class="rounded-xl border border-border bg-secondary/40 p-4">
      <label class="flex items-start gap-3 text-sm font-medium">
        <input type="checkbox" name="${escapeHtml(name)}" class="mt-1 rounded border-border" ${checked ? "checked" : ""}>
        <span>
          <span class="block text-foreground">${escapeHtml(label)}</span>
          ${helpText ? `<span class="mt-1 block text-xs font-normal text-muted-foreground">${escapeHtml(helpText)}</span>` : ""}
        </span>
      </label>
    </div>
  `;

  const formActions = (submitLabel, loadingLabel) => `
    <div class="flex flex-wrap items-center gap-3 pt-2">
      <button type="submit" data-loading-label="${escapeHtml(loadingLabel)}" class="inline-flex h-11 items-center justify-center rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90">
        ${escapeHtml(submitLabel)}
      </button>
    </div>
  `;

  const sectionTitle = (title, description) => `
    <div class="space-y-1">
      <p class="text-sm font-semibold text-foreground">${escapeHtml(title)}</p>
      <p class="text-xs text-muted-foreground">${escapeHtml(description)}</p>
    </div>
  `;

  const buildTravelerPreferencesForm = (booking, variant) => {
    const includeExperienceFields = variant === "preferences";
    return `
      <form data-action-form="traveler-preferences" data-variant="${escapeHtml(variant)}" class="space-y-6">
        <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
        ${sectionTitle(
          variant === "edit" ? "Travel and meal setup" : "Traveler and celebration preferences",
          variant === "edit"
            ? "Update food choices, transport arrangements, and support requests for this booking."
            : "Save honeymoon details, room requests, food preferences, transport, and concierge-style notes."
        )}
        <div class="grid gap-4 md:grid-cols-2">
          ${selectField({
            label: "Meal Option",
            name: "mealPreference",
            value: booking.mealPreference || "",
            options: mealOptions,
            emptyLabel: "No meal preference",
            helpText: "Choose the traveler's preferred meal arrangement.",
          })}
          ${textareaField({
            label: "Dietary Notes",
            name: "dietaryRestrictions",
            value: booking.dietaryRestrictions || "",
            placeholder: "Allergies, Jain meal, no onion-garlic, seafood-free, or other dietary notes",
            helpText: "Add food allergies, restrictions, or kitchen notes.",
            rows: 3,
          })}
          ${selectField({
            label: "Travel Option",
            name: "transportMode",
            value: booking.transportMode || "",
            options: transportOptions,
            emptyLabel: "No transport booking needed",
            helpText: "Choose whether the traveler needs plane, train, bus, or self-arranged transport.",
          })}
          <div data-transport-class-wrapper class="${needsTransportClass(booking.transportMode) ? "" : "hidden "}space-y-2">
            <label class="text-sm font-medium" for="transportClass">Seat or Cabin Class</label>
            <select id="transportClass" name="transportClass" class="h-11 w-full rounded-lg border border-border bg-background px-4">
              ${selectOptions(transportClassOptions, booking.transportClass || "", "Standard arrangement")}
            </select>
            <p class="text-xs text-muted-foreground">Capture the preferred class or seating level for the travel team.</p>
          </div>
        </div>
        ${checkboxField({
          label: "Airport or hotel pickup needed",
          name: "transferRequired",
          checked: booking.transferRequired,
          helpText: "Keep this enabled when the traveler needs transfers coordinated before check-in or departure.",
        })}
        <div class="grid gap-4 md:grid-cols-2">
          ${textareaField({
            label: "Travel Support Notes",
            name: "assistanceNotes",
            value: booking.assistanceNotes || "",
            placeholder: "Wheelchair help, child seat, senior support, extra luggage, or accessibility notes",
            helpText: "Share support needs the operations team should prepare for.",
            rows: 3,
          })}
          ${textareaField({
            label: "Trip Notes",
            name: "travelerNotes",
            value: booking.travelerNotes || "",
            placeholder: "Seat preference, airport timing, coach preference, or extra traveler requests",
            helpText: "Capture timing, seat, or route notes for the travel team.",
            rows: 3,
          })}
        </div>
        ${includeExperienceFields ? `
          <div class="grid gap-4 md:grid-cols-2">
            ${selectField({
              label: "Special Occasion",
              name: "occasionType",
              value: booking.occasionType || "",
              options: occasionOptions,
              emptyLabel: "No special occasion",
              helpText: "Track honeymoon, anniversary, birthday, or celebration type.",
            })}
            ${selectField({
              label: "Room Preference",
              name: "roomPreference",
              value: booking.roomPreference || "",
              options: roomOptions,
              emptyLabel: "No room preference",
              helpText: "Save room setup preferences for the property or travel team.",
            })}
            ${selectField({
              label: "Trip Style",
              name: "tripStyle",
              value: booking.tripStyle || "",
              options: tripStyleOptions,
              emptyLabel: "No trip style selected",
              helpText: "Show how the traveler wants the itinerary to feel.",
            })}
            ${textareaField({
              label: "Celebration Notes",
              name: "occasionNotes",
              value: booking.occasionNotes || "",
              placeholder: "Cake, room decor, private dinner, surprise setup, photo shoot, or celebration notes",
              helpText: "Add honeymoon or celebration setup details.",
              rows: 3,
            })}
          </div>
        ` : ""}
        ${formActions(variant === "edit" ? "Save Travel & Meals" : "Save Preferences", "Saving...")}
      </form>
    `;
  };

  const buildTravelerPaymentForm = (booking, adminMode) => `
    <form data-action-form="${adminMode ? "admin-payment" : "traveler-payment"}" class="space-y-6">
      <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
      ${sectionTitle(
        adminMode ? "Record payment" : "Complete payment",
        adminMode
          ? "Capture the traveler payment in the back office without leaving the dashboard."
          : "Pay the current due amount securely using the saved booking details already on file."
      )}
      <div class="grid gap-4 md:grid-cols-2">
        ${selectField({
          label: "Payment Method",
          name: "method",
          value: booking.paymentLastMethod || "",
          options: paymentMethodOptions,
          emptyLabel: "Choose a payment method",
          helpText: "Select the method used by the traveler.",
        })}
        ${inputField({
          label: "Amount",
          name: "amount",
          type: "number",
          value: Number(booking.paymentDueNowAmount || booking.paymentOutstandingAmount || booking.paymentRefundableAmount || 0) > 0
            ? Number(booking.paymentDueNowAmount || booking.paymentOutstandingAmount || booking.paymentRefundableAmount || 0).toFixed(2)
            : "",
          min: "0.01",
          step: "0.01",
          placeholder: "0.00",
          helpText: `Enter the amount in ${booking.paymentCurrency || "USD"}.`,
        })}
      </div>
      <div class="rounded-xl bg-secondary/50 p-4 text-sm text-muted-foreground">
        <p class="font-medium text-foreground">${escapeHtml(booking.tourTitle)}</p>
        <p class="mt-1">Payment status: ${escapeHtml(booking.paymentStatus || "Pending")}</p>
        <p>Outstanding amount: ${escapeHtml(formatCurrency(booking.paymentOutstandingAmount || 0))}</p>
        ${booking.paymentDueDate ? `<p>Due date: ${escapeHtml(formatDate(booking.paymentDueDate))}</p>` : ""}
      </div>
      ${textareaField({
        label: adminMode ? "Internal Payment Note" : "Payment Note",
        name: "note",
        value: "",
        placeholder: adminMode ? "Deposit, balance, invoice number, or finance note" : "Deposit, balance, or traveler reference note",
        helpText: "Add optional context for receipts and finance records.",
        rows: 3,
      })}
      ${formActions(adminMode ? "Record Payment" : booking.paymentNextStage ? `Pay ${booking.paymentNextStage}` : "Pay Now", adminMode ? "Recording..." : "Processing...")}
    </form>
  `;

  const buildTravelerStatusForm = (booking, action) => `
    <form data-action-form="traveler-status" data-operation="${escapeHtml(action)}" class="space-y-6">
      <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
      ${sectionTitle(
        action === "cancel" ? "Cancel booking" : "Reschedule booking",
        action === "cancel"
          ? "Submit the traveler request and keep a clean audit note for support and operations."
          : "Choose a new departure date and add a note for the travel team."
      )}
      ${action === "reschedule" ? inputField({
        label: "New Travel Date",
        name: "date",
        type: "date",
        value: booking.date || "",
        helpText: "Pick a new confirmed departure date for this tour.",
        required: true,
      }) : ""}
      ${textareaField({
        label: action === "cancel" ? "Cancellation Note" : "Reschedule Note",
        name: "note",
        value: "",
        placeholder: action === "cancel" ? "Reason for cancellation or support details" : "Reason for the change or scheduling note",
        helpText: "This note is visible in the booking activity timeline.",
        rows: 4,
      })}
      ${formActions(action === "cancel" ? "Submit Cancellation" : "Submit Reschedule", "Saving...")}
    </form>
  `;

  const buildAdminStatusForm = (booking, status) => {
    const labels = {
      CONFIRMED: "Confirm booking",
      CANCELLED: "Cancel booking",
      PENDING: "Move booking to pending",
    };
    return `
      <form data-action-form="admin-status" class="space-y-6">
        <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
        <input type="hidden" name="status" value="${escapeHtml(status)}">
        ${sectionTitle(
          labels[status] || "Update booking status",
          "Record a traveler-facing note so the status change is clear in the booking timeline."
        )}
        <div class="rounded-xl bg-secondary/50 p-4 text-sm text-muted-foreground">
          <p class="font-medium text-foreground">${escapeHtml(booking.customerName)} | ${escapeHtml(booking.tourTitle)}</p>
          <p class="mt-1">Current status: ${escapeHtml(booking.status)}</p>
          <p>Travel date: ${escapeHtml(formatDate(booking.date))}</p>
        </div>
        ${textareaField({
          label: "Traveler Status Note",
          name: "note",
          value: booking.statusReason || "",
          placeholder: "Confirmed by travel team, cancelled by support, or review note",
          helpText: "This note is sent to the traveler and stored in the booking history.",
          rows: 4,
        })}
        ${formActions(labels[status] || "Save Status", "Saving...")}
      </form>
    `;
  };

  const buildAdminOperationsForm = (booking) => `
    <form data-action-form="admin-operations" class="space-y-6">
      <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
      ${sectionTitle(
        "Operations and logistics",
        "Update transport progress, document readiness, priority, and internal operations notes for this booking."
      )}
      <div class="grid gap-4 md:grid-cols-2">
        ${selectField({
          label: "Transport Mode",
          name: "transportMode",
          value: booking.transportMode || "",
          options: transportOptions,
          emptyLabel: "No transport booking needed",
          helpText: "Capture the preferred travel mode for the traveler.",
        })}
        <div data-transport-class-wrapper class="${needsTransportClass(booking.transportMode) ? "" : "hidden "}space-y-2">
          <label class="text-sm font-medium" for="transportClass">Seat or Cabin Class</label>
          <select id="transportClass" name="transportClass" class="h-11 w-full rounded-lg border border-border bg-background px-4">
            ${selectOptions(transportClassOptions, booking.transportClass || "", "Standard arrangement")}
          </select>
          <p class="text-xs text-muted-foreground">Update the travel class or seating arrangement if it has changed.</p>
        </div>
        ${selectField({
          label: "Transport Status",
          name: "transportStatus",
          value: booking.transportStatus || "",
          options: transportStatusOptions,
          emptyLabel: "Select a transport status",
          helpText: "Track whether transport is requested, quoted, or confirmed.",
        })}
        ${selectField({
          label: "Operations Priority",
          name: "operationsPriority",
          value: booking.operationsPriority || "",
          options: operationsPriorityOptions,
          emptyLabel: "Normal priority",
          helpText: "Use higher priority for VIP or time-sensitive requests.",
        })}
      </div>
      ${checkboxField({
        label: "Travel documents verified",
        name: "documentsVerified",
        checked: booking.documentsVerified,
        helpText: "Mark this when passport or ID checks are complete for the traveler.",
      })}
      ${textareaField({
        label: "Internal Operations Notes",
        name: "operationsNotes",
        value: booking.operationsNotes || "",
        placeholder: "Ticketing note, transfer schedule, supplier follow-up, or internal operations context",
        helpText: "These notes stay with the operations team.",
        rows: 4,
      })}
      ${formActions("Save Operations Update", "Saving...")}
    </form>
  `;

  const buildReminderForm = (booking) => `
    <form data-action-form="admin-reminder" class="space-y-6">
      <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
      ${sectionTitle(
        "Send traveler reminder",
        "Queue an email reminder for the traveler. Leave the message blank to send the standard reminder."
      )}
      <div class="rounded-xl bg-secondary/50 p-4 text-sm text-muted-foreground">
        <p class="font-medium text-foreground">${escapeHtml(booking.customerName)} | ${escapeHtml(booking.tourTitle)}</p>
        <p class="mt-1">Booking reference: ${escapeHtml(booking.bookingReference)}</p>
        <p>Status: ${escapeHtml(booking.status)}</p>
      </div>
      ${textareaField({
        label: "Reminder Message",
        name: "message",
        value: "",
        placeholder: "Add a custom traveler reminder, or leave blank for the standard message",
        helpText: "A blank message uses the professional default reminder email.",
        rows: 5,
        maxlength: 1000,
      })}
      ${formActions("Queue Reminder", "Sending...")}
    </form>
  `;

  const buildRefundForm = (booking) => `
    <form data-action-form="admin-refund" class="space-y-6">
      <input type="hidden" name="bookingId" value="${escapeHtml(booking.id)}">
      ${sectionTitle(
        "Process refund",
        "Record the refund amount and note for finance and traveler communication."
      )}
      <div class="rounded-xl bg-secondary/50 p-4 text-sm text-muted-foreground">
        <p class="font-medium text-foreground">${escapeHtml(booking.customerName)} | ${escapeHtml(booking.tourTitle)}</p>
        <p class="mt-1">Refundable amount: ${escapeHtml(formatCurrency(booking.paymentRefundableAmount || 0))}</p>
      </div>
      <div class="grid gap-4 md:grid-cols-2">
        ${inputField({
          label: "Refund Amount",
          name: "amount",
          type: "number",
          value: Number(booking.paymentRefundableAmount || 0) > 0 ? Number(booking.paymentRefundableAmount || 0).toFixed(2) : "",
          min: "0.01",
          step: "0.01",
          placeholder: "0.00",
          helpText: `Enter the refund amount in ${booking.paymentCurrency || "USD"}.`,
        })}
      </div>
      ${textareaField({
        label: "Refund Note",
        name: "note",
        value: "",
        placeholder: "Traveler-facing or finance note for this refund",
        helpText: "Keep a clear note for support and reconciliation.",
        rows: 4,
      })}
      ${formActions("Process Refund", "Processing...")}
    </form>
  `;

  const buildInquiryStatusForm = (inquiry, status) => `
    <form data-action-form="inquiry-status" class="space-y-6">
      <input type="hidden" name="inquiryId" value="${escapeHtml(inquiry.id)}">
      <input type="hidden" name="status" value="${escapeHtml(status)}">
      ${sectionTitle(
        status === "IN_PROGRESS" ? "Start inquiry follow-up" : "Resolve inquiry",
        "Update the inquiry status and keep an internal note so support history stays organized."
      )}
      <div class="rounded-xl bg-secondary/50 p-4 text-sm text-muted-foreground">
        <p class="font-medium text-foreground">${escapeHtml(inquiry.customerName)} | ${escapeHtml(inquiry.destination)}</p>
        <p class="mt-1">${escapeHtml(inquiry.email)} | ${escapeHtml(inquiry.phone)}</p>
        <p>${escapeHtml(inquiry.travelWindow)} | ${escapeHtml(String(inquiry.travelers))} travelers</p>
      </div>
      ${textareaField({
        label: "Internal Support Note",
        name: "adminNotes",
        value: inquiry.adminNotes || "",
        placeholder: "Follow-up summary, call note, package note, or resolution details",
        helpText: "This note helps the team manage the inquiry professionally.",
        rows: 5,
        maxlength: 1000,
      })}
      ${formActions(status === "IN_PROGRESS" ? "Start Inquiry" : "Resolve Inquiry", "Saving...")}
    </form>
  `;

  const setLoading = (value) => {
    loading.classList.toggle("hidden", !value);
    if (value) {
      content.classList.add("hidden");
      emptyState.classList.add("hidden");
    }
  };

  const updateExportTarget = () => {
    if (!exportButton || !exportTypeSelect) {
      return;
    }
    const exportType = exportTypeSelect.value || "bookings";
    exportButton.href = exportUrls[exportType] || exportUrls.bookings;
    exportButton.textContent = `Export ${exportType.charAt(0).toUpperCase() + exportType.slice(1)}`;
  };

  const updateChrome = () => {
    const mode = currentMode();
    lookupPanel?.classList.toggle("hidden", !(mode === "anonymous" || mode === "guest"));
    exportControls?.classList.toggle("hidden", mode !== "admin");
    customersSection?.classList.toggle("hidden", mode !== "admin");
    inquiriesSection?.classList.toggle("hidden", mode !== "admin");

    const intros = {
      admin: "Manage bookings, payments, operations, inquiries, alerts, and exports from one secure workspace.",
      user: "Track your bookings, traveler preferences, payments, and self-service updates.",
      guest: "Use your booking reference view to review activity and manage your reservation safely.",
      anonymous: "Sign in to see your traveler account, or use a booking reference to look up a reservation.",
    };

    if (heading && state.snapshot?.heading) {
      heading.textContent = state.snapshot.heading;
    }
    if (intro) {
      intro.textContent = intros[mode] || intros.anonymous;
    }
    updateExportTarget();
  };

  const setActionFeedback = (type, message) => {
    if (!actionFeedback) {
      return;
    }
    actionFeedback.className = "mt-4 rounded-lg p-3 text-sm";
    if (type === "error") {
      actionFeedback.classList.add("bg-destructive/10", "text-destructive");
    } else {
      actionFeedback.classList.add("bg-primary/10", "text-primary");
    }
    actionFeedback.textContent = message;
    actionFeedback.classList.remove("hidden");
  };

  const clearActionFeedback = () => {
    if (!actionFeedback) {
      return;
    }
    actionFeedback.classList.add("hidden");
    actionFeedback.textContent = "";
  };

  const closeActionWorkspacePanel = () => {
    if (!actionWorkspace || !actionWorkspaceContent) {
      return;
    }
    actionWorkspace.classList.add("hidden");
    actionWorkspaceContent.innerHTML = "";
    clearActionFeedback();
  };

  const toggleTransportClassForForm = (form) => {
    if (!form) {
      return;
    }
    const transportModeField = form.querySelector('[name="transportMode"]');
    const transportClassWrapper = form.querySelector("[data-transport-class-wrapper]");
    if (!transportModeField || !transportClassWrapper) {
      return;
    }
    const shouldShow = needsTransportClass(transportModeField.value);
    transportClassWrapper.classList.toggle("hidden", !shouldShow);
    const transportClassField = transportClassWrapper.querySelector('[name="transportClass"]');
    if (!shouldShow && transportClassField) {
      transportClassField.value = "";
    }
  };

  const openActionWorkspace = ({ title, description, content: markup }) => {
    if (!actionWorkspace || !actionWorkspaceTitle || !actionWorkspaceDescription || !actionWorkspaceContent) {
      return;
    }
    clearActionFeedback();
    actionWorkspaceTitle.textContent = title;
    actionWorkspaceDescription.textContent = description;
    actionWorkspaceContent.innerHTML = markup;
    actionWorkspace.classList.remove("hidden");
    toggleTransportClassForForm(actionWorkspaceContent.querySelector("form"));
    actionWorkspace.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const timelineMarkup = (items, emptyMessage) => {
    if (!Array.isArray(items) || items.length === 0) {
      return `<div class="rounded-xl bg-secondary/50 p-4 text-sm text-muted-foreground">${escapeHtml(emptyMessage)}</div>`;
    }

    return `
      <div class="space-y-3">
        ${items.map((item) => {
          const actionLabel = String(item.actionType || "activity").replaceAll("_", " ").toLowerCase();
          const statusText = item.newStatus ? `Status: ${String(item.newStatus).toLowerCase()}` : "Status unchanged";
          const noteText = item.note ? `<p class="mt-2 text-sm text-muted-foreground">${escapeHtml(item.note)}</p>` : "";
          return `
            <article class="rounded-xl border border-border bg-secondary/40 p-4">
              <div class="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                <div>
                  <p class="font-medium capitalize">${escapeHtml(actionLabel)}</p>
                  <p class="text-sm text-muted-foreground">${escapeHtml(item.actorName || "System")} | ${escapeHtml(statusText)}</p>
                  ${noteText}
                </div>
                <span class="text-xs text-muted-foreground">${escapeHtml(formatDateTime(item.createdAt))}</span>
              </div>
            </article>
          `;
        }).join("")}
      </div>
    `;
  };

  const openTravelerTimeline = async (booking) => {
    openActionWorkspace({
      title: `Booking Timeline | ${booking.bookingReference}`,
      description: "Review traveler-facing booking activity, status changes, and support notes.",
      content: '<div class="py-8 text-center text-sm text-muted-foreground">Loading booking activity...</div>',
    });

    try {
      const response = await fetch(`/api/bookings/${booking.id}/activity`, {
        method: "POST",
        headers: csrfHeaders({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify(bookingAccessPayload(booking)),
      });
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(apiErrorMessage(data, "Unable to load booking timeline"));
      }
      actionWorkspaceContent.innerHTML = timelineMarkup(data, "No booking activity has been recorded yet.");
    } catch (error) {
      setActionFeedback("error", error.message);
      actionWorkspaceContent.innerHTML = timelineMarkup([], "No booking activity has been recorded yet.");
    }
  };

  const openAdminTimeline = async (booking) => {
    openActionWorkspace({
      title: `Workflow Timeline | ${booking.bookingReference}`,
      description: "Review internal workflow history, traveler changes, and operations activity for this booking.",
      content: '<div class="py-8 text-center text-sm text-muted-foreground">Loading workflow activity...</div>',
    });

    try {
      const response = await fetch(`/api/admin/bookings/${booking.id}/activity`);
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(apiErrorMessage(data, "Unable to load booking timeline"));
      }
      actionWorkspaceContent.innerHTML = timelineMarkup(data, "No workflow activity has been recorded for this booking yet.");
    } catch (error) {
      setActionFeedback("error", error.message);
      actionWorkspaceContent.innerHTML = timelineMarkup([], "No workflow activity has been recorded for this booking yet.");
    }
  };

  const openWorkspaceForBooking = (id, action, status) => {
    const booking = findBooking(id);
    if (!booking) {
      return;
    }

    if (action === "timeline") {
      if (isAdmin()) {
        openAdminTimeline(booking);
      } else {
        openTravelerTimeline(booking);
      }
      return;
    }

    if (action === "edit") {
      openActionWorkspace({
        title: `Edit Travel & Meals | ${booking.bookingReference}`,
        description: "Keep food, transfer, and transport preferences current without leaving the dashboard.",
        content: buildTravelerPreferencesForm(booking, "edit"),
      });
      return;
    }

    if (action === "preferences") {
      openActionWorkspace({
        title: `Manage Preferences | ${booking.bookingReference}`,
        description: "Update traveler, honeymoon, room, food, and support preferences for this booking.",
        content: buildTravelerPreferencesForm(booking, "preferences"),
      });
      return;
    }

    if (action === "pay") {
      openActionWorkspace({
        title: `Payment | ${booking.bookingReference}`,
        description: "Complete traveler payment using the secure booking details already on file.",
        content: buildTravelerPaymentForm(booking, false),
      });
      return;
    }

    if (action === "cancel" || action === "reschedule") {
      openActionWorkspace({
        title: `${action === "cancel" ? "Cancel Booking" : "Reschedule Booking"} | ${booking.bookingReference}`,
        description: action === "cancel"
          ? "Submit a cancellation request and store a clean support note."
          : "Choose a new departure date and share an update for the travel team.",
        content: buildTravelerStatusForm(booking, action),
      });
      return;
    }

    if (action === "payment") {
      openActionWorkspace({
        title: `Record Payment | ${booking.bookingReference}`,
        description: "Capture a traveler payment as part of back-office processing.",
        content: buildTravelerPaymentForm(booking, true),
      });
      return;
    }

    if (action === "refund") {
      openActionWorkspace({
        title: `Refund Payment | ${booking.bookingReference}`,
        description: "Process the traveler refund with a clear finance note.",
        content: buildRefundForm(booking),
      });
      return;
    }

    if (action === "reminder") {
      openActionWorkspace({
        title: `Send Reminder | ${booking.bookingReference}`,
        description: "Queue a traveler reminder without leaving the admin workspace.",
        content: buildReminderForm(booking),
      });
      return;
    }

    if (action === "operations") {
      openActionWorkspace({
        title: `Update Operations | ${booking.bookingReference}`,
        description: "Manage transport, document checks, priority, and internal operations notes.",
        content: buildAdminOperationsForm(booking),
      });
      return;
    }

    if (status) {
      openActionWorkspace({
        title: `Update Status | ${booking.bookingReference}`,
        description: "Record a traveler-facing status note and keep the workflow history clean.",
        content: buildAdminStatusForm(booking, status),
      });
    }
  };

  const openInquiryWorkspace = (id, status) => {
    const inquiry = findInquiry(id);
    if (!inquiry) {
      return;
    }
    openActionWorkspace({
      title: `Inquiry Update | ${inquiry.customerName}`,
      description: "Move the inquiry forward with a clear internal note and a tracked status update.",
      content: buildInquiryStatusForm(inquiry, status),
    });
  };

  const withFormBusyState = async (form, task) => {
    const submitButtons = Array.from(form.querySelectorAll('button[type="submit"]'));
    const originalLabels = submitButtons.map((button) => button.textContent);

    submitButtons.forEach((button) => {
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
      if (button.dataset.loadingLabel) {
        button.textContent = button.dataset.loadingLabel;
      }
    });

    try {
      await task();
    } finally {
      submitButtons.forEach((button, index) => {
        button.disabled = false;
        button.removeAttribute("aria-busy");
        button.textContent = originalLabels[index];
      });
    }
  };

  const parsePositiveAmount = (value, label) => {
    const amount = Number.parseFloat(value);
    if (!Number.isFinite(amount) || amount <= 0) {
      throw new Error(`Enter a valid ${label} amount.`);
    }
    return amount;
  };

  const fieldValue = (formData, form, name) => {
    if (!form.elements.namedItem(name)) {
      return undefined;
    }
    return String(formData.get(name) ?? "").trim();
  };

  const fetchJson = async (url, options, fallbackMessage) => {
    const response = await fetch(url, options);
    const data = await readJsonResponse(response);
    if (!response.ok) {
      throw new Error(apiErrorMessage(data, fallbackMessage));
    }
    return data;
  };

  const renderStats = () => {
    const stats = state.snapshot?.stats || { total: 0, pending: 0, confirmed: 0, cancelled: 0, revenue: 0 };
    statElements.total.textContent = stats.total;
    statElements.pending.textContent = stats.pending;
    statElements.confirmed.textContent = stats.confirmed;
    statElements.cancelled.textContent = stats.cancelled;
    statElements.revenue.textContent = formatCurrency(stats.revenue);

    tabButtons.forEach((button) => {
      const tab = button.dataset.dashboardTab;
      const counts = {
        all: stats.total,
        pending: stats.pending,
        confirmed: stats.confirmed,
        cancelled: stats.cancelled,
      };
      button.textContent = `${tab.charAt(0).toUpperCase() + tab.slice(1)} (${counts[tab] || 0})`;
      button.classList.toggle("bg-secondary", tab === state.currentTab);
    });
  };

  const bookingPreferenceSummary = (booking) => {
    const parts = [];
    if (booking.paymentStatus) {
      parts.push(`Payment: ${booking.paymentStatus}`);
    }
    if (booking.paymentPaidAmount) {
      parts.push(`Paid: ${formatCurrency(booking.paymentPaidAmount)}`);
    }
    if (booking.paymentOutstandingAmount) {
      parts.push(`Outstanding: ${formatCurrency(booking.paymentOutstandingAmount)}`);
    }
    if (booking.paymentDueDate && booking.paymentOutstandingAmount) {
      parts.push(`Due: ${formatDate(booking.paymentDueDate)}`);
    }
    if (booking.paymentRefundableAmount) {
      parts.push(`Refundable: ${formatCurrency(booking.paymentRefundableAmount)}`);
    }
    if (booking.transportMode) {
      const transportText = booking.transportClass
        ? `${booking.transportMode} / ${booking.transportClass}`
        : booking.transportMode;
      parts.push(`Transport: ${transportText}`);
    }
    if (booking.occasionType) {
      parts.push(`Occasion: ${booking.occasionType}`);
    }
    if (booking.occasionNotes) {
      parts.push("Celebration setup saved");
    }
    if (booking.roomPreference) {
      parts.push(`Room: ${booking.roomPreference}`);
    }
    if (booking.tripStyle) {
      parts.push(`Style: ${booking.tripStyle}`);
    }
    if (booking.transportStatus && booking.transportStatus !== "Not Required") {
      parts.push(`Transport status: ${booking.transportStatus}`);
    }
    if (booking.documentsVerified) {
      parts.push("Docs verified");
    }
    if (booking.mealPreference) {
      parts.push(`Meal: ${booking.mealPreference}`);
    }
    if (booking.dietaryRestrictions) {
      parts.push("Dietary notes saved");
    }
    if (booking.transferRequired) {
      parts.push("Transfer requested");
    }
    if (booking.assistanceNotes) {
      parts.push("Support noted");
    }
    if (booking.travelerNotes) {
      parts.push("Trip notes saved");
    }
    if (parts.length === 0) {
      return "";
    }
    return `<p class="mt-1 text-xs text-muted-foreground">${escapeHtml(parts.join(" | "))}</p>`;
  };

  const actionButtons = (booking) => {
    const mode = currentMode();
    const actions = [];

    if (mode === "admin") {
      if (Number(booking.paymentOutstandingAmount || 0) > 0 && booking.status !== "cancelled") {
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-action="payment">Record Payment</button>`);
      }
      if (Number(booking.paymentRefundableAmount || 0) > 0) {
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-action="refund">Refund Payment</button>`);
      }
      if (booking.status !== "confirmed") {
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-status="CONFIRMED">Confirm Booking</button>`);
      }
      if (booking.status !== "cancelled") {
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm text-destructive transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-status="CANCELLED">Cancel Booking</button>`);
      }
      if (booking.status === "cancelled") {
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-status="PENDING">Mark Pending</button>`);
      }
      actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-action="timeline">View Timeline</button>`);
      actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-action="reminder">Send Reminder</button>`);
      actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-admin-action="operations">Update Ops</button>`);
    } else if (mode === "user" || mode === "guest") {
      actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-user-action="timeline">View Timeline</button>`);
      if (booking.status !== "cancelled") {
        if (Number(booking.paymentOutstandingAmount || 0) > 0) {
          actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-user-action="pay">${escapeHtml(booking.paymentNextStage ? `Pay ${booking.paymentNextStage}` : "Pay Now")}</button>`);
        }
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-user-action="edit">Edit Travel & Meals</button>`);
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-user-action="preferences">Manage Preferences</button>`);
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm text-destructive transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-user-action="cancel">Cancel Booking</button>`);
        actions.push(`<button type="button" class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-secondary" data-booking-id="${escapeHtml(booking.id)}" data-user-action="reschedule">Reschedule</button>`);
      }
    }

    if (actions.length === 0) {
      return '<span class="text-xs text-muted-foreground">No actions</span>';
    }

    return `
      <details class="relative inline-block text-left">
        <summary class="inline-flex h-9 w-9 cursor-pointer list-none items-center justify-center rounded-md hover:bg-secondary">
          <span class="text-lg">...</span>
        </summary>
        <div class="absolute right-0 z-10 mt-2 w-56 rounded-md border bg-background p-1 shadow-lg">
          ${actions.join("")}
        </div>
      </details>
    `;
  };

  const renderBookings = () => {
    const allBookings = state.snapshot?.bookings || [];
    const bookings = filteredBookings();

    if (!state.snapshot || allBookings.length === 0) {
      content.classList.add("hidden");
      emptyState.classList.remove("hidden");
      return;
    }

    emptyState.classList.add("hidden");
    content.classList.remove("hidden");

    if (bookings.length === 0) {
      tableBody.innerHTML = `
        <tr class="border-t border-border">
          <td colspan="7" class="px-4 py-10 text-center text-sm text-muted-foreground">
            No bookings match the current filter.
          </td>
        </tr>
      `;
      return;
    }

    tableBody.innerHTML = bookings.map((booking) => {
      const travelerCell = isAdmin()
        ? `
          <p class="font-medium">${escapeHtml(booking.customerName)}</p>
          <p class="text-sm text-muted-foreground">${escapeHtml(booking.email)}</p>
          ${bookingPreferenceSummary(booking)}
        `
        : `
          <p class="font-medium">${escapeHtml(booking.customerName)}</p>
          <p class="text-sm text-muted-foreground">${escapeHtml(booking.bookingReference)}</p>
          ${bookingPreferenceSummary(booking)}
        `;

      return `
        <tr class="border-t border-border">
          <td class="px-4 py-4">
            <p class="font-medium">${escapeHtml(booking.tourTitle)}</p>
            <p class="text-sm text-muted-foreground">${escapeHtml(booking.bookingReference)}</p>
          </td>
          <td class="px-4 py-4">${travelerCell}</td>
          <td class="px-4 py-4">${formatDate(booking.date)}</td>
          <td class="px-4 py-4">${escapeHtml(booking.guests)}</td>
          <td class="px-4 py-4 font-semibold">${formatCurrency(booking.totalPrice)}</td>
          <td class="px-4 py-4">
            ${statusBadge(booking.status)}
            ${booking.statusReason ? `<p class="mt-1 text-xs text-muted-foreground">${escapeHtml(booking.statusReason)}</p>` : ""}
          </td>
          <td class="px-4 py-4 text-right">${actionButtons(booking)}</td>
        </tr>
      `;
    }).join("");
  };

  const renderNotifications = () => {
    const notifications = state.snapshot?.notifications || [];
    if (!notificationsList) {
      return;
    }
    if (notifications.length === 0) {
      notificationsList.innerHTML = '<p class="text-sm text-muted-foreground">No updates yet.</p>';
      return;
    }

    notificationsList.innerHTML = notifications.map((item) => `
      <article class="rounded-xl bg-secondary/50 p-4">
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              ${severityBadge(item.severity)}
              ${badge(item.category.replace("_", " "), "bg-card text-muted-foreground")}
            </div>
            <p class="font-medium">${escapeHtml(item.subject)}</p>
            <p class="text-sm text-muted-foreground">${escapeHtml(item.message)}</p>
          </div>
          <span class="text-xs text-muted-foreground">${escapeHtml(formatDate(item.createdAt))}</span>
        </div>
      </article>
    `).join("");
  };

  const renderCustomers = () => {
    const customers = state.snapshot?.customers || [];
    if (!customersList) {
      return;
    }
    if (!isAdmin() || customers.length === 0) {
      customersList.innerHTML = '<p class="text-sm text-muted-foreground">No customer insights yet.</p>';
      return;
    }

    customersList.innerHTML = customers.map((customer) => `
      <article class="rounded-xl bg-secondary/50 p-4">
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <p class="font-medium">${escapeHtml(customer.customerName)}</p>
              ${segmentBadge(customer.segment)}
            </div>
            <p class="text-sm text-muted-foreground">${escapeHtml(customer.email)}</p>
            <p class="text-sm text-muted-foreground">${escapeHtml(customer.phone || "No phone on file")}</p>
            <p class="text-xs text-muted-foreground">${escapeHtml(String(customer.bookingCount))} bookings | ${formatCurrency(customer.totalSpend)}</p>
          </div>
          <span class="text-xs text-muted-foreground">${escapeHtml(formatDate(customer.latestTravelDate))}</span>
        </div>
      </article>
    `).join("");
  };

  const inquiryStatusActions = (inquiry) => {
    const actions = [];
    if (inquiry.status !== "in_progress") {
      actions.push(`<button type="button" class="rounded-md px-3 py-2 text-sm hover:bg-secondary" data-inquiry-id="${escapeHtml(inquiry.id)}" data-inquiry-status="IN_PROGRESS">Start</button>`);
    }
    if (inquiry.status !== "resolved") {
      actions.push(`<button type="button" class="rounded-md px-3 py-2 text-sm hover:bg-secondary" data-inquiry-id="${escapeHtml(inquiry.id)}" data-inquiry-status="RESOLVED">Resolve</button>`);
    }
    return actions.join("");
  };

  const renderInquiries = () => {
    const inquiries = state.snapshot?.inquiries || [];
    if (!inquiriesList) {
      return;
    }
    if (!isAdmin() || inquiries.length === 0) {
      inquiriesList.innerHTML = '<p class="text-sm text-muted-foreground">No inquiries yet.</p>';
      return;
    }

    inquiriesList.innerHTML = inquiries.map((inquiry) => `
      <article class="rounded-xl bg-secondary/50 p-4">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div class="space-y-2">
            <p class="font-medium">${escapeHtml(inquiry.customerName)} | ${escapeHtml(inquiry.destination)}</p>
            <p class="text-sm text-muted-foreground">${escapeHtml(inquiry.email)} | ${escapeHtml(inquiry.phone)}</p>
            <p class="text-sm text-muted-foreground">${escapeHtml(inquiry.travelWindow)} | ${escapeHtml(String(inquiry.travelers))} travelers | ${escapeHtml(String(inquiry.ageHours))}h old</p>
            <p class="text-sm">${escapeHtml(inquiry.message)}</p>
            ${inquiry.adminNotes ? `<p class="text-xs text-muted-foreground">Admin note: ${escapeHtml(inquiry.adminNotes)}</p>` : ""}
          </div>
          <div class="flex flex-wrap items-center gap-2">
            ${priorityBadge(inquiry.priority)}
            ${badge(inquiry.status.replace("_", " "), "bg-card text-muted-foreground")}
            ${inquiryStatusActions(inquiry)}
          </div>
        </div>
      </article>
    `).join("");
  };

  const render = () => {
    updateChrome();
    renderStats();
    renderBookings();
    renderNotifications();
    renderCustomers();
    renderInquiries();
  };

  const dashboardUrl = () => {
    if (!state.lookup) {
      return "/api/dashboard";
    }
    const params = new URLSearchParams({
      reference: state.lookup.reference,
      email: state.lookup.email,
    });
    return `/api/dashboard?${params.toString()}`;
  };

  const fetchDashboard = async () => {
    setLoading(true);
    lookupError?.classList.add("hidden");

    try {
      const response = await fetch(dashboardUrl());
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(apiErrorMessage(data, "Failed to load dashboard"));
      }
      state.snapshot = data;
      render();
    } catch (error) {
      if (lookupError && state.lookup) {
        lookupError.textContent = error.message;
        lookupError.classList.remove("hidden");
      }
      if (!state.snapshot) {
        state.snapshot = {
          mode: isAuthenticated() ? "user" : "anonymous",
          authenticated: isAuthenticated(),
          admin: false,
          heading: "My Bookings",
          stats: { total: 0, pending: 0, confirmed: 0, cancelled: 0, revenue: 0 },
          bookings: [],
          notifications: [],
          inquiries: [],
          customers: [],
          recommendations: [],
        };
        render();
      }
    } finally {
      setLoading(false);
    }
  };

  const closeMenuForClick = (element) => {
    element?.closest("details")?.removeAttribute("open");
  };

  tabButtons.forEach((button) => {
    button.addEventListener("click", () => {
      state.currentTab = button.dataset.dashboardTab;
      renderBookings();
      renderStats();
    });
  });

  tableBody.addEventListener("click", (event) => {
    const adminStatusButton = event.target.closest("[data-admin-status]");
    if (adminStatusButton) {
      closeMenuForClick(adminStatusButton);
      openWorkspaceForBooking(adminStatusButton.dataset.bookingId, null, adminStatusButton.dataset.adminStatus);
      return;
    }

    const adminActionButton = event.target.closest("[data-admin-action]");
    if (adminActionButton) {
      closeMenuForClick(adminActionButton);
      openWorkspaceForBooking(adminActionButton.dataset.bookingId, adminActionButton.dataset.adminAction, null);
      return;
    }

    const userActionButton = event.target.closest("[data-user-action]");
    if (userActionButton) {
      closeMenuForClick(userActionButton);
      openWorkspaceForBooking(userActionButton.dataset.bookingId, userActionButton.dataset.userAction, null);
    }
  });

  inquiriesList?.addEventListener("click", (event) => {
    const inquiryAction = event.target.closest("[data-inquiry-status]");
    if (!inquiryAction) {
      return;
    }
    openInquiryWorkspace(inquiryAction.dataset.inquiryId, inquiryAction.dataset.inquiryStatus);
  });

  actionWorkspaceContent?.addEventListener("change", (event) => {
    if (event.target.matches('[name="transportMode"]')) {
      toggleTransportClassForForm(event.target.closest("form"));
    }
  });

  actionWorkspaceContent?.addEventListener("submit", async (event) => {
    const form = event.target.closest("[data-action-form]");
    if (!form) {
      return;
    }
    event.preventDefault();
    clearActionFeedback();

    try {
      await withFormBusyState(form, async () => {
        const formData = new FormData(form);
        const formType = form.dataset.actionForm;

        if (formType === "traveler-preferences") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          const payload = {
            ...bookingAccessPayload(booking),
            transferRequired: form.elements.namedItem("transferRequired")
              ? form.querySelector('[name="transferRequired"]').checked
              : undefined,
          };
          const assignField = (name) => {
            const value = fieldValue(formData, form, name);
            if (value !== undefined) {
              payload[name] = value;
            }
          };
          ["mealPreference", "dietaryRestrictions", "occasionType", "occasionNotes", "roomPreference", "tripStyle", "assistanceNotes", "travelerNotes", "transportMode", "transportClass"].forEach(assignField);

          await fetchJson(`/api/bookings/${booking.id}/preferences`, {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify(payload),
          }, "Unable to save traveler preferences");

          setActionFeedback("success", form.dataset.variant === "edit" ? "Travel and food options updated." : "Traveler preferences saved.");
          await fetchDashboard();
          return;
        }

        if (formType === "traveler-payment") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          const data = await fetchJson(`/api/bookings/${booking.id}/payments`, {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              ...bookingAccessPayload(booking),
              method: fieldValue(formData, form, "method"),
              amount: parsePositiveAmount(fieldValue(formData, form, "amount"), "payment"),
              note: fieldValue(formData, form, "note"),
            }),
          }, "Unable to capture payment");

          setActionFeedback("success", data?.message || "Payment captured.");
          await fetchDashboard();
          return;
        }

        if (formType === "traveler-status") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          const operation = form.dataset.operation;
          await fetchJson(`/api/bookings/${booking.id}/${operation}`, {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              ...bookingAccessPayload(booking),
              note: fieldValue(formData, form, "note"),
              date: fieldValue(formData, form, "date"),
            }),
          }, "Unable to update booking");

          setActionFeedback("success", operation === "cancel" ? "Booking cancelled." : "Booking rescheduled.");
          await fetchDashboard();
          return;
        }

        if (formType === "admin-status") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          await fetchJson(`/api/bookings/${booking.id}`, {
            method: "PATCH",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              status: fieldValue(formData, form, "status"),
              note: fieldValue(formData, form, "note"),
            }),
          }, "Unable to update booking");

          setActionFeedback("success", "Booking status updated.");
          await fetchDashboard();
          return;
        }

        if (formType === "admin-reminder") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          await fetchJson(`/api/admin/bookings/${booking.id}/reminder`, {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              message: fieldValue(formData, form, "message"),
            }),
          }, "Unable to send reminder");

          setActionFeedback("success", "Reminder queued for the traveler.");
          await fetchDashboard();
          return;
        }

        if (formType === "admin-operations") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          await fetchJson(`/api/admin/bookings/${booking.id}/operations`, {
            method: "PATCH",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              transportMode: fieldValue(formData, form, "transportMode"),
              transportClass: fieldValue(formData, form, "transportClass"),
              transportStatus: fieldValue(formData, form, "transportStatus"),
              documentsVerified: form.querySelector('[name="documentsVerified"]').checked,
              operationsPriority: fieldValue(formData, form, "operationsPriority"),
              operationsNotes: fieldValue(formData, form, "operationsNotes"),
            }),
          }, "Unable to update booking operations");

          setActionFeedback("success", "Booking operations updated.");
          await fetchDashboard();
          return;
        }

        if (formType === "admin-payment") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          const data = await fetchJson(`/api/admin/bookings/${booking.id}/payments`, {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              method: fieldValue(formData, form, "method"),
              amount: parsePositiveAmount(fieldValue(formData, form, "amount"), "payment"),
              note: fieldValue(formData, form, "note"),
            }),
          }, "Unable to record payment");

          setActionFeedback("success", data?.message || "Payment recorded.");
          await fetchDashboard();
          return;
        }

        if (formType === "admin-refund") {
          const booking = findBooking(formData.get("bookingId"));
          if (!booking) {
            throw new Error("Booking not found.");
          }

          const data = await fetchJson(`/api/admin/bookings/${booking.id}/refund`, {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              amount: parsePositiveAmount(fieldValue(formData, form, "amount"), "refund"),
              note: fieldValue(formData, form, "note"),
            }),
          }, "Unable to process refund");

          setActionFeedback("success", data?.message || "Refund processed.");
          await fetchDashboard();
          return;
        }

        if (formType === "inquiry-status") {
          const inquiry = findInquiry(formData.get("inquiryId"));
          if (!inquiry) {
            throw new Error("Inquiry not found.");
          }

          await fetchJson(`/api/admin/inquiries/${inquiry.id}`, {
            method: "PATCH",
            headers: csrfHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({
              status: fieldValue(formData, form, "status"),
              adminNotes: fieldValue(formData, form, "adminNotes"),
            }),
          }, "Unable to update inquiry");

          setActionFeedback("success", "Inquiry updated.");
          await fetchDashboard();
        }
      });
    } catch (error) {
      setActionFeedback("error", error.message || "Unable to complete this action.");
    }
  });

  lookupForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const reference = document.getElementById("lookupReference")?.value?.trim();
    const email = document.getElementById("lookupEmail")?.value?.trim();
    if (!reference || !email) {
      lookupError.textContent = "Enter both a booking reference and an email address.";
      lookupError.classList.remove("hidden");
      return;
    }
    state.lookup = { reference, email };
    await fetchDashboard();
  });

  refreshButton?.addEventListener("click", async () => {
    clearActionFeedback();
    await fetchDashboard();
  });

  exportTypeSelect?.addEventListener("change", updateExportTarget);
  actionWorkspaceClose?.addEventListener("click", closeActionWorkspacePanel);

  updateExportTarget();
  fetchDashboard();
});
