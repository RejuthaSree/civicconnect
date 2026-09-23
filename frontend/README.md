# CivicConnect Frontend

CivicConnect has a responsive, dark-themed interface built with plain HTML, CSS, and JavaScript. It calls the Spring Boot API directly; no frontend framework or build step is required.

## Run locally

From the repository root:

```powershell
node .\frontend\server.js
```

Open [http://localhost:5500](http://localhost:5500). Start the backend on `http://localhost:8080` first. The API CORS configuration and Google OAuth callback are configured for port `5500`.

## Frontend files

| File | Responsibility |
| --- | --- |
| `index.html` | Application layout, forms, modals, role-specific sections, and Razorpay/Leaflet scripts. |
| `styles.css` | Dark theme, responsive layouts, cards, forms, dashboards, map, and status styles. |
| `app.js` | Authentication, API calls, role navigation, workflow actions, live data rendering, and Razorpay Checkout. |
| `server.js` | Minimal Node.js static server on port `5500`. |

## Features available in the interface

| Area | What the user can do | Roles |
| --- | --- | --- |
| Sign-in and profile | Sign in with Google, load the role-aware profile, and sign out locally. | All |
| Dashboard | See a task-focused citizen, worker, or administrator dashboard. | All |
| Complaint reporting | Create a report with address, coordinates, image URL, priority, and grouped issue type selection. Payment responsibility is set automatically. | Citizen |
| Complaints | Browse, filter, view status/SLA/AI/duplicate badges, upvote once, verify resolution, and check duplicate matches. | Citizen, Admin |
| Worker directory | Browse verified workers, load worker details, portfolios, reviews, and register a worker profile. | Citizen, Worker, Admin |
| Assignments | Request/assign workers and progress work through accept, completion evidence, citizen verification, and review. Dropdowns show readable choices instead of requiring IDs. | Citizen, Worker, Admin |
| Bookings | Create fixed-price bookings, progress work, confirm completion, and view relevant bookings. | Citizen, Worker, Admin |
| Payments | Start real Razorpay Checkout and display payment history. Household work is citizen-paid; public work is government-paid after administrator verification. | Citizen, Admin |
| Admin console | Verify workers, change availability/priority, review AI classification, manage duplicates/SLA escalation, and use government payment approval. | Admin |
| Government dashboard | View civic KPIs, SLA data, work summaries, and a filterable complaint map. | Admin |
| Notifications | Load and mark account notifications as read. | All |

## Role-based workflow

| Role | Typical path |
| --- | --- |
| Citizen | Report issue → choose worker → track work → verify completion → pay household work or wait for government public-work payment. |
| Worker | Register profile → wait for approval → accept assigned work → submit before/after proof → receive verification and payment. |
| Administrator | Triage reports → approve workers → assign public work → review public completion → verify and release government payment. |

## Important behaviour

- Complaint and worker choices are populated from the API and preserve the selected choice during refreshes.
- Public issue types are government-paid; household types are citizen-paid. The user cannot alter this rule in the form.
- Booking prices are fixed by issue type and shown as read-only in the interface. The backend is the source of truth.
- Payment success is not simulated by the browser. Razorpay sends its signed result to the backend for verification.
- If Google OAuth redirects with `?token=...`, the frontend stores the token and immediately removes it from the visible URL.

## Quick checks

```powershell
node --check .\frontend\app.js
node .\frontend\server.js
```
