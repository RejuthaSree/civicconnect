"use strict";

const state = {
  apiUrl: "http://localhost:8080",
  token: localStorage.getItem("civicconnect_token") || "",
  user: null,
  razorpayOrder: null,
  lastBookings: [],
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
        "PATCH",
        "/api/admin/complaints/{id}/classification?category=&priority=&apply=true",
        "Override AI classification",
      ],
      [
        "GET",
        "/api/admin/complaints/{id}/duplicates",
        "Detect potential duplicates",
      ],
      [
        "POST",
        "/api/admin/complaints/duplicates/group?ids=1,2,3",
        "Group complaints as duplicates",
      ],
      [
        "DELETE",
        "/api/admin/complaints/{id}/duplicates/group",
        "Ungroup a complaint from duplicate set",
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
  let aiBadges = "";
  if (c.aiCategory || c.aiSeverity) {
    const confirmed = !!c.aiConfirmedByAdmin;
    const pct = typeof c.aiConfidence === "number" ? Math.round(c.aiConfidence * 100) : null;
    const cls = confirmed ? "badge ai-confirmed" : "badge ai-suggested";
    const label = confirmed ? "Admin confirmed" : "AI suggested";
    const parts = [];
    if (c.aiCategory) parts.push(escapeHtml(c.aiCategory));
    if (c.aiSeverity) parts.push(escapeHtml(c.aiSeverity));
    if (pct !== null) parts.push(pct + "%");
    aiBadges = `<span class="${cls}" title="${label}${c.aiSuggestedDepartment ? " · " + escapeHtml(c.aiSuggestedDepartment) : ""}">${label}${parts.length ? " · " + parts.join(" / ") : ""}</span>`;
  }
  let dupBadges = "";
  if (c.duplicateGroupId) {
    dupBadges += `<span class="badge duplicate-group" title="Duplicate group">Dup group · ${escapeHtml(c.duplicateGroupId)}</span>`;
  }
  if (c.potentialDuplicates && c.potentialDuplicates.length) {
    const list = c.potentialDuplicates.map((d) =>
      `#${d.id} ${escapeHtml(d.title)}${typeof d.similarity === "number" ? " (" + Math.round(d.similarity * 100) + "%)" : ""}${typeof d.distanceKm === "number" ? " · " + d.distanceKm.toFixed(1) + " km" : ""}`
    ).join("; ");
    dupBadges += `<span class="badge duplicate-warning" title="Potential matches: ${escapeHtml(list)}">⚠ ${c.potentialDuplicates.length} potential dup${c.potentialDuplicates.length === 1 ? "" : "es"}</span>`;
  }
  let slaBadges = "";
  const lvl = c.escalationLevel ?? 0;
  if (lvl >= 2) slaBadges += `<span class="badge sla-admin" title="Escalated to ADMIN due to SLA">🔴 ADMIN ESCALATION</span>`;
  else if (lvl === 1) slaBadges += `<span class="badge sla-supervisor" title="Escalated to SUPERVISOR due to SLA">🟠 SUPERVISOR</span>`;
  if (c.slaBreached) slaBadges += `<span class="badge sla-breached" title="SLA deadline exceeded">⚠ SLA BREACH</span>`;
  else if (c.slaRemainingHours != null && typeof c.slaRemainingHours === "number") {
    const hrs = c.slaRemainingHours;
    if (hrs <= 2) slaBadges += `<span class="badge sla-warn" title="SLA deadline approaching">⏱ ${hrs.toFixed(1)}h left</span>`;
    else slaBadges += `<span class="badge sla-ok" title="SLA on track">⏱ ${hrs.toFixed(1)}h left</span>`;
  }
  return `<article class="data-card">${c.imageUrl ? `<img class="complaint-image" src="${escapeHtml(c.imageUrl)}" alt="${escapeHtml(c.title)}">` : ""}<span class="badge">${escapeHtml(c.status)}</span>${aiBadges}${dupBadges}${slaBadges}<h3>${escapeHtml(c.title)}</h3><p>${escapeHtml(c.issueType)} · ${escapeHtml(c.area)}, ${escapeHtml(c.city)}</p><p>${c.issueScope === "HOUSEHOLD" ? "Household issue · citizen payment" : "Public issue · government payment"}</p><p>Priority: ${escapeHtml(c.priority || "MEDIUM")} · Votes: ${c.upvotes ?? 0}</p><p class="hint">Reference #${c.id} · ${time(c.reportedAt)}${c.slaDeadline ? ` · SLA: ${time(c.slaDeadline)}` : ""}</p></article>`;
}
function workerCard(w) {
  return `<article class="data-card"><span class="badge">${escapeHtml(w.verificationStatus)}</span><h3>${escapeHtml(w.name)}</h3><p>${escapeHtml(w.skill)} · ${escapeHtml(w.serviceArea || "—")}</p><p>★ ${(w.rating ?? 0).toFixed?.(1) ?? w.rating} · ${w.completedTasks ?? 0} jobs</p><p class="hint">Worker ID ${w.id}</p></article>`;
}
function assignmentCard(a) {
  const evidence = a.afterImageUrl ? `<img class="complaint-image" src="${escapeHtml(a.afterImageUrl)}" alt="Completed work evidence">` : "";
  return `<article class="data-card">${evidence}<span class="badge">${escapeHtml(a.completionStatus || "AWAITING_ACCEPTANCE")}</span><h3>Assignment #${a.id}</h3><p>Complaint #${a.complaintId} · Worker #${a.workerId}</p><p>${a.remarks ? escapeHtml(a.remarks) : "No completion note yet."}</p><p class="hint">Assigned ${time(a.assignedAt)}</p></article>`;
}
function bookingCard(b) {
  const canPay = b.bookingStatus === "PAYMENT_PENDING" && ((b.issueScope === "HOUSEHOLD" && state.user?.role === "CITIZEN") || (b.issueScope === "PUBLIC" && state.user?.role === "ADMIN"));
  return `<article class="data-card"><span class="badge">${escapeHtml(b.bookingStatus)}</span><h3>Booking #${b.id}</h3><p>₹${b.amount} · Worker #${b.workerId}</p><p>Complaint #${b.issueId} · ${b.issueScope === "HOUSEHOLD" ? "Citizen payment" : "Government payment"}</p><p class="hint">${time(b.updatedAt)}</p>${canPay ? `<button class="primary-button pay-booking" data-id="${b.id}" data-amount="${b.amount}" data-source="${b.issueScope === "HOUSEHOLD" ? "CITIZEN" : "GOVERNMENT"}">Pay securely</button>` : ""}</article>`;
}

