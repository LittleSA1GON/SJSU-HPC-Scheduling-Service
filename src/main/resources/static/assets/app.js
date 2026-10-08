'use strict';

const main = document.querySelector('#main');
const dialog = document.querySelector('#action-dialog');
const state = {
    user: null, csrf: null, catalog: null, selected: null, confirmation: null,
    filters: { serviceId: '2', date: '', providerId: '', durationMinutes: '', availableOnly: true, size: 5 },
    pages: { slots: 0, reservations: 0, provider: 0, 'provider-bookings': 0 },
    view: 'upcoming', size: 5, slotPage: null, appointmentPage: null, providerPage: null,
    pendingRoute: null, navigationId: 0
};
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
const icon = (name, color = 'blue') => `<img class="icon ${color}" src="/assets/icons/${name}.svg" alt="" aria-hidden="true">`;
const route = () => location.hash.slice(1) || 'home';
const isProvider = () => state.user?.role === 'PROVIDER_ADMIN';
const dateLabel = value => new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
const timeLabel = value => new Date(value).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
const durationLabel = minutes => minutes % 60 === 0 ? `${minutes / 60} ${minutes === 60 ? 'hour' : 'hours'}` : `${minutes} minutes`;
const options = (items, selected, anyLabel) => `${anyLabel ? `<option value="">${anyLabel}</option>` : ''}${items.map(item => `<option value="${item.id}" ${String(item.id) === String(selected) ? 'selected' : ''}>${escapeHtml(item.name)}</option>`).join('')}`;

