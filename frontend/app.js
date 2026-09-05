"use strict";

const state = {
  apiUrl: "http://localhost:8080",
  token: localStorage.getItem("civicconnect_token") || "",
  user: null,
  razorpayOrder: null,
};
const $ = (id) => document.getElementById(id);
const value = (id) => $(id).value.trim();
const number = (id) => {
  const v = value(id);
  return v === "" ? null : Number(v);
};

const endpointGroups = [
  [
    "Authentication & account",
    [
      [
        "GET",
        "/oauth2/authorization/google",
        "Start Google login (browser redirect)",
      ],
      ["GET", "/api/users/me", "Current signed-in user"],
    ],
  ],
  [
    "Complaints",
    [
      ["POST", "/api/complaints", "Create complaint"],
      ["GET", "/api/complaints?status=&page=&size=", "Browse complaints"],
      ["GET", "/api/complaints/mine?page=&size=", "My complaints"],
      ["POST", "/api/complaints/{id}/vote", "Vote"],
      ["POST", "/api/complaints/{id}/verify", "Verify resolved complaint"],
    ],
  ],
  [
    "Workers",
    [
      ["POST", "/api/workers/register", "Register worker"],
      ["GET", "/api/workers/me", "My worker profile"],
      ["GET", "/api/workers?skill=&page=&size=", "Browse verified workers"],
      ["GET", "/api/workers/{id}", "Worker profile"],
      ["GET", "/api/workers/{id}/portfolio", "Worker portfolio"],
      ["GET", "/api/workers/{id}/reviews", "Worker reviews"],
    ],
  ],
  [
    "Administration",
    [
      [
        "PATCH",
        "/api/admin/complaints/{id}/priority?priority=",
        "Change complaint priority",
      ],
      [
        "POST",
        "/api/admin/workers/{id}/verification?approved=&notes=",
        "Verify worker",
      ],
      [
        "POST",
        "/api/admin/workers/{id}/availability?available=",
        "Set availability",
      ],
    ],
  ],
  [
    "Assignments & reviews",
    [
      ["POST", "/api/assignments?complaintId=&workerId=", "Admin assignment"],
      [
        "POST",
        "/api/assignments/request?complaintId=&workerId=",
        "Citizen worker request",
      ],
      ["POST", "/api/assignments/{id}/accept", "Worker accepts"],
      ["POST", "/api/assignments/{id}/complete", "Worker completes"],
      [
        "POST",
        "/api/assignments/{id}/citizen-verification?approved=",
        "Citizen verifies",
      ],
      ["POST", "/api/assignments/{id}/reviews", "Create worker review"],
    ],
  ],
  [
    "Bookings",
    [
      ["POST", "/api/bookings", "Create booking"],
      ["GET", "/api/bookings/mine", "My bookings"],
      ["POST", "/api/bookings/{id}/accept", "Accept booking"],
      ["POST", "/api/bookings/{id}/start", "Start booking"],
      ["POST", "/api/bookings/{id}/complete", "Complete booking"],
      ["POST", "/api/bookings/{id}/confirm-completion", "Confirm completion"],
    ],
  ],
  [
    "Payments & notifications",
    [
      ["POST", "/api/payments/create-order", "Create Razorpay order"],
      ["POST", "/api/payments/verify", "Verify Razorpay payment"],
      ["GET", "/api/payments/history", "Payment history"],
      ["GET", "/api/notifications", "List notifications"],
      ["POST", "/api/notifications/{id}/read", "Mark notification read"],
    ],
  ],
];

function showToast(message, error = false) {
  const toast = $("toast");
  toast.textContent = message;
  toast.className = `toast show${error ? " error" : ""}`;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => (toast.className = "toast"), 3600);
}

function showResult(title, data) {
  const readable = title.replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase();
  showToast(
    `${readable.charAt(0).toUpperCase()}${readable.slice(1)} complete.`,
  );
}

async function api(path, { method = "GET", body } = {}) {
  const headers = { Accept: "application/json" };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${state.apiUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : { success: response.ok };
  } catch {
    data = text;
  }
  if (!response.ok) {
    if (response.status === 401) clearSession();
    throw new Error(
      data?.message || data?.error || `Request failed (${response.status})`,
    );
  }
  return data;
}