function fillSelect(id, items, label) {
  const select = $(id);
  if (!select) return;
  select.innerHTML = `<option value="">Choose an option</option>${items.map((item) => `<option value="${item.id}">${escapeHtml(label(item))}</option>`).join("")}`;
}

async function loadAssignmentChoices() {
  if (!state.user?.role) return;
  try {
    const [complaintPage, workerPage, assignments, bookings, adminWorkers] = await Promise.all([
      api(state.user.role === "CITIZEN" ? "/api/complaints/mine?page=0&size=100" : "/api/complaints?page=0&size=100"),
      api("/api/workers?page=0&size=100"),
      api("/api/assignments/mine"),
      api("/api/bookings/mine"),
      state.user.role === "ADMIN" ? api("/api/admin/workers") : Promise.resolve([]),
    ]);
    state.lastBookings = bookings;
    const complaints = complaintPage.content || [];
    const workers = workerPage.content || [];
    const complaintLabel = (c) => `#${c.id} — ${c.title} (${c.status})`;
    const workerLabel = (w) => `#${w.id} — ${w.name} · ${w.skill}`;
    fillSelect("request-complaint-id", complaints.filter((c) => !c.assignedWorkerId), complaintLabel);
    fillSelect("assign-complaint-id", complaints.filter((c) => !c.assignedWorkerId), complaintLabel);
    fillSelect("request-worker-id", workers, workerLabel);
    fillSelect("assign-worker-id", workers, workerLabel);
    fillSelect("booking-worker-id", workers, workerLabel);
    fillSelect("worker-detail-id", workers, workerLabel);
    fillSelect("worker-portfolio-id", workers, workerLabel);
    fillSelect("admin-worker-id", adminWorkers, workerLabel);
    fillSelect("availability-worker-id", adminWorkers, workerLabel);
    fillSelect("priority-complaint-id", complaints, complaintLabel);
    fillSelect("classify-complaint-id", complaints, complaintLabel);
    fillSelect("dup-detect-complaint-id", complaints, complaintLabel);
    fillSelect("dup-ungroup-complaint-id", complaints, complaintLabel);
    fillSelect("escalate-complaint-id", complaints, complaintLabel);
    fillSelect("booking-issue-id", complaints, complaintLabel);
    const assignmentLabel = (a) => `#${a.id} — complaint #${a.complaintId} · ${a.completionStatus || "awaiting acceptance"}`;
    fillSelect("accept-assignment-id", assignments.filter((a) => !a.acceptedAt), assignmentLabel);
    fillSelect("complete-assignment-id", assignments.filter((a) => a.acceptedAt && !a.completedAt), assignmentLabel);
    fillSelect("verify-assignment-id", assignments.filter((a) => a.completionStatus === "PENDING_CITIZEN_APPROVAL"), assignmentLabel);
    fillSelect("review-assignment-id", assignments.filter((a) => a.completionStatus === "APPROVED"), assignmentLabel);
    const bookingLabel = (b) => `#${b.id} — complaint #${b.issueId} · ₹${b.amount} · ${b.bookingStatus}`;
    fillSelect("booking-action-id", bookings.filter((b) => ["PENDING", "ACCEPTED", "IN_PROGRESS"].includes(b.bookingStatus)), bookingLabel);
    fillSelect("booking-confirm-id", bookings.filter((b) => b.bookingStatus === "COMPLETED"), bookingLabel);
    fillSelect("payment-booking-id", bookings.filter((b) => b.bookingStatus === "PAYMENT_PENDING"), bookingLabel);
  } catch (error) {
    showToast("Could not load complaint and worker choices.", true);
  }
}
function paymentCard(p) {
  return `<article class="data-card"><span class="badge">${escapeHtml(p.paymentStatus)}</span><h3>₹${p.amount} ${escapeHtml(p.currency)}</h3><p>${escapeHtml(p.paymentSource)} · Booking #${p.bookingId}</p><p class="hint">${escapeHtml(p.razorpayOrderId || "No order ID")} · ${time(p.createdAt)}</p></article>`;
}