async function api(path, { method = 'GET', body, form = false, quiet = false, csrfRetried = false } = {}) {
    const headers = { Accept: 'application/json' };
    if (method !== 'GET') {
        if (!state.csrf) await refreshCsrf();
        headers[state.csrf.headerName] = state.csrf.token;
        if (body !== undefined) headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json';
    }
    const response = await fetch(path, {
        method, headers, credentials: 'same-origin',
        body: body === undefined ? undefined : form ? new URLSearchParams(body) : JSON.stringify(body)
    });
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
        // A CSRF rejection occurs before the controller can mutate data. Refresh once.
        if (response.status === 403 && data?.code === 'INVALID_CSRF' && !csrfRetried) {
            await refreshCsrf();
            return api(path, { method, body, form, quiet, csrfRetried: true });
        }
        const error = new Error(data?.message || 'Unable to connect to the scheduler. Please try again.');
        error.status = response.status;
        if (response.status === 401 && !quiet && path !== '/api/auth/login') {
            state.user = null; state.pendingRoute = route(); navigate('login');
        }
        throw error;
    }
    return data;
}
async function refreshCsrf() { state.csrf = await api('/api/auth/csrf'); }
function navigate(target) {
    if (route() === target) renderRoute();
    else location.hash = target;
}
function banner(message, success = false) {
    document.querySelector('#message')?.remove();
    const element = document.createElement('div');
    element.id = 'message'; element.className = `banner${success ? ' success' : ''}`;
    element.setAttribute('role', success ? 'status' : 'alert'); element.textContent = message;
    main.prepend(element);
}
function header() {
    const current = route();
    const links = [['home', 'Home'], ['slots', 'Available Slots'], ...(isProvider()
        ? [['provider', 'Manage Availability'], ['provider-bookings', 'Provider Reservations']]
        : [['reservations', 'My Reservations']])];
    document.querySelector('#navigation').innerHTML = links.map(([target, name]) => `<a href="#${target}" ${current === target ? 'aria-current="page"' : ''}>${name}</a>`).join('');
    document.querySelector('#account').innerHTML = state.user
        ? `<span class="account-name">${icon('person-fill', '')}${escapeHtml(state.user.displayName)}</span><button class="btn small" data-action="logout">Sign out</button>`
        : `<a class="btn small" href="#login">Sign in</a>`;
}
function heading(title, description = '', back = '') {
    return `${back ? `<a class="back-link" href="#${back}">${icon('arrow-left')}Back to ${back === 'home' ? 'Home' : 'Available Slots'}</a>` : ''}<div class="page-heading"><h1>${title}</h1>${description ? `<p>${description}</p>` : ''}</div>`;
}
function statusBadge(status) {
    const label = status === 'OPEN' ? 'Available' : status.charAt(0) + status.slice(1).toLowerCase();
    return `<span class="status ${status.toLowerCase()}"><span class="status-dot" aria-hidden="true"></span>${label}</span>`;
}
function slotDetails(slot, appointment = false) {
    return `<dl class="details-list">
        ${appointment ? `<dt>Reservation ID:</dt><dd>#${slot.id}</dd>` : ''}
        <dt>Service:</dt><dd>${escapeHtml(slot.serviceName)}</dd>
        <dt>Date:</dt><dd>${dateLabel(slot.startTime)}</dd>
        <dt>Time:</dt><dd>${timeLabel(slot.startTime)} – ${timeLabel(slot.endTime)}</dd>
        <dt>Duration:</dt><dd>${durationLabel(slot.durationMinutes)}</dd>
        <dt>Provider:</dt><dd>${escapeHtml(slot.providerName)}</dd>
        <dt>Nodes / GPU:</dt><dd>${escapeHtml(slot.gpuType || (slot.serviceName === 'Storage Access' ? 'Shared storage' : 'CPU nodes'))}</dd>
        <dt>Status:</dt><dd>${statusBadge(slot.status)}</dd>
        ${appointment ? `<dt>Project:</dt><dd>${escapeHtml(slot.projectName)}</dd><dt>Contact:</dt><dd>${escapeHtml(slot.contactEmail)}</dd>${slot.resourceRequirements ? `<dt>Requirements:</dt><dd>${escapeHtml(slot.resourceRequirements)}</dd>` : ''}` : ''}
    </dl>`;
}
function pagination(page, list, noun = 'slots') {
    const start = page.total ? page.page * page.size + 1 : 0;
    const end = Math.min((page.page + 1) * page.size, page.total);
    const nearbyPages = Array.from({ length: Math.min(5, page.totalPages) }, (_, offset) => Math.max(0, Math.min(page.page - 2, page.totalPages - 5)) + offset);
    return `<div class="pagination"><span>Showing ${start}–${end} of ${page.total} ${noun}</span>
        <div class="page-buttons" aria-label="Pagination">
            <button class="btn" data-list="${list}" data-page="${page.page - 1}" aria-label="Previous page" ${page.page === 0 ? 'disabled' : ''}>${icon('chevron-left', '')}</button>
            ${nearbyPages.map(number => `<button class="btn ${number === page.page ? 'active' : ''}" data-list="${list}" data-page="${number}" aria-label="Page ${number + 1}" ${number === page.page ? 'aria-current="page"' : ''}>${number + 1}</button>`).join('')}
            <button class="btn" data-list="${list}" data-page="${page.page + 1}" aria-label="Next page" ${page.page + 1 >= page.totalPages ? 'disabled' : ''}>${icon('chevron-right', '')}</button>
        </div><label class="page-size">Rows per page: <select data-size-list="${list}">${[5, 10, 20].map(size => `<option ${size === page.size ? 'selected' : ''}>${size}</option>`).join('')}</select></label></div>`;
}
function renderHome() {
    const filters = state.filters;
    main.innerHTML = `<section class="hero"><div><h1>Reserve HPC Resources</h1><p>Find and book compute resources for your research and projects.</p></div><img src="/assets/servers.png" alt="Compute servers"></section>
        <form id="search-form" class="search-panel">
            <div class="primary-search"><fieldset style="border:0;padding:0;margin:0;min-width:0"><legend><strong>1. Select Compute Service</strong></legend><div class="service-choices">
                ${state.catalog.services.map(service => `<label class="service-choice"><input type="radio" name="serviceId" value="${service.id}" ${String(service.id) === filters.serviceId ? 'checked' : ''} required><span class="service-card">${icon(service.id === 1 ? 'cpu' : service.id === 2 ? 'gpu-card' : 'database')}<strong>${escapeHtml(service.name)}</strong><span>${escapeHtml(service.description)}</span></span></label>`).join('')}
            </div></fieldset><label class="field"><span>2. Select Date</span><input name="date" type="date" min="${state.catalog.today}" value="${filters.date}" aria-describedby="date-hint"><small id="date-hint" class="hint">Leave empty to search all dates.</small></label></div>
            <div class="filters"><h3>Optional Filters</h3><div class="filter-grid">
                <label class="field"><span>Duration</span><select name="durationMinutes"><option value="">Any</option>${[60, 120, 240].map(minutes => `<option value="${minutes}" ${String(minutes) === filters.durationMinutes ? 'selected' : ''}>${durationLabel(minutes)}</option>`).join('')}</select></label>
                <label class="field"><span>Provider</span><select name="providerId">${options(state.catalog.providers, filters.providerId, 'Any')}</select></label>
                <div class="toggles"><label class="toggle"><input type="checkbox" name="availableOnly" ${filters.availableOnly ? 'checked' : ''}>Show only available slots</label><label class="toggle"><input id="advanced-toggle" type="checkbox" aria-controls="advanced-options" aria-expanded="false">Advanced options</label></div>
            </div><div id="advanced-options" class="advanced" hidden><label class="field"><span>Results per page</span><select name="size">${[5, 10, 20].map(size => `<option ${size === filters.size ? 'selected' : ''}>${size}</option>`).join('')}</select></label></div></div>
            <div class="search-actions"><button class="btn primary" type="submit">${icon('search', 'white')}Search Available Slots</button></div>
        </form><p class="hint" style="margin-top:15px">Reservations are for simulated HPC resources. Sign in to book a slot.</p>`;
}
async function renderSlots(navigationId) {
    const params = new URLSearchParams({ page: state.pages.slots, size: state.filters.size, availableOnly: state.filters.availableOnly });
    for (const key of ['serviceId', 'providerId', 'date', 'durationMinutes']) if (state.filters[key]) params.set(key, state.filters[key]);
    const page = await api(`/api/slots?${params}`);
    if (navigationId !== state.navigationId) return;
    state.slotPage = page;
    const service = state.catalog.services.find(item => String(item.id) === state.filters.serviceId);
    const provider = state.catalog.providers.find(item => String(item.id) === state.filters.providerId);
    main.innerHTML = `${heading('Available HPC Slots', '', 'home')}
        <div class="search-summary">${icon(service?.id === 2 ? 'gpu-card' : service?.id === 3 ? 'database' : 'cpu')}<strong>${escapeHtml(service?.name || 'All services')}</strong><span>Date: ${state.filters.date ? dateLabel(state.filters.date) : 'Any'}</span><span>Duration: ${state.filters.durationMinutes ? durationLabel(Number(state.filters.durationMinutes)) : 'Any'}</span><span>Provider: ${escapeHtml(provider?.name || 'Any')}</span><a class="btn small" href="#home">Edit Search</a></div>
        ${page.items.length ? `<div class="table-wrap"><table><thead><tr><th scope="col" aria-sort="ascending">Start Time</th><th scope="col">Duration</th><th scope="col">Provider</th><th scope="col">Nodes / GPU</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead><tbody>${page.items.map(slot => `<tr><td>${timeLabel(slot.startTime)} – ${timeLabel(slot.endTime)}<span class="table-date">${dateLabel(slot.startTime)}</span></td><td>${durationLabel(slot.durationMinutes)}</td><td>${escapeHtml(slot.providerName)}</td><td>${escapeHtml(slot.gpuType || (slot.serviceId === 3 ? 'Shared storage' : 'CPU nodes'))}</td><td>${statusBadge(slot.status)}</td><td class="table-action"><button class="btn ${slot.status === 'OPEN' ? 'primary' : ''}" data-book="${slot.slotId}" ${slot.status !== 'OPEN' || isProvider() ? 'disabled' : ''}>${slot.status === 'OPEN' ? 'Book' : 'Booked'}</button></td></tr>`).join('')}</tbody></table></div>` : `<div class="empty"><h2>No matching slots</h2><p>Try another date, provider, or duration.</p><a class="btn" href="#home">Edit Search</a></div>`}
        ${pagination(page, 'slots')}${isProvider() ? '<p class="hint">Customer accounts can book reservations. Your provider account manages availability.</p>' : ''}`;
}
function renderLogin() {
    main.innerHTML = `<section class="login-card"><h1>Sign in to SJSU HPC</h1><p>Reserve resources or manage your provider availability.</p>
        <form id="login-form"><label class="field"><span>Username</span><input name="username" autocomplete="username" maxlength="100" required></label><label class="field"><span>Password</span><input name="password" type="password" autocomplete="current-password" maxlength="100" required></label><button class="btn primary" type="submit">Sign in</button></form>
        <div class="demo-note"><strong>Demo accounts</strong><p>Customer: <code>student-researcher</code> or <code>second-researcher</code></p><p>Provider: <code>hpc-admin</code> or <code>lab-admin</code></p><p>Demo password: <code>HpcDemo172!</code></p></div></section>`;
}
function renderBook() {
    const slot = state.selected;
    if (!slot) { navigate('slots'); return; }
    main.innerHTML = `${heading('Book HPC Reservation', 'Review the selected slot and provide additional details.', 'slots')}
        <div class="booking-layout"><aside class="slot-details"><h2>Selected Slot</h2>${slotDetails(slot)}</aside>
        <form id="booking-form" class="booking-form"><label class="field"><span>Project Name / Purpose *</span><input name="projectName" placeholder="e.g., research project, course work, model training" maxlength="120" required aria-describedby="project-hint"></label><p id="project-hint" class="hint">Briefly describe how you will use the compute resources.</p>
        <label class="field"><span>Resource Requirements (Optional)</span><textarea name="resourceRequirements" id="requirements" maxlength="200" placeholder="Any specific requirements or notes…" aria-describedby="requirements-count"></textarea></label><div id="requirements-count" class="counter">0/200</div>
        <label class="field"><span>Contact Email *</span><input name="contactEmail" type="email" maxlength="254" value="${escapeHtml(state.user.email)}" required aria-describedby="email-hint"></label><p id="email-hint" class="hint">Contact details will be available to the resource provider.</p>
        <div class="form-actions"><a class="btn" href="#slots">Cancel</a><button class="btn primary" type="submit">Confirm Reservation</button></div></form></div>`;
}
async function renderConfirmation(navigationId, id) {
    const appointment = state.confirmation?.id === Number(id) ? state.confirmation : await api(`/api/appointments/${id}`);
    if (navigationId !== state.navigationId) return;
    const booked = appointment.status === 'BOOKED';
    main.innerHTML = `<div class="confirmation-layout"><section class="confirmation"><div class="success-icon">${icon('check-lg', '')}</div><h1 class="success-title">${booked ? 'Reservation Confirmed!' : 'Reservation Details'}</h1><p>${booked ? 'Your HPC reservation has been successfully booked.' : 'Review the status and details of your reservation.'}</p><div class="slot-details">${slotDetails(appointment, true)}</div></section>
        <aside class="next-panel"><h2>What's Next?</h2><div class="next-item">${icon('calendar-event')}<div><a href="#reservations">View My Reservations</a><p>See all your upcoming and past reservations.</p></div></div><div class="next-item">${icon('plus-circle')}<div><a href="#slots">Book Another Slot</a><p>Return to available slots to make another reservation.</p></div></div><div class="next-item">${icon('question-circle')}<div><button class="link-button" data-action="help">Need Help?</button><p>Learn how to manage your HPC reservations.</p></div></div></aside></div>`;
}
function viewTabs() {
    return `<div class="tabs" aria-label="Reservation views">${[['upcoming', 'Upcoming'], ['history', 'History'], ['all', 'All Reservations']].map(([value, label]) => `<button class="btn ${state.view === value ? 'active' : ''}" data-view="${value}" ${state.view === value ? 'aria-pressed="true"' : 'aria-pressed="false"'}>${label}</button>`).join('')}</div>`;
}
async function renderReservations(navigationId, provider = false) {
    const list = provider ? 'provider-bookings' : 'reservations';
    const page = await api(`${provider ? '/api/provider/appointments' : '/api/appointments'}?view=${state.view}&page=${state.pages[list]}&size=${state.size}`);
    if (navigationId !== state.navigationId) return;
    state.appointmentPage = page;
    main.innerHTML = `${heading(provider ? 'Provider Reservations' : 'My Reservations', provider ? 'View reservations booked with your resource provider.' : 'Manage your upcoming reservations and review your history.')}${viewTabs()}
        ${page.items.length ? `<div class="table-wrap"><table><thead><tr><th>Reservation</th><th>Service</th><th>Date & Time</th><th>${provider ? 'Customer / Contact' : 'Provider'}</th><th>Status</th><th>Action</th></tr></thead><tbody>${page.items.map(appointment => `<tr><td>#${appointment.id}<span class="table-date">${escapeHtml(appointment.projectName)}</span></td><td>${escapeHtml(appointment.serviceName)}</td><td>${dateLabel(appointment.startTime)}<span class="table-date">${timeLabel(appointment.startTime)} – ${timeLabel(appointment.endTime)}</span></td><td>${escapeHtml(provider ? appointment.customerName : appointment.providerName)}${provider ? `<span class="table-date">${escapeHtml(appointment.contactEmail)}</span>` : ''}</td><td>${statusBadge(appointment.status)}</td><td><div class="reservation-actions"><button class="btn small" data-details="${appointment.id}">Details</button>${!provider && appointment.status === 'BOOKED' && new Date(appointment.startTime) > new Date() ? `<button class="btn danger small" data-cancel="${appointment.id}">Cancel</button>` : ''}</div></td></tr>`).join('')}</tbody></table></div>` : `<div class="empty"><h2>No ${state.view === 'all' ? '' : state.view + ' '}reservations</h2><p>${provider ? 'Reservations will appear here when customers book your slots.' : 'Find a compute slot to get started.'}</p>${!provider ? '<a class="btn primary" href="#slots">Browse Available Slots</a>' : ''}</div>`}${pagination(page, list, 'reservations')}`;
}
async function renderProvider(navigationId) {
    const page = await api(`/api/provider/slots?page=${state.pages.provider}&size=${state.size}`);
    if (navigationId !== state.navigationId) return;
    state.providerPage = page;
    main.innerHTML = `${heading('Manage Availability', 'Create service availability and remove unbooked slots. All times are Pacific Time.')}
        <form id="provider-form" class="provider-form"><label class="field"><span>Compute Service</span><select name="serviceId" required>${options(state.catalog.services, '1')}</select></label><label class="field"><span>Start Time</span><input type="datetime-local" name="startTime" min="${state.catalog.today}T00:00" required></label><label class="field"><span>End Time</span><input type="datetime-local" name="endTime" min="${state.catalog.today}T00:00" required></label><button class="btn primary" type="submit">Add Slot</button></form>
        ${page.items.length ? `<div class="table-wrap"><table><thead><tr><th>Service</th><th>Date & Time</th><th>Duration</th><th>Status</th><th>Action</th></tr></thead><tbody>${page.items.map(slot => `<tr><td>${escapeHtml(slot.serviceName)}</td><td>${dateLabel(slot.startTime)}<span class="table-date">${timeLabel(slot.startTime)} – ${timeLabel(slot.endTime)}</span></td><td>${durationLabel(slot.durationMinutes)}</td><td>${statusBadge(slot.status)}</td><td><button class="btn danger small" data-remove="${slot.slotId}" ${slot.status !== 'OPEN' ? 'disabled' : ''}>${slot.status === 'CLOSED' ? 'Removed' : 'Remove'}</button></td></tr>`).join('')}</tbody></table></div>` : '<div class="empty"><h2>No availability slots yet</h2><p>Create your first slot using the form above.</p></div>'}${pagination(page, 'provider')}`;
}
async function renderRoute() {
    if (!state.catalog) return;
    const current = route();
    const navigationId = ++state.navigationId;
    header();
    if (['book', 'reservations', 'provider', 'provider-bookings'].includes(current) || current.startsWith('confirmation/')) {
        if (!state.user) { state.pendingRoute = current; navigate('login'); return; }
        const requiresProvider = current.startsWith('provider');
        if (requiresProvider !== isProvider()) {
            main.innerHTML = heading('This account has a different role', 'Sign in with a customer account to book, or a provider account to manage availability.');
            return;
        }
    }
    main.innerHTML = '<div class="loading" role="status">Loading…</div>';
    try {
        if (current === 'home') renderHome();
        else if (current === 'slots') await renderSlots(navigationId);
        else if (current === 'login') renderLogin();
        else if (current === 'book') renderBook();
        else if (/^confirmation\/\d+$/.test(current)) await renderConfirmation(navigationId, current.split('/')[1]);
        else if (current === 'reservations') await renderReservations(navigationId);
        else if (current === 'provider-bookings') await renderReservations(navigationId, true);
        else if (current === 'provider') await renderProvider(navigationId);
        else navigate('home');
    } catch (error) {
        if (navigationId !== state.navigationId || (error.status === 401 && route() === 'login')) return;
        main.innerHTML = heading('Unable to load this page', 'Please try again or return to Home.'); banner(error.message);
    }
    if (navigationId === state.navigationId) { main.focus({ preventScroll: true }); window.scrollTo(0, 0); }
}