function requireNumber(inputId, name) {
  const n = number(inputId);
  if (!Number.isFinite(n)) throw new Error(`${name} is required.`);
  return n;
}
function cleanForm(object) {
  return Object.fromEntries(
    Object.entries(object).filter(
      ([, v]) => v !== "" && v !== null && v !== undefined && !Number.isNaN(v),
    ),
  );
}
function time(value) {
  return value ? new Date(value).toLocaleString() : "—";
}
function escapeHtml(value = "") {
  return String(value).replace(
    /[&<>'"]/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[
        char
      ],
  );
}

function renderCards(target, items, renderer) {
  const container = $(target);
  if (!items?.length) {
    container.className = "data-grid empty-state";
    container.textContent = "No records found.";
    return;
  }
  container.className = "data-grid";
  container.innerHTML = items.map(renderer).join("");
}
function complaintCard(c) {
  return `<article class="data-card">${c.imageUrl ? `<img class="complaint-image" src="${escapeHtml(c.imageUrl)}" alt="${escapeHtml(c.title)}">` : ""}<span class="badge">${escapeHtml(c.status)}</span><h3>${escapeHtml(c.title)}</h3><p>${escapeHtml(c.issueType)} · ${escapeHtml(c.area)}, ${escapeHtml(c.city)}</p><p>Priority: ${escapeHtml(c.priority || "MEDIUM")} · Votes: ${c.upvotes ?? 0}</p><p class="hint">ID ${c.id} · ${time(c.reportedAt)}</p></article>`;
}
function workerCard(w) {
  return `<article class="data-card"><span class="badge">${escapeHtml(w.verificationStatus)}</span><h3>${escapeHtml(w.name)}</h3><p>${escapeHtml(w.skill)} · ${escapeHtml(w.serviceArea || "—")}</p><p>★ ${(w.rating ?? 0).toFixed?.(1) ?? w.rating} · ${w.completedTasks ?? 0} jobs</p><p class="hint">Worker ID ${w.id}</p></article>`;
}
function bookingCard(b) {
  return `<article class="data-card"><span class="badge">${escapeHtml(b.bookingStatus)}</span><h3>Booking #${b.id}</h3><p>₹${b.amount} · Worker #${b.workerId}</p><p>Complaint #${b.issueId}</p><p class="hint">${time(b.updatedAt)}</p>${b.bookingStatus === "PAYMENT_PENDING" ? `<button class="primary-button pay-booking" data-id="${b.id}" data-amount="${b.amount}">Pay now</button>` : ""}</article>`;
}
function paymentCard(p) {
  return `<article class="data-card"><span class="badge">${escapeHtml(p.paymentStatus)}</span><h3>₹${p.amount} ${escapeHtml(p.currency)}</h3><p>${escapeHtml(p.paymentSource)} · Booking #${p.bookingId}</p><p class="hint">${escapeHtml(p.razorpayOrderId || "No order ID")} · ${time(p.createdAt)}</p></article>`;
}

