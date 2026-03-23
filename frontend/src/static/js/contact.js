document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("contactForm");
  const errorBox = document.getElementById("contactError");
  const successBox = document.getElementById("contactSuccess");

  if (!form || !errorBox || !successBox) {
    return;
  }

  const { escapeHtml, csrfHeaders, readJsonResponse, apiErrorMessage } = window.WanderlustUI;
  const submitButton = form.querySelector('button[type="submit"]');

  if (!submitButton) {
    return;
  }

  const dismissMessages = () => {
    errorBox.classList.add("hidden");
    successBox.classList.add("hidden");
    errorBox.textContent = "";
  };

  form.querySelectorAll("input, select, textarea").forEach((field) => {
    field.addEventListener("input", dismissMessages);
    field.addEventListener("change", dismissMessages);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    dismissMessages();
    const originalLabel = submitButton.textContent;
    submitButton.disabled = true;
    submitButton.setAttribute("aria-busy", "true");
    submitButton.textContent = "Sending...";

    const payload = {
      customerName: form.customerName.value.trim(),
      email: form.email.value.trim(),
      phone: form.phone.value.trim(),
      destination: form.destination.value.trim(),
      travelWindow: form.travelWindow.value.trim(),
      travelers: Number(form.travelers.value),
      message: form.message.value.trim(),
    };

    try {
      const response = await fetch("/api/contact", {
        method: "POST",
        headers: csrfHeaders({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify(payload),
      });

      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(apiErrorMessage(data, "Unable to send inquiry"));
      }

      successBox.classList.remove("hidden");
      form.reset();
    } catch (error) {
      errorBox.innerHTML = `<span>${escapeHtml(error.message)}</span>`;
      errorBox.classList.remove("hidden");
    } finally {
      submitButton.disabled = false;
      submitButton.removeAttribute("aria-busy");
      submitButton.textContent = originalLabel;
    }
  });
});