document.addEventListener('submit', async event => {
    const form = event.target;
    if (!['search-form', 'login-form', 'booking-form', 'provider-form'].includes(form.id)) return;
    event.preventDefault();
    if (!form.reportValidity()) return;
    const fields = Object.fromEntries(new FormData(form));
    const submit = form.querySelector('button[type="submit"]');
    submit.disabled = true;
    try {
        if (form.id === 'search-form') {
            state.filters = { ...fields, size: Number(fields.size), availableOnly: form.elements.availableOnly.checked };
            state.pages.slots = 0; navigate('slots');
        } else if (form.id === 'login-form') {
            await api('/api/auth/login', { method: 'POST', body: fields, form: true });
            await refreshCsrf(); state.user = await api('/api/auth/me');
            const target = isProvider() ? 'provider' : state.pendingRoute || 'home';
            state.pendingRoute = null; form.reset(); navigate(target);
        } else if (form.id === 'booking-form') {
            const slot = state.selected;
            state.confirmation = await api('/api/appointments', { method: 'POST', body: {
                ...fields, projectName: fields.projectName.trim(), contactEmail: fields.contactEmail.trim(),
                slotId: slot.slotId, serviceId: slot.serviceId, version: slot.version
            } });
            state.selected = null; navigate(`confirmation/${state.confirmation.id}`);
        } else {
            if (fields.endTime <= fields.startTime) throw new Error('End time must be after start time.');
            await api('/api/provider/slots', { method: 'POST', body: { ...fields, serviceId: Number(fields.serviceId) } });
            state.pages.provider = 0; await renderRoute(); banner('Availability slot created.', true);
        }
    } catch (error) { banner(error.message); }
    finally { if (submit.isConnected) submit.disabled = false; }
});

