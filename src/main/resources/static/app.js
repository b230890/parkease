const state = {
    token: localStorage.getItem("parkease_token"),
    user: JSON.parse(localStorage.getItem("parkease_user") || "null"),
    historyPage: 0,
    historySize: 10,
    historySort: "checkInTime,desc"
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

function showToast(message, isError = false) {
    const toast = $("#toast");
    toast.textContent = message;
    toast.className = `toast show${isError ? " error" : ""}`;
    window.clearTimeout(showToast.timeout);
    showToast.timeout = window.setTimeout(() => toast.classList.remove("show"), 3400);
}

function showView(view) {
    ["landing", "auth", "dashboard"].forEach((name) => $(`#${name}-view`).classList.toggle("hidden", name !== view));
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function showAuth(mode) {
    const register = mode === "register";
    $("#auth-eyebrow").textContent = register ? "A better shift starts here" : "Welcome back";
    $("#auth-title").textContent = register ? "Create your garage account" : "Log in to your garage";
    $("#auth-copy").textContent = register ? "Set up your operations desk in a minute." : "Your operations desk is waiting.";
    $("#login-form").classList.toggle("hidden", register);
    $("#register-form").classList.toggle("hidden", !register);
    $("#auth-switch").innerHTML = register
        ? 'Already using ParkEase? <button class="text-button" data-route="login">Log in</button>'
        : 'New to ParkEase? <button class="text-button" data-route="register">Create an account</button>';
    showView("auth");
}

function route(route) {
    if (route === "dashboard") {
        if (!state.token) { showAuth("login"); return; }
        showView("dashboard");
        loadDashboard();
        return;
    }
    if (route === "login" || route === "register") { showAuth(route); return; }
    showView("landing");
}

async function api(path, options = {}) {
    const headers = { ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }), ...(options.headers || {}) };
    if (state.token) headers.Authorization = `Bearer ${state.token}`;
    const response = await fetch(path, { ...options, headers });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    if (!response.ok) {
        if (response.status === 401 && state.token) { logout(false); }
        throw new Error(data?.error || data?.message || `Request failed (${response.status})`);
    }
    return data;
}

function formDataObject(form) { return Object.fromEntries(new FormData(form).entries()); }
function formatDate(value) { return value ? new Date(value).toLocaleString([], { dateStyle: "medium", timeStyle: "short" }) : "--"; }
function money(value) { return value == null ? "--" : `₹${Number(value).toFixed(2)}`; }
function setResult(id, html) { const element = $(id); element.innerHTML = html; element.classList.remove("hidden"); }
function setMessage(message, error = false) { const element = $("#dashboard-message"); element.textContent = message; element.classList.toggle("hidden", !message); element.style.background = error ? "#fff0d8" : "var(--mint)"; element.style.color = error ? "#85551c" : "var(--green-dark)"; }

async function login(form) {
    const data = await api("/api/auth/login", { method: "POST", body: JSON.stringify(formDataObject(form)) });
    saveAuth(data);
    showToast("Welcome to your operations desk.");
    route("dashboard");
}

async function register(form) {
    const data = await api("/api/auth/register", { method: "POST", body: JSON.stringify(formDataObject(form)) });
    saveAuth(data);
    showToast("Account created. Welcome to ParkEase.");
    route("dashboard");
}

function saveAuth(data) {
    state.token = data.token;
    state.user = data;
    localStorage.setItem("parkease_token", data.token);
    localStorage.setItem("parkease_user", JSON.stringify(data));
}

function logout(notify = true) {
    state.token = null;
    state.user = null;
    localStorage.removeItem("parkease_token");
    localStorage.removeItem("parkease_user");
    showView("landing");
    if (notify) showToast("You have been logged out.");
}

async function loadAvailability() {
    const [summary, ev] = await Promise.all([api("/api/spots/availability"), api("/api/spots/availability?type=EV")]);
    $("#total-spots").textContent = summary.totalSpots;
    $("#available-spots").textContent = summary.availableSpots;
    $("#occupied-spots").textContent = summary.occupiedSpots;
    $("#ev-spots").textContent = ev.available;
    $("#ev-status").textContent = ev.available > 0 ? "Ready for an EV" : "No EV spots open";
}

async function loadActive() {
    const sessions = await api("/api/parking/active");
    $("#active-count").textContent = `${sessions.length} active`;
    const table = $("#active-table");
    if (!sessions.length) { table.innerHTML = '<tr><td colspan="5" class="empty-state">No active sessions right now.</td></tr>'; return; }
    table.innerHTML = sessions.map((session) => `<tr><td><strong>${escapeHtml(session.plateNumber)}</strong></td><td>${session.vehicleType}</td><td>${escapeHtml(session.spotNumber)}</td><td>${formatDate(session.checkInTime)}</td><td><button class="button button-outline checkout-button" data-session-id="${session.sessionId}">Check out</button></td></tr>`).join("");
    $$(".checkout-button").forEach((button) => button.addEventListener("click", () => checkout(button.dataset.sessionId)));
}