async function action(name) {
  try {
    let data;
    switch (name) {
      case "profile":
        data = await api("/api/users/me");
        setUser(data);
        showResult("Current user", data);
        break;
      case "complaints-list": {
        const q = new URLSearchParams({
          page: value("complaint-page") || "0",
          size: value("complaint-size") || "20",
        });
        if (value("complaint-status"))
          q.set("status", value("complaint-status"));
        data = await api(`/api/complaints?${q}`);
        renderCards("complaint-list", data.content, complaintCard);
        showResult("Complaint list", data);
        break;
      }
      case "complaints-mine": {
        data = await api(
          `/api/complaints/mine?page=${value("complaint-page") || 0}&size=${value("complaint-size") || 20}`,
        );
        renderCards("complaint-list", data.content, complaintCard);
        showResult("My complaints", data);
        break;
      }
      case "complaint-vote": {
        data = await api(
          `/api/complaints/${requireNumber("complaint-action-id", "Complaint ID")}/vote`,
          { method: "POST" },
        );
        showResult("Complaint vote", data);
        showToast("Vote recorded.");
        break;
      }
      case "complaint-verify": {
        data = await api(
          `/api/complaints/${requireNumber("complaint-action-id", "Complaint ID")}/verify`,
          { method: "POST" },
        );
        showResult("Complaint verification", data);
        showToast("Verification submitted.");
        break;
      }
      case "workers-browse": {
        const q = new URLSearchParams({
          page: value("worker-page") || "0",
          size: "20",
        });
        if (value("worker-skill")) q.set("skill", value("worker-skill"));
        data = await api(`/api/workers?${q}`);
        renderCards("worker-list", data.content, workerCard);
        showResult("Verified worker directory", data);
        break;
      }
      case "workers-me":
        data = await api("/api/workers/me");
        showResult("My worker profile", data);
        break;
      case "worker-profile":
        data = await api(
          `/api/workers/${requireNumber("worker-detail-id", "Worker ID")}`,
        );
        showResult("Worker profile", data);
        break;
      case "worker-portfolio":
        data = await api(
          `/api/workers/${requireNumber("worker-portfolio-id", "Worker ID")}/portfolio`,
        );
        showResult("Worker portfolio", data);
        break;
      case "worker-reviews":
        data = await api(
          `/api/workers/${requireNumber("worker-portfolio-id", "Worker ID")}/reviews`,
        );
        showResult("Worker reviews", data);
        break;
      case "assignment-request":
        data = await api(
          `/api/assignments/request?complaintId=${requireNumber("request-complaint-id", "Complaint ID")}&workerId=${requireNumber("request-worker-id", "Worker ID")}`,
          { method: "POST" },
        );
        showResult("Worker request", data);
        showToast("Worker request created.");
        break;
      case "assignment-create":
        data = await api(
          `/api/assignments?complaintId=${requireNumber("assign-complaint-id", "Complaint ID")}&workerId=${requireNumber("assign-worker-id", "Worker ID")}`,
          { method: "POST" },
        );
        showResult("Assignment created", data);
        showToast("Assignment created.");
        break;
      case "assignment-accept":
        data = await api(
          `/api/assignments/${requireNumber("accept-assignment-id", "Assignment ID")}/accept`,
          { method: "POST" },
        );
        showResult("Assignment accepted", data);
        break;
      case "assignment-complete":
        data = await api(
          `/api/assignments/${requireNumber("complete-assignment-id", "Assignment ID")}/complete`,
          {
            method: "POST",
            body: cleanForm({
              remarks: value("assignment-remarks"),
              beforeImageUrl: value("before-image-url"),
              afterImageUrl: value("after-image-url"),
            }),
          },
        );
        showResult("Assignment completion", data);
        showToast("Completion submitted.");
        break;
      case "assignment-verify":
        data = await api(
          `/api/assignments/${requireNumber("verify-assignment-id", "Assignment ID")}/citizen-verification?approved=${value("verification-decision")}`,
          { method: "POST" },
        );
        showResult("Citizen decision", data);
        break;
      case "review-create":
        data = await api(
          `/api/assignments/${requireNumber("review-assignment-id", "Assignment ID")}/reviews`,
          {
            method: "POST",
            body: {
              rating: requireNumber("review-rating", "Rating"),
              comment: value("review-comment") || null,
            },
          },
        );
        showResult("Worker review", data);
        showToast("Review published.");
        break;
      case "booking-create":
        data = await api("/api/bookings", {
          method: "POST",
          body: {
            workerId: requireNumber("booking-worker-id", "Worker ID"),
            issueId: requireNumber("booking-issue-id", "Complaint ID"),
            amount: requireNumber("booking-amount", "Amount"),
          },
        });
        showResult("Booking created", data);
        showToast("Booking created.");
        break;
      case "bookings-mine":
        data = await api("/api/bookings/mine");
        renderBookings(data);
        showResult("My bookings", data);
        break;
      case "booking-accept":
        data = await api(
          `/api/bookings/${requireNumber("booking-action-id", "Booking ID")}/accept`,
          { method: "POST" },
        );
        showResult("Booking accepted", data);
        break;
      case "booking-start":
        data = await api(
          `/api/bookings/${requireNumber("booking-action-id", "Booking ID")}/start`,
          { method: "POST" },
        );
        showResult("Booking started", data);
        break;
      case "booking-complete":
        data = await api(
          `/api/bookings/${requireNumber("booking-action-id", "Booking ID")}/complete`,
          { method: "POST" },
        );
        showResult("Booking completed", data);
        break;
      case "booking-confirm":
        data = await api(
          `/api/bookings/${requireNumber("booking-confirm-id", "Booking ID")}/confirm-completion`,
          { method: "POST" },
        );
        showResult("Completion confirmed", data);
        break;
      case "payment-order":
        data = await api("/api/payments/create-order", {
          method: "POST",
          body: {
            bookingId: requireNumber("payment-booking-id", "Booking ID"),
            amount: requireNumber("payment-amount", "Amount"),
            paymentSource: value("payment-source"),
          },
        });
        state.razorpayOrder = data;
        $("razorpay-order-id").value = data.orderId || "";
        $("open-checkout").disabled = false;
        showToast("Secure payment is ready. Opening Razorpay Checkout…");
        openRazorpayCheckout();
        break;
      case "payment-verify":
        data = await api("/api/payments/verify", {
          method: "POST",
          body: {
            razorpay_order_id: value("razorpay-order-id"),
            razorpay_payment_id: value("razorpay-payment-id"),
            razorpay_signature: value("razorpay-signature"),
          },
        });
        showResult("Payment verification", data);
        showToast(
          data.success ? "Payment verified." : "Payment verification failed.",
          !data.success,
        );
        break;
      case "payments-history":
        data = await api("/api/payments/history");
        renderCards("payment-list", data, paymentCard);
        showResult("Payment history", data);
        break;
      case "notifications-list":
        data = await api("/api/notifications");
        renderNotifications(data);
        showResult("Notifications", data);
        break;
      case "admin-verify-worker":
        data = await api(
          `/api/admin/workers/${requireNumber("admin-worker-id", "Worker ID")}/verification?approved=${value("admin-approved")}&notes=${encodeURIComponent(value("admin-notes"))}`,
          { method: "POST" },
        );
        showResult("Worker verification", data);
        break;
      case "admin-availability":
        data = await api(
          `/api/admin/workers/${requireNumber("availability-worker-id", "Worker ID")}/availability?available=${value("availability-value")}`,
          { method: "POST" },
        );
        showResult("Worker availability", data);
        break;
      case "admin-priority":
        data = await api(
          `/api/admin/complaints/${requireNumber("priority-complaint-id", "Complaint ID")}/priority?priority=${value("priority-value")}`,
          { method: "PATCH" },
        );
        showResult("Complaint priority", data);
        break;
      default:
        throw new Error("Action is not configured.");
    }
  } catch (error) {
    showToast(paymentFriendlyError(error.message), true);
  }
}

