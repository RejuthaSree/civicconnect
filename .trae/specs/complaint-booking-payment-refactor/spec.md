# Spec: Complaint / Booking / Payment Flow Refactor

## Problem

The current CivicConnect system has several critical mismatches and UX gaps in the complaint → booking → payment flow:

1. **Enum mismatch**: `IssueType` and `WorkerSkill` enums are missing values that `IssuePricingConfig` already references (DEEP_CLEANING, WALL_REPAIR, PAINTING, PLUMBING in IssueType; PAINTER in WorkerSkill). This will fail at runtime / compile time.
2. **No scope-for-type enforcement**: A citizen can select issue type "PLUMBING" (household) with scope "PUBLIC" (government pays), or "ROAD" (public) with scope "HOUSEHOLD" (citizen pays). No validation prevents this contradiction.
3. **Drop-down confusion in add-complaint form**: The front-end "Issue type" drop-down lists only the public civic categories. The private work types (deep cleaning, painting, wall repair, plumbing) are missing. The "Who pays?" selector is free-choice, so citizens can wrongly assign a household job to the government or vice versa.
4. **Manual booking amount**: The citizen types the booking ₹ amount into a free-text input. Every worker instead has a fixed payment per work complexity based on the issue type.
5. **Payment timing for household (private) work**: The current booking flow waits for citizen "confirm completion" → "PAYMENT_PENDING" → manual "create order" → Razorpay. For household work paid by the citizen, the Razorpay Checkout screen should surface immediately after the citizen confirms completion so they pay right then, not through an extra manual navigation step.
6. **Government payments for public work**: For public-scope issues (government pays), the workflow requires the admin/government to verify the completed public work before releasing payment. Currently the payment source is validated, but there is no explicit admin "government verify + pay immediately" step for public work, and workers should not depend on admin verification for their private (household) citizen payments.

## Users

- **CITIZEN**: reports household or public issues, books verified workers for household work, confirms completion, pays for household work via Razorpay.
- **WORKER**: accepts bookings, completes work, receives payment without additional admin verification for household citizen-paid jobs; public-work payment still waits on government verification.
- **ADMIN / GOVERNMENT**: verifies completed public work, pays workers through the government payment flow for verified public issues.

## Goals

- Prevent invalid (issue-type, issue-scope) combinations: private types → citizen pays; public types → government pays.
- Make the add-complaint drop-down clearly list the two groups (public issues & private issues) with exact required types: deep cleaning, painting, repair walls, plumbing as household; existing civic types as public.
- Remove the citizen-entered booking amount; compute a fixed, complexity-based price from the issue type and show it read-only before the citizen confirms the booking.
- For HOUSEHOLD bookings: after citizen "confirm completion", immediately open the Razorpay Checkout with the already-fixed amount.
- For PUBLIC bookings: require an explicit admin government-verification step that marks the work verified and triggers immediate government-sourced payment through Razorpay; no admin verification gates the worker payment for HOUSEHOLD citizen-funded jobs.
- Keep the system consistent end-to-end: every backend validation is reflected in the front-end UX so users are never able to submit an invalid pairing.

## Non-goals

- Changing the Google OAuth login, JWT scheme, worker onboarding verification, AI classifier, duplicate detection, SLA engine, or Leaflet government map.
- Adding a new database of custom per-worker pricing; pricing remains per (IssueType) in `IssuePricingConfig`.
- Integrating a new payment gateway; Razorpay is the only provider.
- Rewriting the assignment (non-booking) flow; this scope is the bookings payment path.

## Functional Requirements