async function checkout(sessionId) {
    if (!window.confirm("Check out this vehicle and calculate its final fee?")) return;
    try {
        const result = await api(`/api/parking/check-out/${sessionId}`, { method: "POST" });
        setMessage(`Session ${result.sessionId} closed. Final fee: ${money(result.fee)}.`);
        showToast(`Checkout complete: ${money(result.fee)}`);
        await loadDashboard();
    } catch (error) { setMessage(error.message, true); }
}

async function loadHistory() {
    const params = new URLSearchParams({ page: state.historyPage, size: state.historySize, sort: state.historySort });
    const history = await api(`/api/parking/history?${params}`);
    const table = $("#history-table");
    if (!history.content.length) { table.innerHTML = '<tr><td colspan="6" class="empty-state">No completed sessions yet.</td></tr>'; }
    else table.innerHTML = history.content.map((session) => `<tr><td><strong>${escapeHtml(session.plateNumber)}</strong></td><td>${escapeHtml(session.spotNumber)}</td><td>${formatDate(session.checkInTime)}</td><td>${formatDate(session.checkOutTime)}</td><td class="fee">${money(session.fee)}</td><td><span class="status-pill">${session.status}</span></td></tr>`).join("");
    $("#history-meta").textContent = `Page ${history.page + 1} of ${Math.max(history.totalPages, 1)} · ${history.totalElements} sessions`;
    $("#history-prev").disabled = history.page <= 0;
    $("#history-next").disabled = history.page + 1 >= history.totalPages;
}

async function loadDashboard() {
    $("#user-name").textContent = state.user?.name || "Attendant";
    $("#greeting-name").textContent = (state.user?.name || "attendant").split(" ")[0];
    try { await Promise.all([loadAvailability(), loadActive(), loadHistory()]); setMessage(""); }
    catch (error) { setMessage(error.message, true); }
}

function escapeHtml(value) { return String(value ?? "").replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#039;", '"': "&quot;" }[character])); }

document.addEventListener("click", (event) => {
    const routeTarget = event.target.closest("[data-route]");
    if (routeTarget) { event.preventDefault(); route(routeTarget.dataset.route); }
});

$("#login-form").addEventListener("submit", async (event) => { event.preventDefault(); try { await login(event.target); } catch (error) { showToast(error.message, true); } });
$("#register-form").addEventListener("submit", async (event) => { event.preventDefault(); try { await register(event.target); } catch (error) { showToast(error.message, true); } });
$("#logout-button").addEventListener("click", () => logout());
$("#refresh-button").addEventListener("click", () => loadDashboard().catch((error) => setMessage(error.message, true)));
$("#check-in-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try { const result = await api("/api/parking/check-in", { method: "POST", body: JSON.stringify(formDataObject(event.target)) }); setResult("#check-in-result", `<strong>Spot ${escapeHtml(result.spotNumber)} assigned</strong>${escapeHtml(result.plateNumber)} · ${result.vehicleType} · floor ${result.floor}`); event.target.reset(); await loadDashboard(); } catch (error) { setMessage(error.message, true); }
});
$("#transfer-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try { const result = await api("/api/parking/transfer", { method: "POST", body: JSON.stringify(formDataObject(event.target)) }); setResult("#transfer-result", `<strong>Plate transferred</strong>${escapeHtml(result.oldPlateNumber)} → ${escapeHtml(result.newPlateNumber)} · spot ${escapeHtml(result.spotNumber)}`); event.target.reset(); await loadDashboard(); } catch (error) { setMessage(error.message, true); }
});
$("#search-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try { const data = formDataObject(event.target); const sessions = await api(`/api/parking/search?plateNumber=${encodeURIComponent(data.plateNumber)}`); $("#search-result").innerHTML = sessions.length ? sessions.map((session) => `<div class="result-row"><span><strong>${escapeHtml(session.plateNumber)}</strong> · ${session.status}</span><span>${escapeHtml(session.spotNumber)} · ${money(session.fee)}</span></div>`).join("") : '<p class="muted">No sessions found for that plate.</p>'; } catch (error) { setMessage(error.message, true); }
});
$("#clock-button").addEventListener("click", async () => { try { const result = await api("/clock", { method: "POST" }); setResult("#clock-result", `<strong>${result.closedSessions} session${result.closedSessions === 1 ? "" : "s"} closed</strong>${result.spotsFreed} spot${result.spotsFreed === 1 ? "" : "s"} freed.`); await loadDashboard(); } catch (error) { setMessage(error.message, true); } });
$("#history-sort").addEventListener("change", (event) => { state.historySort = event.target.value; state.historyPage = 0; loadHistory().catch((error) => setMessage(error.message, true)); });
$("#history-prev").addEventListener("click", () => { if (state.historyPage > 0) { state.historyPage -= 1; loadHistory().catch((error) => setMessage(error.message, true)); } });
$("#history-next").addEventListener("click", () => { state.historyPage += 1; loadHistory().catch((error) => { state.historyPage -= 1; setMessage(error.message, true); }); });

route(state.token ? "dashboard" : "landing");