function paymentFriendlyError(message) {
  const known = {
    "Payment is available only after citizen completion confirmation":
      "This booking is not ready to pay yet. The citizen must confirm completed work first.",
    "Only the booking citizen can make a citizen payment":
      "Please sign in as the citizen who created this booking to make this payment.",
    "Payment amount must match the booking amount":
      "The payment amount must exactly match the booking amount.",
    "Razorpay is not configured":
      "Payments are not configured yet. Add Razorpay test keys to the backend environment and restart it.",
    "Razorpay could not create the order":
      "Razorpay could not start this payment. Check your Razorpay test keys and internet connection.",
  };
  return known[message] || message;
}

function renderBookings(bookings) {
  renderCards("booking-list", bookings, bookingCard);
  document.querySelectorAll(".pay-booking").forEach((button) =>
    button.addEventListener("click", () => {
      $("payment-booking-id").value = button.dataset.id;
      $("payment-amount").value = button.dataset.amount;
      $("payment-source").value = "CITIZEN";
      navigate("payments");
      showToast(
        "Booking ready for payment. Select “Pay securely” to continue.",
      );
    }),
  );
}

function renderNotifications(items) {
  const container = $("notification-list");
  const unread = items.filter((n) => !n.read).length;
  $("notification-dot").classList.toggle("hidden", unread === 0);
  if (!items.length) {
    container.className = "notification-list empty-state";
    container.textContent = "You have no notifications.";
    return;
  }
  container.className = "notification-list";
  container.innerHTML = items
    .map(
      (n) =>
        `<article class="notification-item ${n.read ? "" : "unread"}"><div><p class="eyebrow">${time(n.createdAt)}</p><h3>${escapeHtml(n.title)}</h3><p>${escapeHtml(n.message)}</p></div>${n.read ? '<span class="badge">Read</span>' : `<button class="secondary-button mark-read" data-id="${n.id}">Mark read</button>`}</article>`,
    )
    .join("");
  container.querySelectorAll(".mark-read").forEach((button) =>
    button.addEventListener("click", async () => {
      try {
        const data = await api(`/api/notifications/${button.dataset.id}/read`, {
          method: "POST",
        });
        showResult("Notification marked read", data);
        action("notifications-list");
      } catch (error) {
        showToast(error.message, true);
      }
    }),
  );
}

