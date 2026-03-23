document.addEventListener("DOMContentLoaded", () => {
  const page = document.getElementById("toursPage");
  const dataElement = document.getElementById("tours-data");
  const container = document.getElementById("toursContainer");
  const noResults = document.getElementById("noResults");
  const resultsCount = document.getElementById("resultsCount");
  const searchInput = document.getElementById("searchQuery");
  const sortBySelect = document.getElementById("sortBy");
  const gridViewBtn = document.getElementById("gridViewBtn");
  const listViewBtn = document.getElementById("listViewBtn");
  const mobileFiltersDot = document.getElementById("mobileFiltersDot");

  if (!page || !dataElement || !container || !searchInput || !sortBySelect) {
    return;
  }

  const { escapeHtml, formatCurrency, parseEmbeddedJson } = window.WanderlustUI;
  const rawTours = parseEmbeddedJson(dataElement, []);
  const tours = rawTours.map((tour) => ({
    ...tour,
    title: String(tour.title || ""),
    destination: String(tour.destination || ""),
    country: String(tour.country || ""),
    category: String(tour.category || ""),
    difficulty: String(tour.difficulty || ""),
    description: String(tour.description || ""),
    duration: String(tour.duration || ""),
    price: Number(tour.price || 0),
    originalPrice: tour.originalPrice == null ? null : Number(tour.originalPrice),
    rating: Number(tour.rating || 0),
    reviews: Number(tour.reviews || 0),
    maxGroupSize: Number(tour.maxGroupSize || 0),
  }));
  const priceUpperBound = Math.max(1000, Math.ceil(Math.max(...tours.map((tour) => tour.price), 0) / 100) * 100);
  const params = new URLSearchParams(window.location.search);
  const normalizeFilterValue = (value) => String(value || "").trim().toLowerCase();
  const durationDays = (tour) => Number.parseInt(String(tour.duration || "").replace(/[^\d]/g, ""), 10) || 0;

  const state = {
    searchQuery: params.get("search") || page.dataset.initialSearch || "",
    selectedCategory: params.get("category") || page.dataset.initialCategory || "All",
    priceMin: Number(params.get("priceMin") || 0),
    priceMax: Number(params.get("priceMax") || priceUpperBound),
    selectedDifficulties: params.getAll("difficulty"),
    sortBy: params.get("sort") || "featured",
    viewMode: params.get("view") || "grid",
  };

  const categoryButtons = Array.from(document.querySelectorAll("[data-category-btn]"));
  const difficultyInputs = Array.from(document.querySelectorAll("[data-difficulty]"));
  const priceMinInputs = Array.from(document.querySelectorAll("[data-price-min]"));
  const priceMaxInputs = Array.from(document.querySelectorAll("[data-price-max]"));
  const priceMinLabels = Array.from(document.querySelectorAll("[data-price-label-min]"));
  const priceMaxLabels = Array.from(document.querySelectorAll("[data-price-label-max]"));
  const priceRanges = Array.from(document.querySelectorAll("[data-price-range]"));
  const clearButtons = Array.from(document.querySelectorAll("[data-clear-filters]"));
  const clearEmptyFilters = document.getElementById("clearEmptyFilters");
  const MIN_PRICE_GAP = 100;

  const hasActiveFilters = () =>
    Boolean(state.searchQuery) ||
    state.selectedCategory !== "All" ||
    state.priceMin > 0 ||
    state.priceMax < priceUpperBound ||
    state.selectedDifficulties.length > 0;

  const updateUrl = () => {
    const next = new URLSearchParams();
    if (state.searchQuery) next.set("search", state.searchQuery);
    if (state.selectedCategory && state.selectedCategory !== "All") next.set("category", state.selectedCategory);
    if (state.priceMin > 0) next.set("priceMin", String(state.priceMin));
    if (state.priceMax < priceUpperBound) next.set("priceMax", String(state.priceMax));
    state.selectedDifficulties.forEach((difficulty) => next.append("difficulty", difficulty));
    if (state.sortBy !== "featured") next.set("sort", state.sortBy);
    if (state.viewMode !== "grid") next.set("view", state.viewMode);

    const query = next.toString();
    const url = query ? `${window.location.pathname}?${query}` : window.location.pathname;
    window.history.replaceState({}, "", url);
  };

  const syncPriceControls = () => {
    priceMinInputs.forEach((input) => {
      input.max = String(priceUpperBound);
      input.value = String(state.priceMin);
    });
    priceMaxInputs.forEach((input) => {
      input.max = String(priceUpperBound);
      input.value = String(state.priceMax);
    });

    priceMinLabels.forEach((label) => {
      label.textContent = formatCurrency(state.priceMin);
    });
    priceMaxLabels.forEach((label) => {
      label.textContent = formatCurrency(state.priceMax);
    });

    const left = priceUpperBound === 0 ? 0 : (state.priceMin / priceUpperBound) * 100;
    const right = priceUpperBound === 0 ? 0 : 100 - (state.priceMax / priceUpperBound) * 100;
    priceRanges.forEach((range) => {
      range.style.left = `${left}%`;
      range.style.right = `${right}%`;
    });
  };

  const syncControls = () => {
    searchInput.value = state.searchQuery;
    sortBySelect.value = state.sortBy;

    categoryButtons.forEach((button) => {
      const active = button.dataset.categoryBtn === state.selectedCategory;
      button.classList.toggle("bg-primary", active);
      button.classList.toggle("text-primary-foreground", active);
      button.classList.toggle("hover:bg-secondary", !active);
    });

    difficultyInputs.forEach((input) => {
      input.checked = state.selectedDifficulties.includes(input.value);
    });

    syncPriceControls();

    clearButtons.forEach((button) => {
      button.classList.toggle("hidden", !hasActiveFilters());
      button.classList.toggle("inline-flex", hasActiveFilters());
    });

    if (mobileFiltersDot) {
      mobileFiltersDot.classList.toggle("hidden", !hasActiveFilters());
    }

    if (gridViewBtn && listViewBtn) {
      const gridActive = state.viewMode === "grid";
      gridViewBtn.classList.toggle("bg-secondary", gridActive);
      gridViewBtn.classList.toggle("bg-background", !gridActive);
      listViewBtn.classList.toggle("bg-secondary", !gridActive);
      listViewBtn.classList.toggle("bg-background", gridActive);
    }
  };

  const renderGridCard = (tour) => {
    const originalPrice = tour.originalPrice ? `<span class="mr-2 text-sm text-muted-foreground line-through">${formatCurrency(tour.originalPrice)}</span>` : "";
    const saveBadge = tour.originalPrice
      ? `<span class="absolute right-4 top-4 inline-flex items-center rounded-full bg-destructive px-3 py-1 text-xs font-medium text-white">Save ${formatCurrency(tour.originalPrice - tour.price)}</span>`
      : "";

    return `
      <a href="/tours/${encodeURIComponent(tour.id)}" class="block">
        <article class="group overflow-hidden rounded-xl border-0 bg-card shadow-sm transition-all duration-300 hover:-translate-y-1 hover:shadow-lg">
          <div class="relative aspect-[4/3] overflow-hidden">
            <img src="${escapeHtml(tour.image)}" alt="${escapeHtml(tour.title)}" class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105">
            <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent"></div>
            <span class="absolute left-4 top-4 inline-flex items-center rounded-full bg-primary px-3 py-1 text-xs font-medium text-primary-foreground">${escapeHtml(tour.category)}</span>
            ${saveBadge}
            <div class="absolute bottom-4 left-4 right-4">
              <p class="text-sm text-white/80">${escapeHtml(tour.destination)}, ${escapeHtml(tour.country)}</p>
            </div>
          </div>
          <div class="p-5">
            <h3 class="mb-2 line-clamp-1 text-lg font-semibold transition-colors group-hover:text-primary">${escapeHtml(tour.title)}</h3>
            <div class="mb-4 flex items-center gap-4 text-sm text-muted-foreground">
              <span>${escapeHtml(tour.duration)}</span>
              <span>Max ${escapeHtml(tour.maxGroupSize)}</span>
            </div>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-1.5">
                <span class="text-accent">★</span>
                <span class="font-medium">${escapeHtml(tour.rating)}</span>
                <span class="text-sm text-muted-foreground">(${escapeHtml(tour.reviews)})</span>
              </div>
              <div class="text-right">
                ${originalPrice}
                <span class="text-lg font-bold text-primary">${formatCurrency(tour.price)}</span>
                <span class="text-sm text-muted-foreground">/person</span>
              </div>
            </div>
          </div>
        </article>
      </a>
    `;
  };

  const renderListCard = (tour) => {
    const originalPrice = tour.originalPrice ? `<span class="mr-2 text-sm text-muted-foreground line-through">${formatCurrency(tour.originalPrice)}</span>` : "";
    return `
      <a href="/tours/${encodeURIComponent(tour.id)}" class="block">
        <article class="overflow-hidden rounded-xl bg-card shadow-sm transition-shadow hover:shadow-lg">
          <div class="flex flex-col md:flex-row">
            <div class="relative md:w-[320px]">
              <img src="${escapeHtml(tour.image)}" alt="${escapeHtml(tour.title)}" class="h-full w-full object-cover md:h-full">
            </div>
            <div class="flex flex-1 flex-col justify-between p-6">
              <div>
                <div class="mb-3 flex flex-wrap gap-2">
                  <span class="inline-flex items-center rounded-full bg-primary px-3 py-1 text-xs font-medium text-primary-foreground">${escapeHtml(tour.category)}</span>
                  <span class="inline-flex items-center rounded-full bg-secondary px-3 py-1 text-xs font-medium">${escapeHtml(tour.difficulty)}</span>
                </div>
                <h3 class="mb-2 text-2xl font-bold">${escapeHtml(tour.title)}</h3>
                <p class="mb-4 text-muted-foreground">${escapeHtml(tour.description)}</p>
                <div class="flex flex-wrap gap-4 text-sm text-muted-foreground">
                  <span>${escapeHtml(tour.destination)}, ${escapeHtml(tour.country)}</span>
                  <span>${escapeHtml(tour.duration)}</span>
                  <span>Max ${escapeHtml(tour.maxGroupSize)} people</span>
                </div>
              </div>
              <div class="mt-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <p class="text-sm text-muted-foreground">Rating</p>
                  <p class="font-medium">${escapeHtml(tour.rating)} (${escapeHtml(tour.reviews)} reviews)</p>
                </div>
                <div class="text-left sm:text-right">
                  ${originalPrice}
                  <span class="text-2xl font-bold text-primary">${formatCurrency(tour.price)}</span>
                  <span class="text-sm text-muted-foreground">/person</span>
                </div>
              </div>
            </div>
          </div>
        </article>
      </a>
    `;
  };

  const render = () => {
    let filteredTours = [...tours];

    if (state.searchQuery) {
      const query = normalizeFilterValue(state.searchQuery);
      filteredTours = filteredTours.filter((tour) =>
        normalizeFilterValue(tour.title).includes(query) ||
        normalizeFilterValue(tour.destination).includes(query) ||
        normalizeFilterValue(tour.country).includes(query) ||
        normalizeFilterValue(tour.category).includes(query) ||
        normalizeFilterValue(tour.description).includes(query)
      );
    }

    if (state.selectedCategory && state.selectedCategory !== "All") {
      filteredTours = filteredTours.filter((tour) =>
        normalizeFilterValue(tour.category) === normalizeFilterValue(state.selectedCategory)
      );
    }

    filteredTours = filteredTours.filter((tour) => tour.price >= state.priceMin && tour.price <= state.priceMax);

    if (state.selectedDifficulties.length > 0) {
      const normalizedDifficulties = state.selectedDifficulties.map(normalizeFilterValue);
      filteredTours = filteredTours.filter((tour) =>
        normalizedDifficulties.includes(normalizeFilterValue(tour.difficulty))
      );
    }

    switch (state.sortBy) {
      case "price-low":
        filteredTours.sort((a, b) => a.price - b.price);
        break;
      case "price-high":
        filteredTours.sort((a, b) => b.price - a.price);
        break;
      case "rating":
        filteredTours.sort((a, b) => b.rating - a.rating);
        break;
      case "duration":
        filteredTours.sort((a, b) => durationDays(a) - durationDays(b));
        break;
      default:
        break;
    }

    resultsCount.textContent = String(filteredTours.length);
    container.className =
      state.viewMode === "grid"
        ? "grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3"
        : "flex flex-col gap-4";

    if (filteredTours.length === 0) {
      container.innerHTML = "";
      container.classList.add("hidden");
      noResults.classList.remove("hidden");
    } else {
      container.classList.remove("hidden");
      noResults.classList.add("hidden");
      container.innerHTML = filteredTours
        .map((tour) => (state.viewMode === "grid" ? renderGridCard(tour) : renderListCard(tour)))
        .join("");
    }

    syncControls();
    updateUrl();
  };

  const resetFilters = () => {
    state.searchQuery = "";
    state.selectedCategory = "All";
    state.priceMin = 0;
    state.priceMax = priceUpperBound;
    state.selectedDifficulties = [];
    state.sortBy = "featured";
    state.viewMode = "grid";
    render();
  };

  searchInput.addEventListener("input", (event) => {
    state.searchQuery = event.target.value;
    render();
  });

  sortBySelect.addEventListener("change", (event) => {
    state.sortBy = event.target.value;
    render();
  });

  gridViewBtn?.addEventListener("click", () => {
    state.viewMode = "grid";
    render();
  });

  listViewBtn?.addEventListener("click", () => {
    state.viewMode = "list";
    render();
  });

  categoryButtons.forEach((button) => {
    button.addEventListener("click", () => {
      const selectedCategory = button.dataset.categoryBtn || "All";
      state.selectedCategory = state.selectedCategory === selectedCategory ? "All" : selectedCategory;
      render();
    });
  });

  difficultyInputs.forEach((input) => {
    input.addEventListener("change", () => {
      state.selectedDifficulties = difficultyInputs
        .filter((field) => field.checked)
        .map((field) => field.value);
      render();
    });
  });

  priceMinInputs.forEach((input) => {
    input.addEventListener("input", (event) => {
      state.priceMin = Math.min(Number(event.target.value), Math.max(0, state.priceMax - MIN_PRICE_GAP));
      syncPriceControls();
    });

    input.addEventListener("change", (event) => {
      state.priceMin = Math.min(Number(event.target.value), Math.max(0, state.priceMax - MIN_PRICE_GAP));
      render();
    });
  });

  priceMaxInputs.forEach((input) => {
    input.addEventListener("input", (event) => {
      state.priceMax = Math.max(Number(event.target.value), state.priceMin + MIN_PRICE_GAP);
      syncPriceControls();
    });

    input.addEventListener("change", (event) => {
      state.priceMax = Math.max(Number(event.target.value), state.priceMin + MIN_PRICE_GAP);
      render();
    });
  });

  clearButtons.forEach((button) => {
    button.addEventListener("click", resetFilters);
  });

  clearEmptyFilters?.addEventListener("click", resetFilters);

  state.priceMin = Math.max(0, Math.min(state.priceMin, priceUpperBound));
  state.priceMax = Math.max(state.priceMin + MIN_PRICE_GAP, Math.min(state.priceMax, priceUpperBound));

  render();
});