function renderCitizenProfile(user) {
  $("profile-result").innerHTML = `<div class="profile-details"><strong>${escapeHtml(user.username || "Civic user")}</strong><span>${escapeHtml(user.email || "")}</span><span class="badge">${escapeHtml(user.role || "CITIZEN")}</span><p>Your reports, bookings, confirmations, and payments are available from this workspace.</p></div>`;
}

function renderWorkerProfile(worker) {
  $("profile-result").innerHTML = `<div class="profile-details">${worker.profilePhotoUrl ? `<img class="profile-photo" src="${escapeHtml(worker.profilePhotoUrl)}" alt="${escapeHtml(worker.name)}">` : ""}<strong>${escapeHtml(worker.name)}</strong><span class="badge">${escapeHtml(worker.verificationStatus)}</span><span>${escapeHtml(worker.skill)} · ${escapeHtml(worker.serviceArea || "Service area not set")}</span><p>${worker.experienceYears || 0} years experience · ★ ${worker.rating ?? 0} (${worker.totalReviews ?? 0} reviews) · ${worker.completedTasks ?? 0} completed jobs</p><p>${worker.available ? "Available for work" : "Currently unavailable"}</p></div>`;
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
      case "complaint-duplicates": {
        const targetId = requireNumber("complaint-action-id", "Complaint ID");
        data = await api(`/api/complaints/${targetId}/with-duplicates`);
        renderDuplicateResult(data);
        openModal("dup-result-modal");
        showResult(`Duplicate scan for #${targetId}`, data);
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
        renderWorkerProfile(data);
        navigate("dashboard");
        showToast("Your worker profile is shown on the dashboard.");
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
      case "assignments-mine":
        data = await api("/api/assignments/mine");
        renderCards("assignment-list", data, assignmentCard);
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
        loadAssignmentChoices();
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
        loadAssignmentChoices();
        break;
      case "booking-start":
        data = await api(
          `/api/bookings/${requireNumber("booking-action-id", "Booking ID")}/start`,
          { method: "POST" },
        );
        showResult("Booking started", data);
        loadAssignmentChoices();
        break;
      case "booking-complete":
        data = await api(
          `/api/bookings/${requireNumber("booking-action-id", "Booking ID")}/complete`,
          { method: "POST" },
        );
        showResult("Booking completed", data);
        loadAssignmentChoices();
        break;
      case "booking-confirm":
        data = await api(
          `/api/bookings/${requireNumber("booking-confirm-id", "Booking ID")}/confirm-completion`,
          { method: "POST" },
        );
        showResult("Completion confirmed", data);
        showToast("Completion confirmed. Payment is now available for this booking.");
        loadAssignmentChoices();
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
        if (data.success) loadAssignmentChoices();
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
      case "admin-classify":
        data = await api(
          `/api/admin/complaints/${requireNumber("classify-complaint-id", "Complaint ID")}/classification?category=${value("classify-category")}&priority=${value("classify-priority")}&apply=${value("classify-apply") === "false" ? "false" : "true"}`,
          { method: "PATCH" },
        );
        showResult("AI classification overridden", data);
        showToast("Classification updated.");
        break;
      case "admin-dup-detect": {
        const targetId = requireNumber("dup-detect-complaint-id", "Complaint ID");
        data = await api(`/api/admin/complaints/${targetId}/duplicates`);
        const matches = data.potentialDuplicates || [];
        if (matches.length) {
          renderCards("complaint-list", [data, ...matches.map((m) => ({ ...(m), id: m.id, title: m.title, issueType: data.issueType, area: data.area, city: data.city, issueScope: data.issueScope, priority: data.priority, status: "POTENTIAL_DUPLICATE · sim " + Math.round((m.similarity || 0) * 100) + "%", upvotes: (typeof m.distanceKm === "number" ? (m.distanceKm.toFixed(1) + " km") : "area match"), reportedAt: data.reportedAt, imageUrl: null, aiCategory: null, aiSeverity: null, aiConfidence: null, aiConfirmedByAdmin: null, duplicateGroupId: null, potentialDuplicates: null, reporterId: data.reporterId, assignedWorkerId: null }))].map((x, i) => i === 0 ? x : ({ ...x, status: "Potential dup: " + (x.status || "").replace("POTENTIAL_DUPLICATE · ", "") })), complaintCard);
        } else {
          renderCards("complaint-list", [data], complaintCard);
        }
        showResult(`Duplicate detection (${matches.length} match${matches.length === 1 ? "" : "es"})`, data);
        showToast(matches.length ? `Found ${matches.length} potential duplicate${matches.length === 1 ? "" : "s"}.` : "No potential duplicates found.");
        break;
      }
      case "admin-dup-group": {
        const raw = value("dup-group-ids");
        if (!raw) throw new Error("Enter at least 2 complaint IDs separated by commas.");
        const parts = raw.split(",").map((s) => s.trim()).filter((s) => s);
        if (parts.some((s) => !/^\d+$/.test(s))) throw new Error("IDs must be numbers separated by commas.");
        if (parts.length < 2) throw new Error("Group at least 2 complaint IDs.");
        data = await api(`/api/admin/complaints/duplicates/group?ids=${encodeURIComponent(parts.join(","))}`, { method: "POST" });
        renderCards("complaint-list", data, complaintCard);
        showResult("Duplicate group created", data);
        showToast(`Group ${data[0]?.duplicateGroupId || ""} updated with ${data.length} complaint${data.length === 1 ? "" : "s"}.`);
        break;
      }
      case "admin-dup-ungroup": {
        const targetId = requireNumber("dup-ungroup-complaint-id", "Complaint ID");
        data = await api(`/api/admin/complaints/${targetId}/duplicates/group`, { method: "DELETE" });
        showResult("Ungrouped complaint", data);
        showToast("Complaint removed from duplicate group.");
        break;
      }
      case "admin-escalate": {
        const targetId = requireNumber("escalate-complaint-id", "Complaint ID");
        const lvl = value("escalate-level") || "";
        const lvlParam = lvl ? `&level=${lvl}` : "";
        data = await api(`/api/admin/complaints/${targetId}/escalate?dummy=1${lvlParam}`, { method: "POST" });
        showResult("Complaint escalation", data);
        showToast(`Complaint #${targetId} escalated to level ${data.escalationLevel ?? "N/A"}.`);
        break;
      }
      case "admin-escalated-list": {
        data = await api("/api/admin/complaints/escalated");
        renderCards("complaint-list", (data || []).map((e) => ({
          id: e.complaintId,
          title: e.title,
          status: (e.status || "") + (e.escalationLevel >= 2 ? " · ADMIN ESCALATED" : e.escalationLevel === 1 ? " · SUPERVISOR ESCALATED" : ""),
          area: "—", city: "—",
          issueType: "—",
          issueScope: "PUBLIC",
          priority: e.priority || "MEDIUM",
          upvotes: e.remainingHours != null ? `${e.remainingHours.toFixed(1)}h` : (e.slaBreached ? "SLA BREACHED" : "—"),
          reportedAt: e.slaDeadline,
          imageUrl: null,
          aiCategory: null, aiSeverity: null, aiConfidence: null, aiConfirmedByAdmin: null,
          duplicateGroupId: null, potentialDuplicates: null, reporterId: null, assignedWorkerId: null,
          slaDeadline: e.slaDeadline, slaBreached: e.slaBreached, slaRemainingHours: e.remainingHours,
          escalationLevel: e.escalationLevel
        })), complaintCard);
        showResult("Escalated complaints", data);
        showToast(`${(data || []).length} escalated complaint${(data || []).length === 1 ? "" : "s"} loaded.`);
        break;
      }
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
  state.lastBookings = bookings;
  renderCards("booking-list", bookings, bookingCard);
  const bookingLabel = (b) => `#${b.id} — complaint #${b.issueId} · ₹${b.amount} · ${b.bookingStatus}`;
  fillSelect("payment-booking-id", bookings.filter((b) => b.bookingStatus === "PAYMENT_PENDING"), bookingLabel);
  document.querySelectorAll(".pay-booking").forEach((button) =>
    button.addEventListener("click", () => {
      $("payment-booking-id").value = button.dataset.id;
      $("payment-amount").value = button.dataset.amount;
      $("payment-source").value = button.dataset.source;
      $("payment-source").disabled = true;
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
  applyRoleWorkspace();
  loadAssignmentChoices();
}

function applyRoleWorkspace() {
  const role = state.user?.role || "CITIZEN";
  document.querySelectorAll("[data-roles]").forEach((item) => {
    item.classList.toggle("hidden", !item.dataset.roles.split(",").includes(role));
  });
  const dashboard = {
    CITIZEN: {
      title: "Your neighbourhood, moving forward.",
      copy: "Report a household or public issue, choose a verified worker, and follow its progress from one clear place.",
      actions: [["complaints", "Report an issue"], ["workers", "Find a worker"]],
      steps: [["01", "Report", "Choose public or household so payment responsibility is clear."], ["02", "Choose", "Select one of the verified workers shown to you."], ["03", "Confirm", "Review completed work and pay securely when required."]],
    },
    WORKER: {
      title: "Today’s field work, clearly organised.",
      copy: "Keep your professional profile current, accept only your assigned work, and share before-and-after proof for citizens.",
      actions: [["assignments", "View my assignments"], ["bookings", "Manage my bookings"]],
      steps: [["01", "Receive", "See nearby requests and assignments intended for you."], ["02", "Resolve", "Accept, complete, and attach clear photo evidence."], ["03", "Build trust", "Citizens verify work and leave reviews on your profile."]],
    },
    ADMIN: {
      title: "Public services, under control.",
      copy: "Prioritise civic issues, verify workers, coordinate assignments, and pay approved public-issue work through Razorpay.",
      actions: [["admin", "Open admin console"], ["complaints", "Review public issues"]],
      steps: [["01", "Triage", "Set priorities and match verified workers to reported issues."], ["02", "Govern", "Approve worker applications and manage availability."], ["03", "Pay", "Use the government payment flow only for completed public work."]],
    },
  }[role];
  $("dashboard-title").textContent = dashboard.title;
  $("dashboard-copy").textContent = dashboard.copy;
  $("dashboard-actions").innerHTML = dashboard.actions.map(([target, text]) => `<button class="${target === dashboard.actions[0][0] ? "primary-button" : "secondary-button"} go-to" data-target="${target}">${text} <span>→</span></button>`).join("");
  $("dashboard-steps").innerHTML = dashboard.steps.map(([number, heading, text]) => `<article><span>${number}</span><strong>${heading}</strong><p>${text}</p></article>`).join("");
  document.querySelectorAll("#dashboard-actions .go-to").forEach((button) => button.addEventListener("click", () => navigate(button.dataset.target)));
  renderCitizenProfile({ ...state.user, role });
  if (role === "WORKER") action("workers-me");
}
async function loadSignedInUser() {
  try {
    const user = await api("/api/users/me");
    setUser(user);
    applyRoleWorkspace();
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
    government: "Municipal command centre.",
    admin: "Government operations console.",
    endpoints: "Backend API, fully connected.",
  };
  $("page-title").textContent = title[id];
  $("section-kicker").textContent =
    id === "dashboard" || id === "government" ? "Command centre" : "CivicConnect workspace";
  document.querySelector(".sidebar").classList.remove("open");
  window.scrollTo({ top: 0, behavior: "smooth" });
  if (id === "government") refreshGovernmentView();
}

function renderDuplicateResult(complaint) {
  const matches = complaint?.potentialDuplicates || [];
  $("dup-result-title").textContent = complaint?.id ? `Complaint #${complaint.id} · ${matches.length} potential dup${matches.length === 1 ? "" : "es"}` : "Potential duplicate complaints";
  const items = [];
  if (complaint?.id) {
    items.push(complaintCard({ ...complaint, potentialDuplicates: null, status: "YOUR REPORT · " + (complaint.status || "") }));
  }
  for (const m of matches) {
    const pct = typeof m.similarity === "number" ? Math.round(m.similarity * 100) : null;
    const dist = typeof m.distanceKm === "number" ? m.distanceKm.toFixed(1) + " km" : "area match";
    const badge = pct !== null ? `${pct}% similar · ${dist}` : dist;
    items.push(complaintCard({ id: m.id, title: m.title, issueType: complaint?.issueType || "—", area: complaint?.area || "—", city: complaint?.city || "—", issueScope: complaint?.issueScope, priority: complaint?.priority, status: badge, upvotes: dist, reportedAt: complaint?.reportedAt, imageUrl: null, aiCategory: null, aiSeverity: null, aiConfidence: null, aiConfirmedByAdmin: null, duplicateGroupId: null, potentialDuplicates: null, reporterId: complaint?.reporterId, assignedWorkerId: null }));
  }
  if (!items.length) {
    $("dup-result-body").className = "data-grid empty-state";
    $("dup-result-body").textContent = "No potential duplicates detected for this complaint.";
    return;
  }
  $("dup-result-body").className = "data-grid";
  $("dup-result-body").innerHTML = items.join("");
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
      event.currentTarget.reset();
      const matches = data?.potentialDuplicates || [];
      if (matches.length) {
        showToast(`Complaint #${data.id} submitted · ${matches.length} potential dup${matches.length === 1 ? "" : "es"} found.`);
        closeModals();
        renderDuplicateResult(data);
        openModal("dup-result-modal");
      } else {
        closeModals();
        showToast("Complaint submitted.");
      }
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
  if ($("payment-booking-id")) {
    $("payment-booking-id").addEventListener("change", () => {
      const bid = Number($("payment-booking-id").value);
      if (!Number.isFinite(bid)) {
        $("payment-amount").value = "";
        $("payment-source").disabled = false;
        return;
      }
      const match = state.lastBookings.find((b) => b.id === bid);
      if (match) {
        $("payment-amount").value = match.amount == null ? "" : match.amount;
        $("payment-source").value = match.issueScope === "HOUSEHOLD" ? "CITIZEN" : "GOVERNMENT";
        $("payment-source").disabled = true;
      } else {
        $("payment-source").disabled = false;
      }
    });
  }
  $("mobile-menu").addEventListener("click", () =>
    document.querySelector(".sidebar").classList.toggle("open"),
  );
  if ($("gov-refresh")) $("gov-refresh").addEventListener("click", refreshGovernmentStats);
  if ($("gov-map-refresh")) $("gov-map-refresh").addEventListener("click", refreshGovernmentMap);
  setupForms();
  if (state.token) await loadSignedInUser();
}

let govMap = null;
let govMarkers = [];
const STATUS_PALETTE = {
  REPORTED: "#f59e0b",
  UNDER_REVIEW: "#f59e0b",
  ASSIGNED: "#3b82f6",
  WORK_ACCEPTED: "#3b82f6",
  IN_PROGRESS: "#3b82f6",
  WORK_COMPLETED: "#3b82f6",
  CITIZEN_VERIFICATION: "#3b82f6",
  PAYMENT_APPROVED: "#22c55e",
  RESOLVED: "#22c55e",
  REJECTED: "#ef4444",
};
function colorForStatus(s) { return STATUS_PALETTE[s] || "#94a3b8"; }
function pinDotSvg(color) {
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 28 36'><path fill='${color}' stroke='rgba(0,0,0,0.25)' stroke-width='1.2' d='M14 2C7.925 2 3 6.925 3 13c0 8 11 21 11 21s11-13 11-21C25 6.925 20.075 2 14 2z'/><circle cx='14' cy='13' r='4.2' fill='white'/></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

async function refreshGovernmentView() {
  if (state.user?.role !== "ADMIN") return;
  await Promise.all([refreshGovernmentStats(), refreshGovernmentMap()]);
}
async function refreshGovernmentStats() {
  if (!state.token) return;
  try {
    const s = await api("/api/admin/stats");
    $("gov-kpi-total").textContent = s.totalComplaints ?? 0;
    $("gov-kpi-open").textContent = s.openComplaints ?? 0;
    $("gov-kpi-inprog").textContent = s.inProgressComplaints ?? 0;
    $("gov-kpi-res-day").textContent = s.resolvedToday ?? 0;
    $("gov-kpi-res-7d").textContent = s.resolvedLast7Days ?? 0;
    $("gov-kpi-new-day").textContent = s.reportedToday ?? 0;
    $("gov-kpi-new-7d").textContent = s.reportedLast7Days ?? 0;
    $("gov-kpi-avg").textContent = s.avgResolutionHours != null ? `${s.avgResolutionHours} h` : "—";
    $("gov-kpi-sla").textContent = s.slaBreached ?? 0;
    const compliance = s.slaCompliancePct != null ? `${s.slaCompliancePct}%` : "—";
    const metTotal = s.slaMetTotal ?? 0;
    const supervisorEsc = s.slaEscalatedSupervisor ?? 0;
    const adminEsc = s.slaEscalatedAdmin ?? 0;
    const byPrio = s.slaBreachedByPriority || {};
    const bpText = Object.entries(byPrio).filter(([_, v]) => v > 0).map(([k, v]) => `${k}:${v}`).join(" · ") || "none";
    if ($("gov-kpi-compliance")) $("gov-kpi-compliance").textContent = compliance;
    if ($("gov-kpi-met")) $("gov-kpi-met").textContent = metTotal;
    if ($("gov-kpi-esc-sup")) $("gov-kpi-esc-sup").textContent = supervisorEsc;
    if ($("gov-kpi-esc-admin")) $("gov-kpi-esc-admin").textContent = adminEsc;
    if ($("gov-kpi-bp")) $("gov-kpi-bp").textContent = bpText;
    $("gov-kpi-w-total").textContent = s.totalWorkers ?? 0;
    $("gov-kpi-w-verified").textContent = s.verifiedWorkers ?? 0;
    $("gov-kpi-act-assn").textContent = s.activeAssignments ?? 0;
    $("gov-kpi-pub-done").textContent = s.completedPublicAssignments ?? 0;
    $("gov-kpi-pub-unpaid").textContent = s.unpaidPublicAssignments ?? 0;
    renderBreakdown("gov-areas", (s.topAreas || []).map(a => [
      a.area, `${a.complaints} total`, `${a.open || 0} open · ${a.resolved || 0} resolved`
    ]));
    renderBreakdown("gov-status", Object.entries(s.countsByStatus || {}).map(([k, v]) => [k, `${v} complaints`, null]));
    renderBreakdown("gov-types", Object.entries(s.countsByIssueType || {}).map(([k, v]) => [k, `${v} complaints`, null]));
    renderBreakdown("gov-priority", Object.entries(s.countsByPriority || {}).map(([k, v]) => [k, `${v} complaints`, null]));
    renderBreakdown("gov-sla-bp", Object.entries(byPrio).map(([k, v]) => [k, `${v} breached`, null]));
  } catch (e) {
    showToast(e.message, true);
  }
}
function renderBreakdown(elId, rows) {
  const el = $(elId);
  if (!rows || !rows.length) {
    el.className = "gov-list empty-state";
    el.textContent = "No data yet.";
    return;
  }
  el.className = "gov-list";
  const max = Math.max(...rows.map((r) => typeof r[1] === "number" ? r[1] : 1), 1);
  el.innerHTML = rows.map(([label, big, subtitle]) => {
    const numeric = typeof big === "number";
    const val = numeric ? big : big;
    const pct = numeric ? Math.min(100, Math.round((big / max) * 100)) : null;
    return `<div class="gov-row">
      <div class="gov-row-head"><strong>${escapeHtml(label)}</strong><span>${escapeHtml(String(val))}</span></div>
      ${pct !== null ? `<div class="gov-bar"><div style="width:${pct}%"></div></div>` : ""}
      ${subtitle ? `<small class="gov-row-sub">${escapeHtml(subtitle)}</small>` : ""}
    </div>`;
  }).join("");
}
async function refreshGovernmentMap() {
  if (!state.token || !$("gov-map")) return;
  if (!govMap) {
    if (typeof L === "undefined") {
      showToast("Leaflet is not loaded yet.", true);
      return;
    }
    govMap = L.map("gov-map", { scrollWheelZoom: false }).setView([20.5937, 78.9629], 5);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap contributors"
    }).addTo(govMap);
    // Invalidate layout when the map container becomes visible.
    const observer = new ResizeObserver(() => govMap.invalidateSize());
    observer.observe($("gov-map"));
  } else {
    govMarkers.forEach((m) => govMap.removeLayer(m));
    govMarkers = [];
  }
  const params = new URLSearchParams();
  const s = $("gov-f-status")?.value;
  const p = $("gov-f-priority")?.value;
  const t = $("gov-f-type")?.value;
  const a = $("gov-f-area")?.value?.trim();
  if (s) params.set("status", s);
  if (p) params.set("priority", p);
  if (t) params.set("issueType", t);
  if (a) params.set("area", a);
  const qs = params.toString();
  let items = [];
  try {
    items = await api(`/api/admin/complaints/geo${qs ? "?" + qs : ""}`) || [];
  } catch (e) {
    showToast(e.message, true);
    return;
  }
  $("gov-map-count").textContent = `${items.length} location${items.length === 1 ? "" : "s"}`;
  const bounds = [];
  for (const c of items) {
    if (!c?.latitude || !c?.longitude) continue;
    const color = colorForStatus(c.status || "");
    const icon = L.icon({
      iconUrl: pinDotSvg(color),
      iconSize: [26, 34],
      iconAnchor: [13, 33],
      popupAnchor: [0, -30]
    });
    const marker = L.marker([c.latitude, c.longitude], { icon, title: `#${c.id} ${c.title || ""}` });
    const body = `<div class="gov-popup">
      <p class="eyebrow">Complaint #${c.id} · ${escapeHtml(c.priority || "—")}</p>
      <h3>${escapeHtml(c.title || "Untitled")}</h3>
      <ul>
        <li><b>Status:</b> ${escapeHtml(c.status || "—")}</li>
        <li><b>Type:</b> ${escapeHtml(c.issueType || "—")}</li>
        <li><b>Area:</b> ${escapeHtml(c.area || "—")}, ${escapeHtml(c.city || "—")}</li>
        <li><b>Reported:</b> ${escapeHtml(c.reportedAt || "—")}</li>
      </ul>
      <button class="primary-button gov-popup-open" data-id="${c.id}">Open complaint →</button>
    </div>`;
    marker.bindPopup(body);
    marker.on("popupopen", (e) => {
      const btn = e.popup._contentNode?.querySelector?.(".gov-popup-open");
      if (btn) btn.addEventListener("click", () => {
        marker.closePopup();
        navigate("complaints");
        setTimeout(() => {
          showToast(`Opening complaint #${btn.dataset.id}`);
        }, 150);
      });
    });
    marker.addTo(govMap);
    govMarkers.push(marker);
    bounds.push([c.latitude, c.longitude]);
  }
  govMap.invalidateSize();
  if (bounds.length) {
    const b = L.latLngBounds(bounds);
    govMap.fitBounds(b, { padding: [28, 28], maxZoom: 14 });
  }
}

document.addEventListener("DOMContentLoaded", initialise);