function setUser(user) {
  state.user = user;
  $("profile-name").textContent = user.username || "Civic user";
  $("profile-email").textContent = user.email || "";
  $("profile-initial").textContent = (user.username || user.email || "?")
    .slice(0, 1)
    .toUpperCase();
  $("auth-banner").style.display = "none";
  document.body.classList.remove("logged-out");
  document.body.classList.add("logged-in");
  localStorage.setItem("civicconnect_user", JSON.stringify(user));
}
async function loadSignedInUser() {
  try {
    const user = await api("/api/users/me");
    setUser(user);
    $("profile-result").textContent = JSON.stringify(user, null, 2);
    navigate("dashboard");
    return true;
  } catch (error) {
    clearSession();
    showToast(
      "Your sign-in session is invalid or expired. Please sign in again.",
      true,
    );
    return false;
  }
}
function clearSession(message = "") {
  state.token = "";
  state.user = null;
  localStorage.removeItem("civicconnect_token");
  localStorage.removeItem("civicconnect_user");
  $("profile-name").textContent = "Guest user";
  $("profile-email").textContent = "Sign in to continue";
  $("profile-initial").textContent = "?";
  $("auth-banner").style.display = "flex";
  document.body.classList.remove("logged-in");
  document.body.classList.add("logged-out");
  $("login-error").textContent = message;
}
function openModal(id) {
  $(id).classList.add("open");
  $(id).setAttribute("aria-hidden", "false");
}
function closeModals() {
  document.querySelectorAll(".modal").forEach((m) => {
    m.classList.remove("open");
    m.setAttribute("aria-hidden", "true");
  });
}
function navigate(id) {
  document
    .querySelectorAll(".view")
    .forEach((v) => v.classList.remove("active-view"));
  $(id).classList.add("active-view");
  document
    .querySelectorAll(".nav-link")
    .forEach((n) => n.classList.toggle("active", n.dataset.view === id));
  const title = {
    dashboard: "Civic action, in one place.",
    complaints: "Report. Track. Resolve.",
    workers: "A verified local workforce.",
    assignments: "A clear path to resolution.",
    bookings: "Services, coordinated.",
    payments: "Every payment, accounted for.",
    notifications: "What needs your attention.",
    admin: "Government operations console.",
    endpoints: "Backend API, fully connected.",
  };
  $("page-title").textContent = title[id];
  $("section-kicker").textContent =
    id === "dashboard" ? "Command centre" : "CivicConnect workspace";
  document.querySelector(".sidebar").classList.remove("open");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function setupForms() {
  $("complaint-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const fd = new FormData(event.currentTarget);
    const body = cleanForm({
      ...Object.fromEntries(fd),
      latitude: Number(fd.get("latitude")),
      longitude: Number(fd.get("longitude")),
    });
    try {
      const data = await api("/api/complaints", { method: "POST", body });
      showResult("Complaint created", data);
      closeModals();
      event.currentTarget.reset();
      showToast("Complaint submitted.");
    } catch (error) {
      showToast(error.message, true);
    }
  });
  $("worker-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const fd = new FormData(event.currentTarget);
    const raw = Object.fromEntries(fd);
    ["latitude", "longitude", "workRadiusKm", "experienceYears"].forEach(
      (key) => {
        raw[key] = raw[key] === "" ? null : Number(raw[key]);
      },
    );
    try {
      const data = await api("/api/workers/register", {
        method: "POST",
        body: cleanForm(raw),
      });
      showResult("Worker registration", data);
      closeModals();
      event.currentTarget.reset();
      showToast(
        "Worker profile submitted. Log in again to receive a worker JWT.",
      );
    } catch (error) {
      showToast(error.message, true);
    }
  });
}

