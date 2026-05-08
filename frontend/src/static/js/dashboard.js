document.addEventListener('DOMContentLoaded', () => {
  const { formatCurrency, formatDate, escapeHtml, csrfHeaders, readJsonResponse, apiErrorMessage } = window.WanderlustUI;
  const page         = document.getElementById('dashboardPage');
  const loading      = document.getElementById('dashboardLoading');
  const content      = document.getElementById('dashboardContent');
  const emptyState   = document.getElementById('dashboardEmpty');
  const tableBody    = document.getElementById('bookingsTableBody');
  const refreshBtn   = document.getElementById('refreshBookings');
  const lookupPanel  = document.getElementById('lookupPanel');
  const lookupForm   = document.getElementById('lookupForm');
  const lookupError  = document.getElementById('lookupError');
  const workspace    = document.getElementById('actionWorkspace');
  const wsTitle      = document.getElementById('actionWorkspaceTitle');
  const wsDesc       = document.getElementById('actionWorkspaceDescription');
  const wsContent    = document.getElementById('actionWorkspaceContent');
  const wsClose      = document.getElementById('actionWorkspaceClose');
  const wsFeedback   = document.getElementById('actionFeedback');
  const notifList    = document.getElementById('notificationsList');
  const bannerEl     = document.getElementById('nextTripBanner');
  const tabs         = Array.from(document.querySelectorAll('[data-dashboard-tab]'));
  if (!page || !loading || !content || !emptyState || !tableBody) return;

  const state = { currentTab: 'all', snapshot: null, lookup: null };

  const mode = () => state.snapshot?.mode || page.dataset.dashboardMode || 'anonymous';

  // -- Tabs ----------------------------------------------------------------
  tabs.forEach(btn => btn.addEventListener('click', () => {
    state.currentTab = btn.dataset.dashboardTab;
    tabs.forEach(b => {
      b.className = b.dataset.dashboardTab === state.currentTab
        ? 'dashboard-tab rounded-lg bg-primary text-primary-foreground px-3 py-1.5 text-xs font-medium'
        : 'dashboard-tab rounded-lg bg-secondary px-3 py-1.5 text-xs font-medium hover:bg-secondary/80';
    });
    renderBookings();
  }));

  // -- Stats ----------------------------------------------------------------
  const renderStats = () => {
    const s = state.snapshot?.stats || {};
    document.getElementById('statTotal').textContent     = s.total     ?? 0;
    document.getElementById('statConfirmed').textContent = s.confirmed ?? 0;
    document.getElementById('statPending').textContent   = s.pending   ?? 0;
  };

  // -- Next-trip banner -----------------------------------------------------
  const renderNextTrip = () => {
    const bookings = (state.snapshot?.bookings || [])
      .filter(b => (b.status || '').toLowerCase() === 'confirmed' && b.date)
      .sort((a, b) => new Date(a.date) - new Date(b.date));
    if (!bookings.length || !bannerEl) return;
    const next = bookings[0];
    const days = Math.ceil((new Date(next.date) - new Date()) / 86400000);
    if (days < 0) return;
    bannerEl.classList.remove('hidden');
    document.getElementById('nextTripTitle').textContent = next.tourTitle || 'Upcoming Trip';
    document.getElementById('nextTripMeta').textContent  = `${formatDate(next.date)} - ${next.guests} traveler${next.guests !== 1 ? 's' : ''}`;
    document.getElementById('nextTripCountdown').textContent = days;
  };

  // -- Status badge ---------------------------------------------------------
  const statusBadge = s => {
    const key = (s || '').toLowerCase();
    const map = { confirmed: 'bg-green-100 text-green-700', cancelled: 'bg-red-100 text-red-700', pending: 'bg-amber-100 text-amber-700', completed: 'bg-blue-100 text-blue-700' };
    const cls = map[key] || 'bg-secondary text-foreground';
    return `<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${cls}">${escapeHtml(s)}</span>`;
  };

  // -- Booking card actions (user only) -------------------------------------
  const userActions = b => {
    const btns = [];
    btns.push(`<button class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-secondary transition-colors" data-booking-id="${escapeHtml(b.id)}" data-user-action="timeline">View Timeline</button>`);
    if ((b.status || '').toLowerCase() !== 'cancelled') {
      btns.push(`<button class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-secondary transition-colors" data-booking-id="${escapeHtml(b.id)}" data-user-action="preferences">Travel Preferences</button>`);
      btns.push(`<button class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-secondary transition-colors" data-booking-id="${escapeHtml(b.id)}" data-user-action="reschedule">Reschedule</button>`);
      btns.push(`<button class="w-full text-left px-3 py-2 text-sm rounded-md text-destructive hover:bg-secondary transition-colors" data-booking-id="${escapeHtml(b.id)}" data-user-action="cancel">Cancel Booking</button>`);
    }
    return `<details class="relative">
      <summary class="inline-flex h-8 w-8 cursor-pointer list-none items-center justify-center rounded-md hover:bg-secondary text-lg" aria-label="Booking actions">...</summary>
      <div class="absolute right-0 z-10 mt-1 w-52 rounded-xl border border-border bg-card p-1 shadow-lg">${btns.join('')}</div>
    </details>`;
  };

  // -- Render bookings -------------------------------------------------------
  const renderBookings = () => {
    const all  = state.snapshot?.bookings || [];
    const list = state.currentTab === 'all' ? all : all.filter(b => (b.status || '').toLowerCase() === state.currentTab);
    if (!state.snapshot || all.length === 0) {
      content.classList.add('hidden'); emptyState.classList.remove('hidden'); return;
    }
    emptyState.classList.add('hidden'); content.classList.remove('hidden');
    if (list.length === 0) {
      tableBody.innerHTML = `<p class="py-8 text-center text-sm text-muted-foreground">No bookings in this category.</p>`; return;
    }
    tableBody.innerHTML = list.map(b => {
      return `
      <article class="rounded-xl border border-border bg-background p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between hover:shadow-sm transition-shadow">
        <div class="flex gap-4 items-start">
          <div class="flex-1 min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <p class="font-semibold truncate">${escapeHtml(b.tourTitle || b.tourId)}</p>
              ${statusBadge(b.status)}
            </div>
            <p class="text-xs text-muted-foreground mt-1">Ref: <span class="font-mono">${escapeHtml(b.bookingReference)}</span></p>
            <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
              <span>Date: ${formatDate(b.date)}</span>
              <span>Guests: ${b.guests} traveler${b.guests !== 1 ? 's' : ''}</span>
              <span class="font-medium text-foreground">${formatCurrency(b.totalPrice)}</span>
              ${b.mealPreference ? `<span>Meal: ${escapeHtml(b.mealPreference)}</span>` : ''}
              ${b.transportMode  ? `<span>Transport: ${escapeHtml(b.transportMode)}</span>` : ''}
            </div>
            ${b.statusReason ? `<p class="mt-1 text-xs text-muted-foreground italic">${escapeHtml(b.statusReason)}</p>` : ''}
          </div>
        </div>
        <div class="self-end sm:self-auto">${userActions(b)}</div>
      </article>`;
    }).join('');
  };

  // -- Notifications ---------------------------------------------------------
  const renderNotifications = () => {
    const items = state.snapshot?.notifications || [];
    if (!notifList) return;
    notifList.innerHTML = items.length === 0
      ? '<p class="text-sm text-muted-foreground">No updates yet.</p>'
      : items.map(n => `
        <article class="rounded-xl bg-secondary/50 p-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="font-medium text-sm">${escapeHtml(n.subject)}</p>
              <p class="text-sm text-muted-foreground">${escapeHtml(n.message)}</p>
            </div>
            <span class="text-xs text-muted-foreground whitespace-nowrap">${formatDate(n.createdAt)}</span>
          </div>
        </article>`).join('');
  };

  // -- Workspace helpers -----------------------------------------------------
  const openWorkspace = ({ title, desc, html }) => {
    wsTitle.textContent = title; wsDesc.textContent = desc;
    wsContent.innerHTML = html;
    wsFeedback.classList.add('hidden'); wsFeedback.textContent = '';
    workspace.classList.remove('hidden');
    workspace.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };
  const closeWorkspace = () => { workspace.classList.add('hidden'); wsContent.innerHTML = ''; };
  const feedback = (ok, msg) => {
    wsFeedback.className = `mb-4 rounded-lg p-3 text-sm ${ok ? 'bg-green-50 text-green-700' : 'bg-destructive/10 text-destructive'}`;
    wsFeedback.textContent = msg; wsFeedback.classList.remove('hidden');
  };

  const field = (id, label, type, val = '', ph = '', extra = '') =>
    `<div class="space-y-1"><label class="text-sm font-medium" for="${id}">${label}</label>
     <input id="${id}" name="${id}" type="${type}" value="${escapeHtml(val)}" placeholder="${escapeHtml(ph)}" ${extra}
            class="h-11 w-full rounded-lg border border-border bg-background px-4 text-sm focus:outline-none focus:ring-2 focus:ring-primary"></div>`;

  const textarea = (id, label, val = '', ph = '') =>
    `<div class="space-y-1"><label class="text-sm font-medium" for="${id}">${label}</label>
     <textarea id="${id}" name="${id}" rows="3" placeholder="${escapeHtml(ph)}"
               class="w-full rounded-lg border border-border bg-background px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary resize-none">${escapeHtml(val)}</textarea></div>`;

  const submitBtn = label =>
    `<button type="submit" class="h-11 rounded-lg bg-primary px-6 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors">${label}</button>`;

  const mealOpts = ['Standard','Vegetarian','Vegan','Halal','Kosher','Gluten Free','Jain','Kids Meal'];
  const transportOpts = ['Flight','Train','Bus','Self Arranged'];

  const selectField = (id, label, options, val = '') =>
    `<div class="space-y-1"><label class="text-sm font-medium" for="${id}">${label}</label>
     <select id="${id}" name="${id}" class="h-11 w-full rounded-lg border border-border bg-background px-4 text-sm focus:outline-none focus:ring-2 focus:ring-primary">
       <option value="">- Select -</option>
       ${options.map(o => `<option value="${escapeHtml(o)}"${o === val ? ' selected' : ''}>${escapeHtml(o)}</option>`).join('')}
     </select></div>`;

  // -- Open booking workspace ------------------------------------------------
  const findBooking = id => (state.snapshot?.bookings || []).find(b => String(b.id) === String(id));

  const accessPayload = b => ({
    bookingReference: state.lookup?.reference || b.bookingReference,
    email: state.lookup?.email || b.email,
  });

  const openBookingWorkspace = async (id, action) => {
    const b = findBooking(id);
    if (!b) return;

    if (action === 'timeline') {
      openWorkspace({ title: `Timeline - ${b.bookingReference}`, desc: 'Your booking activity history.', html: '<div class="py-6 text-center text-sm text-muted-foreground">Loading...</div>' });
      try {
        const res = await fetch(`/api/bookings/${b.id}/activity`, {
          method: 'POST', headers: csrfHeaders({ 'Content-Type': 'application/json' }),
          body: JSON.stringify(accessPayload(b)),
        });
        const data = await readJsonResponse(res);
        if (!res.ok) throw new Error(apiErrorMessage(data, 'Unable to load timeline'));
        wsContent.innerHTML = (!data || data.length === 0)
          ? '<p class="text-sm text-muted-foreground">No activity recorded yet.</p>'
          : data.map(item => `
            <article class="rounded-xl border border-border bg-secondary/40 p-4 mb-3">
              <div class="flex items-start justify-between gap-2">
                <div>
                  <p class="font-medium capitalize text-sm">${escapeHtml(String(item.actionType||'activity').replace(/_/g,' ').toLowerCase())}</p>
                  <p class="text-xs text-muted-foreground">${escapeHtml(item.actorName||'System')}${item.newStatus ? ' - '+item.newStatus : ''}</p>
                  ${item.note ? `<p class="text-sm mt-1">${escapeHtml(item.note)}</p>` : ''}
                </div>
                <span class="text-xs text-muted-foreground whitespace-nowrap">${formatDate(item.createdAt)}</span>
              </div>
            </article>`).join('');
      } catch (e) { feedback(false, e.message); }
      return;
    }

    if (action === 'preferences') {
      openWorkspace({
        title: `Travel Preferences - ${b.bookingReference}`, desc: 'Update meal, transport and occasion details.',
        html: `<form id="prefForm" class="space-y-4">
          <input type="hidden" name="bookingId" value="${escapeHtml(b.id)}">
          <div class="grid gap-4 sm:grid-cols-2">
            ${selectField('mealPreference','Meal Preference', mealOpts, b.mealPreference||'')}
            ${selectField('transportMode','Transport Mode', transportOpts, b.transportMode||'')}
          </div>
          ${textarea('dietaryRestrictions','Dietary Notes', b.dietaryRestrictions||'', 'Allergies, restrictions...')}
          ${textarea('assistanceNotes','Support Needs', b.assistanceNotes||'', 'Wheelchair, child seat...')}
          ${textarea('travelerNotes','Trip Notes', b.travelerNotes||'', 'Seat preference, timing...')}
          <div class="flex gap-3 pt-2">${submitBtn('Save Preferences')}</div>
        </form>`,
      });
      return;
    }

    if (action === 'cancel') {
      openWorkspace({
        title: `Cancel Booking - ${b.bookingReference}`, desc: 'Submit a cancellation request.',
        html: `<form id="cancelForm" class="space-y-4">
          <input type="hidden" name="bookingId" value="${escapeHtml(b.id)}">
          ${textarea('note','Reason for cancellation','','Let us know why you are cancelling...')}
          <div class="flex gap-3 pt-2">
            <button type="submit" class="h-11 rounded-lg bg-destructive px-6 text-sm font-medium text-white hover:bg-destructive/90 transition-colors">Confirm Cancellation</button>
          </div>
        </form>`,
      });
      return;
    }

    if (action === 'reschedule') {
      openWorkspace({
        title: `Reschedule - ${b.bookingReference}`, desc: 'Pick a new travel date.',
        html: `<form id="rescheduleForm" class="space-y-4">
          <input type="hidden" name="bookingId" value="${escapeHtml(b.id)}">
          ${field('date','New Travel Date','date', b.date||'','','required')}
          ${textarea('note','Reschedule Note','','Reason for the change...')}
          <div class="flex gap-3 pt-2">${submitBtn('Submit Reschedule')}</div>
        </form>`,
      });
      return;
    }
  };

  // -- Form submissions ------------------------------------------------------
  wsContent?.addEventListener('submit', async e => {
    const form = e.target;
    e.preventDefault();
    const data    = new FormData(form);
    const bookingId = data.get('bookingId');
    const b       = findBooking(bookingId);
    if (!b) return;
    const btn = form.querySelector('button[type="submit"]');
    const orig = btn.textContent; btn.disabled = true; btn.textContent = 'Saving...';

    const post = async (url, body) => {
      const res = await fetch(url, { method:'POST', headers: csrfHeaders({'Content-Type':'application/json'}), body: JSON.stringify(body) });
      const json = await readJsonResponse(res);
      if (!res.ok) throw new Error(apiErrorMessage(json, 'Action failed'));
      return json;
    };

    try {
      if (form.id === 'prefForm') {
        await post(`/api/bookings/${b.id}/preferences`, {
          ...accessPayload(b),
          mealPreference:     data.get('mealPreference')     || undefined,
          transportMode:      data.get('transportMode')      || undefined,
          dietaryRestrictions:data.get('dietaryRestrictions')|| undefined,
          assistanceNotes:    data.get('assistanceNotes')    || undefined,
          travelerNotes:      data.get('travelerNotes')      || undefined,
        });
        feedback(true, 'Preferences saved!'); await fetchDashboard();
      } else if (form.id === 'cancelForm') {
        await post(`/api/bookings/${b.id}/cancel`, { ...accessPayload(b), note: data.get('note') || '' });
        feedback(true, 'Booking cancelled.'); await fetchDashboard();
      } else if (form.id === 'rescheduleForm') {
        await post(`/api/bookings/${b.id}/reschedule`, { ...accessPayload(b), date: data.get('date'), note: data.get('note') || '' });
        feedback(true, 'Booking rescheduled.'); await fetchDashboard();
      }
    } catch (err) {
      feedback(false, err.message);
    } finally {
      btn.disabled = false; btn.textContent = orig;
    }
  });

  tableBody?.addEventListener('click', e => {
    const btn = e.target.closest('[data-user-action]');
    if (btn) { e.target.closest('details')?.removeAttribute('open'); openBookingWorkspace(btn.dataset.bookingId, btn.dataset.userAction); }
  });

  wsClose?.addEventListener('click', closeWorkspace);

  // -- Render all ------------------------------------------------------------
  const render = () => {
    const m = mode();
    lookupPanel?.classList.toggle('hidden', !(m === 'anonymous' || m === 'guest'));
    if (bannerEl) bannerEl.classList.add('hidden');
    renderStats();
    renderBookings();
    renderNotifications();
    renderNextTrip();
  };

  // -- Fetch -----------------------------------------------------------------
  const dashboardUrl = () => {
    if (!state.lookup) return '/api/dashboard';
    return `/api/dashboard?reference=${encodeURIComponent(state.lookup.reference)}&email=${encodeURIComponent(state.lookup.email)}`;
  };

  const fetchDashboard = async () => {
    loading.classList.remove('hidden'); content.classList.add('hidden'); emptyState.classList.add('hidden');
    try {
      const res  = await fetch(dashboardUrl());
      const data = await readJsonResponse(res);
      if (!res.ok) throw new Error(apiErrorMessage(data, 'Failed to load dashboard'));
      state.snapshot = data; render();
    } catch (err) {
      if (lookupError && state.lookup) { lookupError.textContent = err.message; lookupError.classList.remove('hidden'); }
      if (!state.snapshot) { state.snapshot = { mode:'anonymous', bookings:[], notifications:[], stats:{} }; render(); }
    } finally { loading.classList.add('hidden'); }
  };

  lookupForm?.addEventListener('submit', async e => {
    e.preventDefault();
    const ref   = document.getElementById('lookupReference')?.value?.trim();
    const email = document.getElementById('lookupEmail')?.value?.trim();
    if (!ref || !email) { lookupError.textContent = 'Enter both fields.'; lookupError.classList.remove('hidden'); return; }
    state.lookup = { reference: ref, email };
    await fetchDashboard();
  });

  refreshBtn?.addEventListener('click', fetchDashboard);

  fetchDashboard();
});