document.addEventListener('change', event => {
    const element = event.target;
    if (element.id === 'advanced-toggle') {
        document.querySelector('#advanced-options').hidden = !element.checked;
        element.setAttribute('aria-expanded', String(element.checked));
    } else if (element.dataset.sizeList) {
        const list = element.dataset.sizeList; state.pages[list] = 0;
        if (list === 'slots') state.filters.size = Number(element.value); else state.size = Number(element.value);
        renderRoute();
    }
});
document.addEventListener('input', event => {
    if (event.target.id === 'requirements') document.querySelector('#requirements-count').textContent = `${event.target.value.length}/200`;
});
function openDialog(title, content, action = '') {
    dialog.innerHTML = `<h2 id="dialog-title">${title}</h2>${content}<div class="form-actions"><button class="btn" data-action="close-dialog">${action ? 'Keep Reservation' : 'Close'}</button>${action}</div>`;
    dialog.showModal();
}
document.addEventListener('click', async event => {
    if (event.target.closest('.skip-link')) {
        event.preventDefault();
        main.focus();
        return;
    }
    const element = event.target.closest('button');
    if (!element || element.disabled) return;
    try {
        if (element.dataset.book) {
            state.selected = state.slotPage.items.find(slot => slot.slotId === Number(element.dataset.book)); navigate('book');
        } else if (element.dataset.page !== undefined) {
            state.pages[element.dataset.list] = Number(element.dataset.page); await renderRoute();
        } else if (element.dataset.view) {
            state.view = element.dataset.view; state.pages.reservations = 0; state.pages['provider-bookings'] = 0; await renderRoute();
        } else if (element.dataset.cancel) {
            const appointment = state.appointmentPage.items.find(item => item.id === Number(element.dataset.cancel));
            openDialog('Cancel this reservation?', `<p>Reservation #${appointment.id} for ${escapeHtml(appointment.serviceName)} on ${dateLabel(appointment.startTime)} will be cancelled and the slot released.</p>`, `<button class="btn danger" data-confirm-cancel="${appointment.id}">Cancel Reservation</button>`);
        } else if (element.dataset.confirmCancel) {
            element.disabled = true;
            await api(`/api/appointments/${element.dataset.confirmCancel}/cancel`, { method: 'POST' });
            dialog.close(); state.confirmation = null; await renderRoute(); banner('Reservation cancelled. The slot is available to book again.', true);
        } else if (element.dataset.details) {
            const appointment = state.appointmentPage.items.find(item => item.id === Number(element.dataset.details));
            openDialog(`Reservation #${appointment.id}`, `<div style="margin-top:18px">${slotDetails(appointment, true)}</div>`);
        } else if (element.dataset.remove) {
            const slot = state.providerPage.items.find(item => item.slotId === Number(element.dataset.remove));
            dialog.innerHTML = `<h2 id="dialog-title">Remove this availability slot?</h2><p>${escapeHtml(slot.serviceName)}, ${dateLabel(slot.startTime)}, ${timeLabel(slot.startTime)} – ${timeLabel(slot.endTime)} will be removed from search.</p><div class="form-actions"><button class="btn" data-action="close-dialog">Keep Slot</button><button class="btn danger" data-confirm-remove="${slot.slotId}" data-version="${slot.version}">Remove Slot</button></div>`;
            dialog.showModal();
        } else if (element.dataset.confirmRemove) {
            element.disabled = true;
            await api(`/api/provider/slots/${element.dataset.confirmRemove}?version=${element.dataset.version}`, { method: 'DELETE' });
            dialog.close(); await renderRoute(); banner('Availability slot removed.', true);
        } else if (element.dataset.action === 'close-dialog') dialog.close();
        else if (element.dataset.action === 'help') openDialog('Managing your reservations', '<p>Search by service and date, choose an available slot, then enter your project and contact details. You can cancel your own reservation before it starts from My Reservations.</p><p>For resource questions, contact the provider shown on your reservation.</p>');
        else if (element.dataset.action === 'logout') {
            await api('/api/auth/logout', { method: 'POST' });
            state.user = null; state.selected = null; state.confirmation = null; state.pendingRoute = null;
            await refreshCsrf(); navigate('home');
        }
    } catch (error) {
        if (dialog.open) { dialog.close(); }
        if (element.isConnected) element.disabled = false;
        banner(error.message);
    }
});
window.addEventListener('hashchange', renderRoute);
async function start() {
    try {
        state.catalog = await api('/api/catalog'); await refreshCsrf();
        try { state.user = await api('/api/auth/me', { quiet: true }); } catch (error) { if (error.status !== 401) throw error; }
        const tomorrow = new Date(`${state.catalog.today}T12:00:00`); tomorrow.setDate(tomorrow.getDate() + 1);
        state.filters.date = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
        await renderRoute();
    } catch (error) { main.innerHTML = heading('Scheduler unavailable', 'Please refresh once the server is available.'); banner(error.message); }
}
start();