function updateImagePreview(inputId, previewId, label) {
  const url = value(inputId);
  const preview = $(previewId);
  preview.innerHTML = url
    ? `<img src="${escapeHtml(url)}" alt="${label}" onerror="this.parentElement.innerHTML='<span>Image could not be loaded</span>'">`
    : `<span>${label} preview</span>`;
}

function openRazorpayCheckout() {
  const order = state.razorpayOrder;
  if (!order) return showToast("Create a Razorpay order first.", true);
  if (!window.Razorpay)
    return showToast(
      "Razorpay Checkout could not load. Check your internet connection.",
      true,
    );
  const checkout = new Razorpay({
    key: order.razorpayKey,
    amount: Math.round(Number(order.amount) * 100),
    currency: order.currency,
    name: "CivicConnect",
    description: "Civic service booking payment",
    order_id: order.orderId,
    theme: { color: "#b7f34b" },
    handler: async (response) => {
      $("razorpay-order-id").value = response.razorpay_order_id;
      $("razorpay-payment-id").value = response.razorpay_payment_id;
      $("razorpay-signature").value = response.razorpay_signature;
      await action("payment-verify");
    },
  });
  checkout.open();
}

async function initialise() {
  const params = new URLSearchParams(location.search);
  const callbackToken = params.get("token");
  if (callbackToken) {
    // Persist the token first, then reload a clean dashboard URL so the token never remains in browser history.
    localStorage.setItem("civicconnect_token", callbackToken);
    location.replace(`${location.origin}${location.pathname}#dashboard`);
    return;
  }
  $("google-login").addEventListener(
    "click",
    () => (location.href = `${state.apiUrl}/oauth2/authorization/google`),
  );
  $("gate-google-login").addEventListener(
    "click",
    () => (location.href = `${state.apiUrl}/oauth2/authorization/google`),
  );
  $("load-me").addEventListener("click", () => action("profile"));
  $("logout-button").addEventListener("click", () => {
    clearSession();
    navigate("dashboard");
    showToast("Local session cleared.");
  });
  document
    .querySelectorAll(".nav-link")
    .forEach((button) =>
      button.addEventListener("click", () => navigate(button.dataset.view)),
    );
  document
    .querySelectorAll(".go-to")
    .forEach((button) =>
      button.addEventListener("click", () => navigate(button.dataset.target)),
    );
  document
    .querySelectorAll("[data-modal]")
    .forEach((button) =>
      button.addEventListener("click", () => openModal(button.dataset.modal)),
    );
  document
    .querySelectorAll(".close-modal")
    .forEach((button) => button.addEventListener("click", closeModals));
  document.querySelectorAll(".modal").forEach((modal) =>
    modal.addEventListener("click", (event) => {
      if (event.target === modal) closeModals();
    }),
  );
  document
    .querySelectorAll(".endpoint-action")
    .forEach((button) =>
      button.addEventListener("click", () => action(button.dataset.endpoint)),
    );
  $("open-checkout").addEventListener("click", openRazorpayCheckout);
  $("before-image-url").addEventListener("input", () =>
    updateImagePreview("before-image-url", "before-preview", "Before image"),
  );
  $("after-image-url").addEventListener("input", () =>
    updateImagePreview("after-image-url", "after-preview", "After image"),
  );
  $("mobile-menu").addEventListener("click", () =>
    document.querySelector(".sidebar").classList.toggle("open"),
  );
  setupForms();
  if (state.token) await loadSignedInUser();
}

document.addEventListener("DOMContentLoaded", initialise);