- FR1: `IssueType` enum SHALL include: ROAD, GARBAGE, WATER, ELECTRICITY, DRAINAGE, SAFETY, OTHER, DEEP_CLEANING, WALL_REPAIR, PAINTING, PLUMBING.
- FR2: `WorkerSkill` enum SHALL include at minimum: ELECTRICIAN, PLUMBER, CONTRACTOR, SANITATION, TREE_MAINTENANCE, ROAD_REPAIR, PAINTER.
- FR3: When a complaint is created, the back-end SHALL validate that `issueScope` matches the required scope for the chosen `issueType` using `IssuePricingConfig.validateScopeForType` and reject mismatches with a clear 400 message.
- FR4: If a citizen submits a complaint without an explicit `issueScope`, the back-end SHALL auto-assign the scope derived from the issue type (instead of defaulting to PUBLIC).
- FR5: On the "New complaint" modal, the issue type `<select>` SHALL group options into labelled groups: "Public issues (government pays)" and "Private / Household issues (I pay)"; household options SHALL be exactly: DEEP_CLEANING, PAINTING, WALL_REPAIR (display label: Repair walls), PLUMBING.
- FR6: On the "New complaint" modal, the "Who pays?" `<select>` SHALL be read-only / disabled and auto-populated with the correct scope whenever the issue type changes.
- FR7: `CreateBookingRequest.amount` SHALL no longer be accepted as authoritative. The booking service SHALL ignore any provided amount and instead set `booking.amount = IssuePricingConfig.getFixedPrice(issue.issueType)`. The DTO amount field may be kept for API compatibility but its value is overridden server-side.
- FR8: The front-end "Create booking" form SHALL hide / disable the free-text amount input and instead display a read-only "Fixed price (₹)" value that is computed from the selected complaint's issue type before submission.
- FR9: For HOUSEHOLD-scope bookings, after the citizen clicks "Confirm completion" (endpoint `/api/bookings/{id}/confirm-completion`), the UI SHALL immediately transition the booking to PAYMENT_PENDING server-side and then auto-initiate the Razorpay order creation + Checkout flow in the same click without requiring the citizen to separately navigate to Payments and pick "Pay securely". On success the booking becomes PAYMENT_COMPLETED.
- FR10: For PUBLIC-scope bookings:
  - Worker completion + citizen confirmation SHALL move the booking into a new `GOVERNMENT_VERIFICATION_PENDING` status (or remain in COMPLETED with a verification flag) instead of directly to PAYMENT_PENDING.
  - An explicit ADMIN-only endpoint SHALL exist (`POST /api/bookings/{id}/government-verify-and-pay`) that (a) verifies the completed public work, (b) transitions the booking to PAYMENT_PENDING, and (c) immediately creates the government-sourced Razorpay order (or marks it paid if Razorpay is simulated). Payment is "verified and paid immediately" per this admin action.
  - No additional admin verification is required for HOUSEHOLD citizen-paid bookings. The worker gets their payment as soon as the citizen's Razorpay payment succeeds without any admin step.
- FR11: When booking a worker for a HOUSEHOLD (citizen-paid) job, the back-end SHALL NOT require the worker to have admin verification status if the booking creator chooses them; public-scope bookings SHALL still book only VERIFIED workers (current behavior preserved for civic work).

## Non-functional Requirements

- NFR1: All changes are backward compatible at the data-layer for existing complaints / bookings / payments; if a new enum constant is introduced existing records remain readable.
- NFR2: Fixed price is always determined server-side; client-side display is informational only and never accepted without server recomputation.
- NFR3: Front-end changes are confined to `index.html`, `app.js`, and `styles.css` (if any CSS tweaks are needed); no new front-end build tooling is introduced.
- NFR4: Tests exist that exercise: scope-for-type enforcement, booking price override (ignores request amount), household auto-checkout, and government verify-and-pay behavior.

## Constraints, Dependencies, Assumptions

- Spring Boot 4.1 / Java 21 / PostgreSQL stack unchanged.
- Razorpay Checkout script is already loaded in `index.html`.
- For unit/integration tests, the Razorpay client interaction is either stubbed or tested via the service layer without calling the live gateway.
- A CITIZEN creating a booking always reports the underlying issue themselves (already enforced in `BookingService.create`).

## Open Questions

- None — the required issue types and labels are explicit in the user request: public issues plus private (household) work = deep cleaning, painting, repair walls, plumbing.

## Acceptance Criteria

### Rule AC1
- **Observable pass condition**: Back-end compiles without enum-mismatch errors and `IssuePricingConfig.PUBLIC_ONLY_TYPES`, `HOUSEHOLD_ONLY_TYPES`, and `getRequiredSkill` all resolve against real enum values.
- **Evidence source**: Running `mvn -q -DskipTests compile` exits with code 0.

### Rule AC2
- **Observable pass condition**: `POST /api/complaints` with issueType=PLUMBING + issueScope=PUBLIC returns HTTP 400 whose body message says the issue is HOUSEHOLD and must be citizen-paid. Symmetrically, ROAD + issueScope=HOUSEHOLD returns 400 saying it is PUBLIC / government pays.
- **Evidence source**: Integration test for ComplaintController + curl/Postman reproduction script command (see tests).

