(() => {
  const escapeHtml = (value) =>
    String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");

  const formatCurrency = (value) => {
    const amount = Number(value ?? 0);
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const formatDate = (value) => {
    if (!value) {
      return "";
    }
    let dateValue = value;
    if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [year, month, day] = value.split("-").map(Number);
      dateValue = new Date(year, month - 1, day);
    }
    return new Intl.DateTimeFormat("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    }).format(new Date(dateValue));
  };

  const formatDateTime = (value) => {
    if (!value) {
      return "";
    }

    return new Intl.DateTimeFormat("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }).format(new Date(value));
  };

  const csrfHeaders = (headers = {}) => {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
    if (!token || !headerName) {
      return headers;
    }
    return {
      ...headers,
      [headerName]: token,
    };
  };

  const readJsonResponse = async (response) => {
    const payload = await response.text();
    if (!payload) {
      return null;
    }

    try {
      return JSON.parse(payload);
    } catch {
      return { error: payload };
    }
  };

  const apiErrorMessage = (payload, fallbackMessage) => {
    if (payload && typeof payload === "object") {
      const details = Object.values(payload.details ?? {})
        .map((message) => String(message || "").trim())
        .filter(Boolean);
      if (details.length > 0) {
        return details.join(" ");
      }

      const error = String(payload.error ?? "").trim();
      if (error) {
        return error;
      }
    }

    return fallbackMessage;
  };

  const parseEmbeddedJson = (source, fallbackValue) => {
    const element = typeof source === "string" ? document.getElementById(source) : source;
    if (!element) {
      return fallbackValue;
    }

    const raw = element.textContent || "";
    if (!raw.trim()) {
      return fallbackValue;
    }

    try {
      return JSON.parse(raw);
    } catch (rawError) {
      try {
        const decoder = document.createElement("textarea");
        decoder.innerHTML = raw;
        return JSON.parse(decoder.value);
      } catch (decodedError) {
        console.error("Failed to parse embedded JSON", {
          elementId: element.id,
          rawError,
          decodedError,
        });
        return fallbackValue;
      }
    }
  };

  const initSheets = () => {
    const syncBodyScrollLock = () => {
      const hasOpenSheet = Boolean(document.querySelector("[data-sheet]:not(.hidden)"));
      document.body.classList.toggle("overflow-hidden", hasOpenSheet);
    };

    const openSheet = (name) => {
      const sheet = document.querySelector(`[data-sheet="${name}"]`);
      if (!sheet) {
        return;
      }

      sheet.classList.remove("hidden");
      sheet.setAttribute("aria-hidden", "false");
      syncBodyScrollLock();

      requestAnimationFrame(() => {
        const panel = sheet.querySelector("[data-sheet-panel]");
        if (!panel) {
          return;
        }

        if (panel.dataset.sheetDirection === "left") {
          panel.classList.remove("-translate-x-full");
        } else {
          panel.classList.remove("translate-x-full");
        }
      });
    };

    const closeSheet = (sheet) => {
      const panel = sheet.querySelector("[data-sheet-panel]");
      if (panel) {
        if (panel.dataset.sheetDirection === "left") {
          panel.classList.add("-translate-x-full");
        } else {
          panel.classList.add("translate-x-full");
        }
      }

      window.setTimeout(() => {
        sheet.classList.add("hidden");
        sheet.setAttribute("aria-hidden", "true");
        syncBodyScrollLock();
      }, 250);
    };

    document.querySelectorAll("[data-sheet-trigger]").forEach((button) => {
      button.addEventListener("click", () => openSheet(button.dataset.sheetTrigger));
    });

    document.querySelectorAll("[data-sheet-close]").forEach((button) => {
      button.addEventListener("click", () => {
        const sheet = button.closest("[data-sheet]");
        if (sheet) {
          closeSheet(sheet);
        }
      });
    });

    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") {
        return;
      }
      document.querySelectorAll("[data-sheet]:not(.hidden)").forEach((sheet) => closeSheet(sheet));
    });

    syncBodyScrollLock();
  };

  const initTabs = () => {
    document.querySelectorAll("[data-tabs]").forEach((tabsRoot) => {
      const triggers = Array.from(tabsRoot.querySelectorAll("[data-tab-target]"));
      const panels = Array.from(tabsRoot.querySelectorAll("[data-tab-panel]"));

      const activate = (value) => {
        triggers.forEach((trigger) => {
          const active = trigger.dataset.tabTarget === value;
          trigger.classList.toggle("bg-background", active);
          trigger.classList.toggle("shadow-sm", active);
        });

        panels.forEach((panel) => {
          panel.classList.toggle("hidden", panel.dataset.tabPanel !== value);
        });
      };

      if (triggers.length > 0) {
        activate(triggers[0].dataset.tabTarget);
      }

      triggers.forEach((trigger) => {
        trigger.addEventListener("click", () => activate(trigger.dataset.tabTarget));
      });
    });
  };

  window.WanderlustUI = {
    escapeHtml,
    formatCurrency,
    formatDate,
    formatDateTime,
    csrfHeaders,
    readJsonResponse,
    apiErrorMessage,
    parseEmbeddedJson,
  };

  document.addEventListener("DOMContentLoaded", () => {
    initSheets();
    initTabs();
  });
})();
