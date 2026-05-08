(() => {
  const csrf = () => ({
    token: document.querySelector('meta[name="_csrf"]')?.content,
    header: document.querySelector('meta[name="_csrf_header"]')?.content,
  });

  const authHeaders = (extra = {}) => {
    const { token, header } = csrf();
    return {
      'Content-Type': 'application/json',
      ...(token && header ? { [header]: token } : {}),
      ...extra,
    };
  };

  const esc = value => String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

  const normalizeStatus = status => String(status ?? '').trim().toUpperCase().replace(/\s+/g, '_');
  const displayStatus = status => String(status || '-').replace(/_/g, ' ');
  const asNumber = value => Number(value ?? 0);
  const hasMoney = value => asNumber(value) > 0.009;

  const fmt = {
    currency: value => new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 0,
    }).format(Number(value ?? 0)),
    date: value => {
      if (!value) return '-';
      const date = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
        ? new Date(`${value}T00:00:00`)
        : new Date(value);
      return Number.isNaN(date.getTime())
        ? '-'
        : new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(date);
    },
    dateTime: value => {
      if (!value) return '-';
      const date = new Date(value);
      return Number.isNaN(date.getTime())
        ? '-'
        : new Intl.DateTimeFormat('en-US', {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
          hour: 'numeric',
          minute: '2-digit',
        }).format(date);
    },
    status: status => {
      const key = normalizeStatus(status);
      const classes = {
        PENDING: 'bg-amber-100 text-amber-700',
        CONFIRMED: 'bg-green-100 text-green-700',
        CANCELLED: 'bg-red-100 text-red-700',
        NEW: 'bg-blue-100 text-blue-700',
        IN_PROGRESS: 'bg-amber-100 text-amber-700',
        RESOLVED: 'bg-green-100 text-green-700',
        WAITLISTED: 'bg-amber-100 text-amber-700',
        NOTIFIED: 'bg-blue-100 text-blue-700',
        CONVERTED: 'bg-green-100 text-green-700',
        ACTIVE: 'bg-green-100 text-green-700',
        INACTIVE: 'bg-slate-100 text-slate-700',
      };
      return badge(displayStatus(key), classes[key] || 'bg-slate-100 text-slate-700');
    },
    payment: status => {
      const key = normalizeStatus(status);
      const classes = {
        PAID_IN_FULL: 'bg-green-100 text-green-700',
        PARTIALLY_PAID: 'bg-blue-100 text-blue-700',
        DEPOSIT_DUE: 'bg-amber-100 text-amber-700',
        BALANCE_DUE: 'bg-amber-100 text-amber-700',
        OVERDUE: 'bg-red-100 text-red-700',
        REFUND_DUE: 'bg-purple-100 text-purple-700',
        PARTIALLY_REFUNDED: 'bg-slate-100 text-slate-700',
        REFUNDED: 'bg-slate-100 text-slate-700',
        CANCELLED: 'bg-slate-100 text-slate-700',
        UNPAID: 'bg-red-100 text-red-700',
      };
      return badge(displayStatus(key), classes[key] || 'bg-slate-100 text-slate-700');
    },
  };

  let allBookings = [];
  let allTours = [];
  let allInquiries = [];
  let confirmCallback = null;

  const badge = (text, classes) =>
    `<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${classes}">${esc(text)}</span>`;

  const requestJson = async (url, options = {}, fallback = 'Request failed') => {
    const { headers = {}, ...rest } = options;
    const response = await fetch(url, { ...rest, headers: authHeaders(headers) });
    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const details = Object.values(payload?.details ?? {}).filter(Boolean).join(' ');
      throw new Error(details || payload?.message || payload?.error || fallback);
    }
    return payload;
  };

  const feedback = (msg, ok = true) => {
    const el = document.getElementById('admin-feedback');
    if (!el) return;
    el.textContent = msg;
    el.className = `mb-4 rounded-lg p-3 text-sm ${ok ? 'bg-green-50 text-green-700' : 'bg-destructive/10 text-destructive'}`;
    el.classList.remove('hidden');
    setTimeout(() => el.classList.add('hidden'), 4000);
  };

  const findBooking = id => allBookings.find(b => String(b.id) === String(id));
  const findInquiry = id => allInquiries.find(i => String(i.id) === String(id));
  const selectOptions = (options, selected = '', emptyLabel = 'Select') =>
    [`<option value="">${esc(emptyLabel)}</option>`, ...options.map(option =>
      `<option value="${esc(option)}"${String(option) === String(selected ?? '') ? ' selected' : ''}>${esc(option)}</option>`
    )].join('');

  const modalContent = () => document.getElementById('admin-action-content');

  const openActionModal = (title, html) => {
    document.getElementById('admin-action-title').textContent = title;
    modalContent().innerHTML = html;
    document.getElementById('admin-action-modal').classList.remove('hidden');
  };

  const closeActionModal = () => {
    document.getElementById('admin-action-modal').classList.add('hidden');
    modalContent().innerHTML = '';
  };

  const activateTab = tabName => {
    document.querySelectorAll('.admin-tab').forEach(btn => {
      const active = btn.dataset.tab === tabName;
      btn.className = active
        ? 'admin-tab active-tab shrink-0 px-4 py-2 rounded-lg text-sm font-medium bg-primary text-primary-foreground'
        : 'admin-tab shrink-0 px-4 py-2 rounded-lg text-sm font-medium bg-card border border-border hover:bg-secondary transition-colors';
    });
    document.querySelectorAll('.admin-panel').forEach(panel => {
      panel.classList.toggle('hidden', panel.id !== `tab-${tabName}`);
    });
  };

  const initTabs = () => {
    document.querySelectorAll('.admin-tab').forEach(btn => {
      btn.addEventListener('click', () => activateTab(btn.dataset.tab));
    });
  };

  const loadStats = async () => {
    try {
      const s = await requestJson('/api/admin/stats', {}, 'Unable to load admin stats');
      document.getElementById('s-total').textContent = s.totalBookings ?? '-';
      document.getElementById('s-revenue').textContent = fmt.currency(s.totalRevenue);
      document.getElementById('s-pending').textContent = s.pendingBookings ?? '-';
      document.getElementById('s-inquiries').textContent = s.openInquiries ?? '-';
      document.getElementById('s-tours').textContent = s.totalTours ?? '-';
    } catch {
      // Keep the panel usable if the summary endpoint is temporarily unavailable.
    }
  };

  const loadBookings = async () => {
    allBookings = await requestJson('/api/admin/bookings', {}, 'Unable to load bookings');
    renderBookings();
  };

  const renderBookings = () => {
    const filter = document.getElementById('booking-status-filter').value;
    const rows = filter
      ? allBookings.filter(b => normalizeStatus(b.status) === filter)
      : allBookings;
    const tbody = document.getElementById('bookings-tbody');

    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="10" class="px-4 py-8 text-center text-muted-foreground">No bookings found.</td></tr>';
      return;
    }

    tbody.innerHTML = rows.map(b => {
      const status = normalizeStatus(b.status);
      const due = hasMoney(b.paymentDueNowAmount) ? `<div class="mt-1 text-xs text-muted-foreground">Due ${fmt.currency(b.paymentDueNowAmount)}</div>` : '';
      const ops = b.transportMode
        ? `${esc(b.transportMode)}${b.transportClass ? ` / ${esc(b.transportClass)}` : ''}`
        : 'No transport';
      return `
      <tr class="border-t border-border hover:bg-secondary/30 transition-colors align-top">
        <td class="px-4 py-3 font-mono text-xs text-muted-foreground">${esc(b.bookingReference)}</td>
        <td class="px-4 py-3">
          <div class="font-medium">${esc(b.customerName)}</div>
          <div class="text-xs text-muted-foreground">${esc(b.email)}</div>
        </td>
        <td class="px-4 py-3 max-w-[160px] truncate">${esc(b.tourTitle ?? b.tourId)}</td>
        <td class="px-4 py-3 whitespace-nowrap">${fmt.date(b.date)}</td>
        <td class="px-4 py-3">${esc(b.guests)}</td>
        <td class="px-4 py-3 font-medium">${fmt.currency(b.totalPrice)}</td>
        <td class="px-4 py-3">${fmt.payment(b.paymentStatus)}${due}</td>
        <td class="px-4 py-3">${fmt.status(status)}</td>
        <td class="px-4 py-3">
          <div class="text-xs">${ops}</div>
          <div class="mt-1 text-xs text-muted-foreground">${esc(b.transportStatus || 'Not Required')} / docs ${b.documentsVerified ? 'yes' : 'no'}</div>
        </td>
        <td class="px-4 py-3 text-right">
          <div class="flex gap-1 justify-end flex-wrap">
            <button onclick="adminApp.openBookingDetails(${b.id})" class="h-7 rounded-md bg-secondary px-2 text-xs hover:bg-secondary/80 transition-colors">Details</button>
            <button onclick="adminApp.showActivity(${b.id})" class="h-7 rounded-md bg-secondary px-2 text-xs hover:bg-secondary/80 transition-colors">Timeline</button>
            <button onclick="adminApp.openOperations(${b.id})" class="h-7 rounded-md bg-primary/10 px-2 text-xs text-primary hover:bg-primary/20 transition-colors">Ops</button>
            <button onclick="adminApp.openReminder(${b.id})" class="h-7 rounded-md bg-primary/10 px-2 text-xs text-primary hover:bg-primary/20 transition-colors">Reminder</button>
            ${hasMoney(b.paymentOutstandingAmount) ? `<button onclick="adminApp.openPayment(${b.id})" class="h-7 rounded-md bg-green-100 px-2 text-xs text-green-700 hover:bg-green-200 transition-colors">Pay</button>` : ''}
            ${hasMoney(b.paymentRefundableAmount) ? `<button onclick="adminApp.openRefund(${b.id})" class="h-7 rounded-md bg-amber-100 px-2 text-xs text-amber-700 hover:bg-amber-200 transition-colors">Refund</button>` : ''}
            ${status === 'PENDING' ? `<button onclick="adminApp.updateBookingStatus(${b.id},'CONFIRMED')" class="h-7 rounded-md bg-green-600 px-2 text-xs text-white hover:bg-green-700 transition-colors">Confirm</button>` : ''}
            ${(status === 'PENDING' || status === 'CONFIRMED') ? `<button onclick="adminApp.updateBookingStatus(${b.id},'CANCELLED')" class="h-7 rounded-md bg-red-100 px-2 text-xs text-red-700 hover:bg-red-200 transition-colors">Cancel</button>` : ''}
          </div>
        </td>
      </tr>`;
    }).join('');
  };

  const updateBookingStatus = async (id, status) => {
    try {
      const nextStatus = normalizeStatus(status);
      await requestJson(`/api/admin/bookings/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: nextStatus,
          note: nextStatus === 'CONFIRMED' ? 'Confirmed from admin dashboard' : 'Updated from admin dashboard',
        }),
      }, 'Failed to update booking');
      feedback(nextStatus === 'CONFIRMED' ? 'Booking confirmed.' : 'Booking updated.');
      await loadBookings();
      await loadStats();
    } catch (error) {
      feedback(error.message, false);
    }
  };

  const detailRow = (label, value) => `
    <div class="rounded-lg border border-border bg-secondary/30 p-3">
      <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">${esc(label)}</p>
      <p class="mt-1 text-sm">${esc(value || '-')}</p>
    </div>`;

  const openBookingDetails = id => {
    const b = findBooking(id);
    if (!b) return;
    openActionModal(`Booking ${b.bookingReference}`, `
      <div class="space-y-5">
        <div class="grid gap-3 sm:grid-cols-2">
          ${detailRow('Customer', `${b.customerName} / ${b.email}`)}
          ${detailRow('Phone', b.phone)}
          ${detailRow('Tour', b.tourTitle || b.tourId)}
          ${detailRow('Travel Date', fmt.date(b.date))}
          ${detailRow('Payment', `${b.paymentStatus || '-'}; paid ${fmt.currency(b.paymentPaidAmount)}; outstanding ${fmt.currency(b.paymentOutstandingAmount)}`)}
          ${detailRow('Last Receipt', b.paymentLastReceiptNumber || '-')}
          ${detailRow('Transport', `${b.transportMode || 'No transport'}${b.transportClass ? ` / ${b.transportClass}` : ''}`)}
          ${detailRow('Transport Status', b.transportStatus || 'Not Required')}
          ${detailRow('Documents', b.documentsVerified ? 'Verified' : 'Not verified')}
          ${detailRow('Status Reason', b.statusReason || '-')}
        </div>
        <div class="rounded-lg border border-border bg-background p-4">
          <p class="mb-2 text-sm font-semibold">Traveler Preferences</p>
          <p class="text-sm text-muted-foreground">${esc([
            b.mealPreference && `Meal: ${b.mealPreference}`,
            b.dietaryRestrictions && `Dietary: ${b.dietaryRestrictions}`,
            b.occasionType && `Occasion: ${b.occasionType}`,
            b.occasionNotes && `Occasion notes: ${b.occasionNotes}`,
            b.roomPreference && `Room: ${b.roomPreference}`,
            b.tripStyle && `Style: ${b.tripStyle}`,
            b.transferRequired && 'Transfer requested',
            b.assistanceNotes && `Assistance: ${b.assistanceNotes}`,
            b.travelerNotes && `Notes: ${b.travelerNotes}`,
          ].filter(Boolean).join(' | ') || 'No preferences captured yet.')}</p>
        </div>
        <div class="flex flex-wrap justify-end gap-2">
          <button onclick="adminApp.showActivity(${b.id})" class="h-9 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Timeline</button>
          <button onclick="adminApp.openOperations(${b.id})" class="h-9 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Edit Ops</button>
          <button onclick="adminApp.openReminder(${b.id})" class="h-9 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Send Reminder</button>
        </div>
      </div>
    `);
  };

  const showActivity = async id => {
    const b = findBooking(id);
    openActionModal('Booking Timeline', '<p class="text-sm text-muted-foreground">Loading timeline...</p>');
    try {
      const rows = await requestJson(`/api/admin/bookings/${id}/activity`, {}, 'Unable to load booking activity');
      modalContent().innerHTML = rows.length
        ? `<div class="space-y-3">${rows.map(item => `
            <div class="rounded-lg border border-border bg-background p-4">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="text-sm font-semibold">${esc(displayStatus(item.actionType || 'Activity'))}</p>
                <p class="text-xs text-muted-foreground">${fmt.dateTime(item.createdAt)}</p>
              </div>
              <p class="mt-1 text-xs text-muted-foreground">${esc(item.actorName)} (${esc(item.actorRole)})</p>
              <p class="mt-2 text-sm">${esc(item.note || 'No note recorded.')}</p>
            </div>`).join('')}</div>`
        : `<p class="text-sm text-muted-foreground">No activity recorded for ${esc(b?.bookingReference || 'this booking')}.</p>`;
    } catch (error) {
      modalContent().innerHTML = `<p class="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">${esc(error.message)}</p>`;
    }
  };

  const openReminder = id => {
    const b = findBooking(id);
    if (!b) return;
    openActionModal(`Send Reminder - ${b.bookingReference}`, `
      <form id="admin-reminder-form" class="space-y-4">
        <p class="text-sm text-muted-foreground">Send a stored notification and email when SMTP is configured.</p>
        <textarea id="reminder-message" rows="5" maxlength="500" class="w-full rounded-lg border border-border bg-background p-3 text-sm" placeholder="Optional custom message"></textarea>
        <div class="flex justify-end gap-2">
          <button type="button" onclick="adminApp.closeActionModal()" class="h-10 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Cancel</button>
          <button type="submit" class="h-10 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground hover:bg-primary/90">Send Reminder</button>
        </div>
      </form>`);
    document.getElementById('admin-reminder-form').addEventListener('submit', async event => {
      event.preventDefault();
      try {
        await requestJson(`/api/admin/bookings/${id}/reminder`, {
          method: 'POST',
          body: JSON.stringify({ message: document.getElementById('reminder-message').value.trim() }),
        }, 'Unable to send reminder');
        closeActionModal();
        feedback('Reminder queued for traveler.');
      } catch (error) {
        feedback(error.message, false);
      }
    });
  };

  const openOperations = id => {
    const b = findBooking(id);
    if (!b) return;
    openActionModal(`Operations - ${b.bookingReference}`, `
      <form id="admin-ops-form" class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <label class="text-sm font-medium">Transport Mode</label>
            <select id="op-transport-mode" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">${selectOptions(['Flight', 'Train', 'Bus', 'Self Arranged'], b.transportMode, 'No transport')}</select>
          </div>
          <div class="space-y-1">
            <label class="text-sm font-medium">Transport Class</label>
            <input id="op-transport-class" value="${esc(b.transportClass || '')}" maxlength="80" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm" placeholder="Economy, business, sleeper...">
          </div>
          <div class="space-y-1">
            <label class="text-sm font-medium">Transport Status</label>
            <select id="op-transport-status" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">${selectOptions(['Requested', 'Quoted', 'Confirmed', 'Not Required'], b.transportStatus, 'Select status')}</select>
          </div>
          <div class="space-y-1">
            <label class="text-sm font-medium">Priority</label>
            <select id="op-priority" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">${selectOptions(['Normal', 'High', 'VIP'], b.operationsPriority, 'Select priority')}</select>
          </div>
          <label class="flex items-center gap-2 rounded-lg border border-border bg-secondary/30 p-3 text-sm sm:col-span-2">
            <input id="op-documents" type="checkbox" class="rounded border-border" ${b.documentsVerified ? 'checked' : ''}>
            Documents verified
          </label>
          <div class="space-y-1 sm:col-span-2">
            <label class="text-sm font-medium">Operations Notes</label>
            <textarea id="op-notes" rows="4" maxlength="500" class="w-full rounded-lg border border-border bg-background p-3 text-sm" placeholder="Internal logistics notes">${esc(b.operationsNotes || '')}</textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <button type="button" onclick="adminApp.closeActionModal()" class="h-10 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Cancel</button>
          <button type="submit" class="h-10 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground hover:bg-primary/90">Save Operations</button>
        </div>
      </form>`);
    document.getElementById('admin-ops-form').addEventListener('submit', async event => {
      event.preventDefault();
      try {
        await requestJson(`/api/admin/bookings/${id}/operations`, {
          method: 'PATCH',
          body: JSON.stringify({
            transportMode: document.getElementById('op-transport-mode').value,
            transportClass: document.getElementById('op-transport-class').value.trim(),
            transportStatus: document.getElementById('op-transport-status').value,
            documentsVerified: document.getElementById('op-documents').checked,
            operationsPriority: document.getElementById('op-priority').value,
            operationsNotes: document.getElementById('op-notes').value.trim(),
          }),
        }, 'Unable to update operations');
        closeActionModal();
        feedback('Booking operations updated.');
        await loadBookings();
      } catch (error) {
        feedback(error.message, false);
      }
    });
  };

  const openPayment = id => {
    const b = findBooking(id);
    if (!b) return;
    const suggested = hasMoney(b.paymentDueNowAmount) ? b.paymentDueNowAmount : b.paymentOutstandingAmount;
    openActionModal(`Collect Payment - ${b.bookingReference}`, `
      <form id="admin-payment-form" class="space-y-4">
        <div class="rounded-lg bg-secondary/40 p-4 text-sm">
          Outstanding: <strong>${fmt.currency(b.paymentOutstandingAmount)}</strong>
          <span class="mx-2 text-muted-foreground">/</span>
          Due now: <strong>${fmt.currency(b.paymentDueNowAmount)}</strong>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <label class="text-sm font-medium">Method</label>
            <select id="pay-method" required class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">${selectOptions(['Card', 'Credit Card', 'Debit Card', 'UPI', 'Net Banking', 'Bank Transfer', 'PayPal', 'Google Pay', 'Apple Pay', 'Cash', 'Cheque'], b.paymentLastMethod, 'Select method')}</select>
          </div>
          <div class="space-y-1">
            <label class="text-sm font-medium">Amount</label>
            <input id="pay-amount" type="number" min="0.01" step="0.01" required value="${esc(Number(suggested || 0).toFixed(2))}" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">
          </div>
          <div class="space-y-1 sm:col-span-2">
            <label class="text-sm font-medium">Note</label>
            <textarea id="pay-note" rows="3" maxlength="500" class="w-full rounded-lg border border-border bg-background p-3 text-sm" placeholder="Deposit, balance, offline receipt note..."></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <button type="button" onclick="adminApp.closeActionModal()" class="h-10 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Cancel</button>
          <button type="submit" class="h-10 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground hover:bg-primary/90">Record Payment</button>
        </div>
      </form>`);
    document.getElementById('admin-payment-form').addEventListener('submit', async event => {
      event.preventDefault();
      try {
        const data = await requestJson(`/api/admin/bookings/${id}/payments`, {
          method: 'POST',
          body: JSON.stringify({
            method: document.getElementById('pay-method').value,
            amount: Number(document.getElementById('pay-amount').value),
            note: document.getElementById('pay-note').value.trim(),
          }),
        }, 'Unable to record payment');
        closeActionModal();
        feedback(data?.message || 'Payment recorded.');
        await loadBookings();
        await loadStats();
      } catch (error) {
        feedback(error.message, false);
      }
    });
  };

  const openRefund = id => {
    const b = findBooking(id);
    if (!b) return;
    openActionModal(`Refund - ${b.bookingReference}`, `
      <form id="admin-refund-form" class="space-y-4">
        <div class="rounded-lg bg-secondary/40 p-4 text-sm">
          Refundable amount: <strong>${fmt.currency(b.paymentRefundableAmount)}</strong>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Refund Amount</label>
          <input id="refund-amount" type="number" min="0.01" step="0.01" value="${esc(Number(b.paymentRefundableAmount || 0).toFixed(2))}" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Note</label>
          <textarea id="refund-note" rows="3" maxlength="500" class="w-full rounded-lg border border-border bg-background p-3 text-sm" placeholder="Refund reason or internal note"></textarea>
        </div>
        <div class="flex justify-end gap-2">
          <button type="button" onclick="adminApp.closeActionModal()" class="h-10 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Cancel</button>
          <button type="submit" class="h-10 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground hover:bg-primary/90">Process Refund</button>
        </div>
      </form>`);
    document.getElementById('admin-refund-form').addEventListener('submit', async event => {
      event.preventDefault();
      try {
        const rawAmount = document.getElementById('refund-amount').value;
        const data = await requestJson(`/api/admin/bookings/${id}/refund`, {
          method: 'POST',
          body: JSON.stringify({
            amount: rawAmount ? Number(rawAmount) : null,
            note: document.getElementById('refund-note').value.trim(),
          }),
        }, 'Unable to process refund');
        closeActionModal();
        feedback(data?.message || 'Refund processed.');
        await loadBookings();
        await loadStats();
      } catch (error) {
        feedback(error.message, false);
      }
    });
  };

  const loadTours = async () => {
    allTours = await requestJson('/api/admin/tours', {}, 'Unable to load tours');
    const tbody = document.getElementById('tours-tbody');
    if (!allTours.length) {
      tbody.innerHTML = '<tr><td colspan="7" class="px-4 py-8 text-center text-muted-foreground">No tours yet. Add one!</td></tr>';
      return;
    }
    tbody.innerHTML = allTours.map(t => `
      <tr class="border-t border-border hover:bg-secondary/30 transition-colors">
        <td class="px-4 py-3">
          <div class="flex items-center gap-3">
            <img src="${esc(t.image)}" alt="" class="h-10 w-16 rounded-md object-cover bg-secondary">
            <span class="font-medium">${esc(t.title)}</span>
          </div>
        </td>
        <td class="px-4 py-3">${esc(t.destination)}, ${esc(t.country)}</td>
        <td class="px-4 py-3"><span class="rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium">${esc(t.category)}</span></td>
        <td class="px-4 py-3 font-medium">${fmt.currency(t.price)}</td>
        <td class="px-4 py-3">Rating: ${esc(t.rating ?? '-')}</td>
        <td class="px-4 py-3">${esc(t.maxGroupSize ?? '-')} pax</td>
        <td class="px-4 py-3 text-right">
          <div class="flex gap-1 justify-end">
            <button onclick="adminApp.openTourById('${esc(t.id)}')" class="h-7 rounded-md bg-primary/10 px-2 text-xs text-primary hover:bg-primary/20 transition-colors">Edit</button>
            <button onclick="adminApp.deleteTour('${esc(t.id)}','${esc(t.title)}')" class="h-7 rounded-md bg-destructive/10 px-2 text-xs text-destructive hover:bg-destructive/20 transition-colors">Delete</button>
          </div>
        </td>
      </tr>`).join('');
  };

  const openTourModal = (tour = null) => {
    const isEdit = tour && tour.id;
    document.getElementById('tour-modal-title').textContent = isEdit ? 'Edit Tour / Destination' : 'Add Tour / Destination';
    document.getElementById('tour-form-submit').textContent = isEdit ? 'Update Tour / Destination' : 'Save Tour / Destination';
    document.getElementById('tf-id').value = isEdit ? tour.id : '';
    document.getElementById('tf-title').value = isEdit ? tour.title : '';
    document.getElementById('tf-destination').value = isEdit ? tour.destination : '';
    document.getElementById('tf-country').value = isEdit ? tour.country : '';
    document.getElementById('tf-duration').value = isEdit ? tour.duration : '';
    document.getElementById('tf-category').value = isEdit ? tour.category : '';
    document.getElementById('tf-price').value = isEdit ? tour.price : '';
    document.getElementById('tf-original-price').value = isEdit ? (tour.originalPrice || '') : '';
    document.getElementById('tf-difficulty').value = isEdit ? (tour.difficulty || '') : '';
    document.getElementById('tf-max-group').value = isEdit ? (tour.maxGroupSize || 15) : 15;
    document.getElementById('tf-image').value = isEdit ? tour.image : '';
    document.getElementById('tf-description').value = isEdit ? (tour.description || '') : '';
    document.getElementById('tf-highlights').value = isEdit ? (tour.highlights || []).join('\n') : '';
    document.getElementById('tf-included').value = isEdit ? (tour.included || []).join('\n') : '';
    document.getElementById('tf-dates').value = isEdit ? (tour.startDates || []).join('\n') : '';
    document.getElementById('tour-form-error').classList.add('hidden');
    document.getElementById('tour-modal').classList.remove('hidden');
  };

  const openTourById = id => {
    const tour = allTours.find(item => String(item.id) === String(id));
    if (tour) openTourModal(tour);
  };

  const closeTourModal = () => document.getElementById('tour-modal').classList.add('hidden');

  const addTourDestination = () => {
    activateTab('tours');
    openTourModal();
  };

  const submitTour = async event => {
    event.preventDefault();
    const id = document.getElementById('tf-id').value.trim();
    const errEl = document.getElementById('tour-form-error');
    errEl.classList.add('hidden');
    const lines = elementId => (document.getElementById(elementId).value || '').split('\n').map(s => s.trim()).filter(Boolean);
    const body = {
      id: id || document.getElementById('tf-title').value.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
      title: document.getElementById('tf-title').value.trim(),
      destination: document.getElementById('tf-destination').value.trim(),
      country: document.getElementById('tf-country').value.trim(),
      duration: document.getElementById('tf-duration').value.trim(),
      category: document.getElementById('tf-category').value,
      price: Number(document.getElementById('tf-price').value) || 0,
      originalPrice: document.getElementById('tf-original-price').value ? Number(document.getElementById('tf-original-price').value) : null,
      rating: 5.0,
      reviews: 0,
      image: document.getElementById('tf-image').value.trim(),
      description: document.getElementById('tf-description').value.trim(),
      difficulty: document.getElementById('tf-difficulty').value,
      maxGroupSize: Number(document.getElementById('tf-max-group').value) || 15,
      highlights: lines('tf-highlights'),
      included: lines('tf-included'),
      startDates: lines('tf-dates'),
    };
    try {
      const isEdit = Boolean(id);
      await requestJson(isEdit ? `/api/admin/tours/${id}` : '/api/admin/tours', {
        method: isEdit ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      }, 'Failed to save tour.');
      closeTourModal();
      feedback(isEdit ? 'Tour / destination updated.' : 'Tour / destination created.');
      await loadTours();
      await loadStats();
    } catch (error) {
      errEl.textContent = error.message;
      errEl.classList.remove('hidden');
    }
  };

  const deleteTour = (id, title) => {
    document.getElementById('confirm-message').textContent = `Delete "${title}"? This cannot be undone.`;
    confirmCallback = async () => {
      try {
        await requestJson(`/api/admin/tours/${id}`, { method: 'DELETE' }, 'Failed to delete tour');
        feedback('Tour deleted.');
        await loadTours();
        await loadStats();
      } catch (error) {
        feedback(error.message, false);
      }
    };
    document.getElementById('confirm-modal').classList.remove('hidden');
  };

  const closeConfirm = () => {
    document.getElementById('confirm-modal').classList.add('hidden');
    confirmCallback = null;
  };

  const loadInquiries = async () => {
    allInquiries = await requestJson('/api/admin/inquiries', {}, 'Unable to load inquiries');
    const tbody = document.getElementById('inquiries-tbody');
    if (!allInquiries.length) {
      tbody.innerHTML = '<tr><td colspan="7" class="px-4 py-8 text-center text-muted-foreground">No inquiries yet.</td></tr>';
      return;
    }
    tbody.innerHTML = allInquiries.map(i => {
      const status = normalizeStatus(i.status);
      return `
      <tr class="border-t border-border hover:bg-secondary/30 transition-colors">
        <td class="px-4 py-3"><div class="font-medium">${esc(i.customerName)}</div><div class="text-xs text-muted-foreground">${esc(i.phone || '')}</div></td>
        <td class="px-4 py-3 text-muted-foreground text-xs">${esc(i.email)}</td>
        <td class="px-4 py-3">${esc(i.destination || '-')}</td>
        <td class="px-4 py-3">${esc(i.travelers ?? '-')}</td>
        <td class="px-4 py-3">${fmt.status(status)}</td>
        <td class="px-4 py-3 text-muted-foreground text-xs whitespace-nowrap">${fmt.date(i.createdAt)}</td>
        <td class="px-4 py-3 text-right">
          <div class="flex gap-1 justify-end flex-wrap">
            <button onclick="adminApp.openInquiry(${i.id})" class="h-7 rounded-md bg-secondary px-2 text-xs hover:bg-secondary/80">Details</button>
            ${status === 'NEW' ? `<button onclick="adminApp.updateInquiry(${i.id},'IN_PROGRESS')" class="h-7 rounded-md bg-amber-100 px-2 text-xs text-amber-700 hover:bg-amber-200">In Progress</button>` : ''}
            ${status !== 'RESOLVED' ? `<button onclick="adminApp.updateInquiry(${i.id},'RESOLVED')" class="h-7 rounded-md bg-green-100 px-2 text-xs text-green-700 hover:bg-green-200">Resolve</button>` : ''}
          </div>
        </td>
      </tr>`;
    }).join('');
  };

  const openInquiry = id => {
    const inquiry = findInquiry(id);
    if (!inquiry) return;
    openActionModal(`Inquiry - ${inquiry.customerName}`, `
      <form id="admin-inquiry-form" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2">
          ${detailRow('Email', inquiry.email)}
          ${detailRow('Phone', inquiry.phone)}
          ${detailRow('Destination', inquiry.destination)}
          ${detailRow('Travel Window', inquiry.travelWindow)}
        </div>
        <div class="rounded-lg border border-border bg-background p-4">
          <p class="mb-1 text-sm font-semibold">Message</p>
          <p class="text-sm text-muted-foreground">${esc(inquiry.message || 'No message.')}</p>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Status</label>
          <select id="inquiry-status" class="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm">${selectOptions(['NEW', 'IN_PROGRESS', 'RESOLVED'], normalizeStatus(inquiry.status), 'Select status')}</select>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Admin Notes</label>
          <textarea id="inquiry-notes" rows="4" maxlength="1000" class="w-full rounded-lg border border-border bg-background p-3 text-sm">${esc(inquiry.adminNotes || '')}</textarea>
        </div>
        <div class="flex justify-end gap-2">
          <button type="button" onclick="adminApp.closeActionModal()" class="h-10 rounded-lg border border-border px-4 text-sm font-medium hover:bg-secondary">Cancel</button>
          <button type="submit" class="h-10 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground hover:bg-primary/90">Save Inquiry</button>
        </div>
      </form>`);
    document.getElementById('admin-inquiry-form').addEventListener('submit', async event => {
      event.preventDefault();
      await updateInquiry(id, document.getElementById('inquiry-status').value, document.getElementById('inquiry-notes').value.trim(), true);
    });
  };

  const updateInquiry = async (id, status, adminNotes = '', closeAfterSave = false) => {
    try {
      await requestJson(`/api/admin/inquiries/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: normalizeStatus(status), adminNotes }),
      }, 'Failed to update inquiry.');
      if (closeAfterSave) closeActionModal();
      feedback('Inquiry updated.');
      await loadInquiries();
      await loadStats();
    } catch (error) {
      feedback(error.message, false);
    }
  };

  const loadWaitlist = async () => {
    const data = await requestJson('/api/admin/waitlist', {}, 'Unable to load waitlist');
    const tbody = document.getElementById('waitlist-tbody');
    if (!data.length) {
      tbody.innerHTML = '<tr><td colspan="7" class="px-4 py-8 text-center text-muted-foreground">No active waitlist entries.</td></tr>';
      return;
    }
    tbody.innerHTML = data.map(w => {
      const status = normalizeStatus(w.status);
      return `
      <tr class="border-t border-border hover:bg-secondary/30 transition-colors">
        <td class="px-4 py-3 font-mono text-xs text-muted-foreground">${esc(w.waitlistReference)}</td>
        <td class="px-4 py-3"><div class="font-medium">${esc(w.customerName)}</div><div class="text-xs text-muted-foreground">${esc(w.email)}</div></td>
        <td class="px-4 py-3">${esc(w.tourTitle)}</td>
        <td class="px-4 py-3 whitespace-nowrap">${fmt.date(w.travelDate)}</td>
        <td class="px-4 py-3">${esc(w.guests)}</td>
        <td class="px-4 py-3">${fmt.status(status)}</td>
        <td class="px-4 py-3 text-right">
          ${status === 'WAITLISTED' ? `<button onclick="adminApp.notifyWaitlist(${w.id})" class="h-7 rounded-md bg-primary/10 px-2 text-xs text-primary hover:bg-primary/20">Notify</button>` : ''}
        </td>
      </tr>`;
    }).join('');
  };

  const notifyWaitlist = async id => {
    try {
      await requestJson(`/api/admin/waitlist/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: 'NOTIFIED',
          message: 'A spot has opened up for your requested tour. Please book now.',
        }),
      }, 'Failed to notify customer.');
      feedback('Customer notified.');
      await loadWaitlist();
    } catch (error) {
      feedback(error.message, false);
    }
  };

  const loadNewsletter = async () => {
    const data = await requestJson('/api/admin/newsletter/subscribers', {}, 'Unable to load newsletter subscribers');
    document.getElementById('newsletter-count').textContent = data.length;
    const tbody = document.getElementById('newsletter-tbody');
    if (!data.length) {
      tbody.innerHTML = '<tr><td colspan="4" class="px-4 py-8 text-center text-muted-foreground">No subscribers yet.</td></tr>';
      return;
    }
    tbody.innerHTML = data.map((s, i) => `
      <tr class="border-t border-border hover:bg-secondary/30 transition-colors">
        <td class="px-4 py-3 text-muted-foreground text-xs">${i + 1}</td>
        <td class="px-4 py-3 font-medium">${esc(s.email)}</td>
        <td class="px-4 py-3 text-muted-foreground text-xs whitespace-nowrap">${fmt.date(s.subscribedAt)}</td>
        <td class="px-4 py-3">${fmt.status(s.active ? 'ACTIVE' : 'INACTIVE')}</td>
      </tr>`).join('');
  };

  const loadAll = async () => {
    try {
      await loadStats();
      await loadBookings();
      await loadTours();
      await loadInquiries();
      await loadWaitlist();
      await loadNewsletter();
    } catch (error) {
      feedback(error.message, false);
    }
  };

  document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    loadAll();
    document.getElementById('tour-form').addEventListener('submit', submitTour);
    document.getElementById('confirm-ok').addEventListener('click', async () => {
      if (confirmCallback) await confirmCallback();
      closeConfirm();
    });
    document.getElementById('tour-modal').addEventListener('click', event => {
      if (event.target === event.currentTarget) closeTourModal();
    });
    document.getElementById('confirm-modal').addEventListener('click', event => {
      if (event.target === event.currentTarget) closeConfirm();
    });
    document.getElementById('admin-action-modal').addEventListener('click', event => {
      if (event.target === event.currentTarget) closeActionModal();
    });
  });

  window.adminApp = {
    loadAll,
    filterBookings: renderBookings,
    updateBookingStatus,
    openBookingDetails,
    showActivity,
    openReminder,
    openOperations,
    openPayment,
    openRefund,
    addTourDestination,
    openTourById,
    openTourModal,
    closeTourModal,
    deleteTour,
    closeConfirm,
    closeActionModal,
    openInquiry,
    updateInquiry,
    notifyWaitlist,
  };
})();