### Rule AC3
- **Observable pass condition**: `POST /api/complaints` with issueType=PLUMBING and no issueScope auto-assigns HOUSEHOLD; ROAD with no issueScope auto-assigns PUBLIC (matches the type's required scope, not a hard-coded default).
- **Evidence source**: Integration test asserting returned `issueScope`.

### Rule AC4
- **Observable pass condition**: New-complaint modal issue type select shows optgroups labelled "Public issues (government pays)" and "Private / Household issues (I pay)"; household options include exactly DEEP_CLEANING, PAINTING, WALL_REPAIR, PLUMBING. When the user picks PLUMBING the "Who pays?" select automatically switches to "Household issue — I pay" and is disabled / read-only.
- **Evidence source**: Browser DOM inspection after rendering complaint modal.

### Rule AC5
- **Observable pass condition**: `POST /api/bookings` with `amount=1.00` on a PLUMBING (household) complaint stores the booking with `amount = 1000.0` (matching `IssuePricingConfig.getFixedPrice(PLUMBING)`) and ignores the request amount. Same behavior for every issue type.
- **Evidence source**: Integration test calling the endpoint and asserting the returned Booking amount.

### Rule AC6
- **Observable pass condition**: Front-end "Create booking" panel either does not render an amount `<input>` or renders it as `disabled` / `readonly` and populates the correct price when the citizen chooses a complaint. The "Create booking" button submits without reading any amount value from the DOM (or sends any number, since AC5 overrides it server-side).
- **Evidence source**: DOM inspection of the bookings form.

### Rule AC7
- **Observable pass condition**: For a HOUSEHOLD booking in COMPLETED status, clicking "Confirm completion" in the UI transitions the booking to PAYMENT_PENDING and immediately triggers the same `openRazorpayCheckout()` flow (creates order, opens Checkout) without the citizen navigating to the Payments view and clicking "Pay securely".
- **Evidence source**: Front-end instrumentation (console log / mock) that the checkout is opened inside the `booking-confirm` success handler; integration-level end-to-end trace of statuses.

### Rule AC8
- **Observable pass condition**: For a PUBLIC booking in COMPLETED status, citizen confirm-completion does NOT move it to PAYMENT_PENDING; instead it moves to GOVERNMENT_VERIFICATION_PENDING (or equivalent tracked state). Only an ADMIN call to `/api/bookings/{id}/government-verify-and-pay` transitions to PAYMENT_PENDING + immediately produces a Razorpay order sourced from GOVERNMENT. After Razorpay verify succeeds, worker earnings are incremented and booking is PAYMENT_COMPLETED. HOUSEHOLD bookings never block on this admin step and proceed directly to PAYMENT_PENDING after citizen confirmation.
- **Evidence source**: Integration test exercising citizen confirm + admin verify-and-pay for PUBLIC, and confirming HOUSEHOLD skips the admin gate.

### Rule AC9
- **Observable pass condition**: A worker with `verificationStatus = PENDING` can be booked successfully against a HOUSEHOLD-scope complaint by the citizen, while the same worker cannot be booked for a PUBLIC-scope complaint (only VERIFIED workers allowed).
- **Evidence source**: Integration test in booking service.

### Rubric AC10 — Workflow clarity (0-2, pass ≥ 1.5)
- Dimension: How clearly the front-end UI communicates who pays and the fixed price at complaint-creation and booking-creation time.
- Anchors: 0 = still allows free-choice scope / manual amount; 1 = groups are visible but not auto-filled / amount still editable; 2 = optgroups visible, scope auto-set and disabled, amount display is read-only and correct.
- Threshold: ≥ 1.5 (clear, minimal user confusion possible).
- Evidence source: Reviewer walkthrough of the complaint and booking modals with screenshots.

### Rubric AC11 — Test coverage quality (0-2, pass ≥ 1.5)
- Dimension: Quality and coverage of automated tests for the rules above.
- Anchors: 0 = no tests added; 1 = tests cover one or two ACs but miss the main edge cases; 2 = tests cover AC2, AC3, AC5, AC8, AC9 with clear assertions and readable setup.
- Threshold: ≥ 1.5.
- Evidence source: Test run output (mvn test) + review of test sources.
