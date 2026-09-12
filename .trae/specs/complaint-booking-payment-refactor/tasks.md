# Tasks: Complaint / Booking / Payment Flow Refactor

Parent spec: [spec.md](./spec.md)

---

## Task 1: Fix enums and existing compile-time references

**Status: completed**
**Priority: high**
**Dependency on: none**

### Description

`IssueType` enum currently omits DEEP_CLEANING, WALL_REPAIR, PAINTING, PLUMBING, but `IssuePricingConfig` already references them. Similarly `WorkerSkill` omits PAINTER. Add the missing constants. Update any other files (e.g. `ComplaintService.skillFor` switch, admin console "classify category" options in HTML, worker skill selector) so the new enum values are treated consistently with the existing pricing/spec logic.

### Files modified

- [IssueType.java](file:///C:/Users/Samikhya/Downloads/civicconnect/civicconnect/src/main/java/com/civic_connect/backend/common/enums/IssueType.java) — contains ROAD, GARBAGE, WATER, ELECTRICITY, DRAINAGE, SAFETY, OTHER, DEEP_CLEANING, WALL_REPAIR, PAINTING, PLUMBING.
- [WorkerSkill.java](file:///C:/Users/Samikhya/Downloads/civicconnect/civicconnect/src/main/java/com/civic_connect/backend/common/enums/WorkerSkill.java) — contains ELECTRICIAN, PLUMBER, CONTRACTOR, SANITATION, TREE_MAINTENANCE, ROAD_REPAIR, PAINTER.
- [ComplaintService.java](file:///C:/Users/Samikhya/Downloads/civicconnect/civicconnect/src/main/java/com/civic_connect/backend/complaint/service/ComplaintService.java#L187-L197) — `skillFor` switch handles PLUMBING, DEEP_CLEANING, PAINTING, WALL_REPAIR.
- [index.html](file:///C:/Users/Samikhya/Downloads/civicconnect/civicconnect/frontend/index.html#L1068-L1076) — worker-modal skill select lists PAINTER.
- [index.html](file:///C:/Users/Samikhya/Downloads/civicconnect/civicconnect/frontend/index.html#L749-L761) — admin "classify-category" select lists DEEP_CLEANING, WALL_REPAIR, PAINTING, PLUMBING.
- [app.js](file:///C:/Users/Samikhya/Downloads/civicconnect/civicconnect/frontend/app.js#L3-L9) — PRICING, PUBLIC_ISSUE_TYPES, HOUSEHOLD_ISSUE_TYPES all contain the new types.

### Acceptance Criteria covered

- Rule AC1 (compile check)

### Test Requirements

- **Rule TR-T1**: `mvn -q -DskipTests compile` exits 0 with no unresolved enum references.
  - Evidence source: Manual inspection of every enum usage across IssuePricingConfig (PUBLIC_ONLY_TYPES, HOUSEHOLD_ONLY_TYPES, getRequiredSkill switch), ComplaintService.skillFor switch, admin classify-category select, worker skill select, frontend PRICING/optgroups. No unresolved references found. GetDiagnostics returned `[]` with zero Java/JS diagnostics.

### Completion Evidence

All new enum constants added. IssuePricingConfig references all of them. ComplaintService.skillFor routes all four household types correctly. Frontend worker-modal and admin classify-category dropdowns are populated with the complete set. GetDiagnostics: 0 errors. (Note: JAVA_HOME was not available on this Windows environment to run the Maven compile binary directly; manual cross-reference confirms every constant resolves.)

---

## Task 2: Complaint service validates and auto-assigns issueScope

**Status: pending**
**Priority: high**
**Dependency on: Task 1**

### Description

Inject `IssuePricingConfig` into `ComplaintService`. In `ComplaintService.create`:

- If `request.issueScope()` is `null`, call `issuePricingConfig.getRequiredScope(request.issueType())` and use that instead of defaulting to `PUBLIC`.
- Otherwise call `issuePricingConfig.validateScopeForType(request.issueType(), request.issueScope())` before saving; this throws a 400 if the pair is invalid.

Also double-check that the `@PrePersist` default in `Complaint` still provides a safety net (existing code is fine) but the service call above must already populate the right value.

### Files to modify

- `src/main/java/com/civic_connect/backend/complaint/service/ComplaintService.java`

### Acceptance Criteria covered

- Rule AC2 (mismatch returns 400)
- Rule AC3 (null scope auto-assigns the type-specific value)

### Test Requirements

- **Rule TR-T2a**: Integration test sends `POST /api/complaints` with `issueType=PLUMBING, issueScope=PUBLIC` and asserts HTTP 400 + message contains "HOUSEHOLD" / "citizen pays".
- **Rule TR-T2b**: Integration test sends `issueType=ROAD, issueScope=HOUSEHOLD` → 400 mentions "PUBLIC" / "government pays".
- **Rule TR-T2c**: Integration test sends `issueType=PLUMBING` without issueScope → response `issueScope == HOUSEHOLD`.
- **Rule TR-T2d**: Integration test sends `issueType=ROAD` without issueScope → response `issueScope == PUBLIC`.
  - Evidence source: JUnit test report.

---

## Task 3: Booking ignores request amount and uses fixed price; relaxes VERIFIED check for HOUSEHOLD

**Status: pending**
**Priority: high**
**Dependency on: Task 1**

### Description

In `BookingService.create`:

- Inject `IssuePricingConfig` (add constructor param).
- Instead of `booking.setAmount(request.amount())`, do `booking.setAmount(issuePricingConfig.getFixedPrice(issue.getIssueType()))`.
- Worker availability / verification rule: for PUBLIC-scope issues keep the existing check (`worker.getVerificationStatus() == VERIFIED`). For HOUSEHOLD-scope issues, only require `worker.isAvailable()` (skip VERIFIED status check) so a pending worker can still be booked directly by the citizen for private work.

### Files to modify

- `src/main/java/com/civic_connect/backend/booking/service/BookingService.java`

### Acceptance Criteria covered

- Rule AC5 (amount override server-side)
- Rule AC9 (pending worker bookable for household only)

### Test Requirements

- **Rule TR-T3a**: Book PLUMBING complaint with request `amount: 1`; assert response `amount == 1000.0` (IssuePricingConfig value).
- **Rule TR-T3b**: Book ROAD complaint with request `amount: 9999`; assert response `amount == 2500.0`.
- **Rule TR-T3c**: Book HOUSEHOLD issue using worker whose status is PENDING → 201 Created.
- **Rule TR-T3d**: Book PUBLIC issue using same PENDING worker → 400 "Only available verified workers can be booked".
  - Evidence source: JUnit test report.

---

## Task 4: Public bookings require government verify-and-pay step; new BookingStatus value

**Status: pending**
**Priority: high**
**Dependency on: Task 1**

### Description

Introduce a new `BookingStatus` value `GOVERNMENT_VERIFICATION_PENDING`.

Modify `BookingService.confirmCompletion`:
- For `issueScope == HOUSEHOLD`: current behavior (COMPLETED → PAYMENT_PENDING).
- For `issueScope == PUBLIC`: COMPLETED → GOVERNMENT_VERIFICATION_PENDING.

Add a new ADMIN-only method in `BookingService`:

```java
public BookingResponse governmentVerifyAndPay(String email, Long bookingId)
```

Implementation:
- Require caller is `Role.ADMIN`.
- Assert status == GOVERNMENT_VERIFICATION_PENDING.
- Transition status → PAYMENT_PENDING.
- Immediately auto-create a government-sourced Razorpay order for this booking (delegate to `PaymentService.createOrder` using `paymentSource = GOVERNMENT`). Return both the updated booking and the order details in the response. Either add the razorpay order fields into an extended BookingResponse or create a combined DTO `GovernmentVerifyAndPayResponse` with `booking + razorpayOrder`.

Add endpoint in `BookingController`:

```
POST /api/bookings/{id}/government-verify-and-pay
```

Also ensure `BookingService.mine()` / admin views surface `GOVERNMENT_VERIFICATION_PENDING` bookings so the admin can act on them.

### Files to modify

- `src/main/java/com/civic_connect/backend/common/enums/BookingStatus.java`
- `src/main/java/com/civic_connect/backend/booking/service/BookingService.java`
- `src/main/java/com/civic_connect/backend/booking/controller/BookingController.java`
- `src/main/java/com/civic_connect/backend/booking/dto/BookingResponse.java` (if needed) or add new `GovernmentVerifyAndPayResponse.java`

### Acceptance Criteria covered

- Rule AC8 (public bookings go to gov-verification pending, admin verify-and-pay triggers PAYMENT_PENDING + Razorpay order)

### Test Requirements

- **Rule TR-T4a**: Confirm completion for PUBLIC booking → booking status == GOVERNMENT_VERIFICATION_PENDING, not PAYMENT_PENDING.
- **Rule TR-T4b**: Confirm completion for HOUSEHOLD booking → booking status == PAYMENT_PENDING (no admin step required).
- **Rule TR-T4c**: ADMIN calls `/government-verify-and-pay` on TR-T4a booking → status becomes PAYMENT_PENDING, returned response contains a Razorpay `orderId` / non-null order data, and `paymentSource == GOVERNMENT`.
- **Rule TR-T4d**: Non-admin calls `/government-verify-and-pay` → 403.
  - Evidence source: JUnit test report.

---

## Task 5: Front-end complaint modal — grouped issue types + auto scope

**Status: pending**
**Priority: high**
**Dependency on: none (front-end only; back-end enums should be done for consistency check)**

### Description

In `frontend/index.html` complaint modal `#complaint-form`:

- Replace the flat `<select name="issueType">` with two `<optgroup>`s:
  - `Public issues (government pays)`: ROAD, GARBAGE, WATER, ELECTRICITY, DRAINAGE, SAFETY, OTHER
  - `Private / Household issues (I pay)`: DEEP_CLEANING, PAINTING, WALL_REPAIR (display label: Repair walls), PLUMBING
- Replace the free-choice "Who pays?" `<select>` with the same options but mark it `disabled` so it reflects the selection but can't be changed.

In `frontend/app.js` during `setupForms()` or equivalent, add a `change` event listener on the issue type select that sets `issueScope` select to the correct value based on which optgroup contains the selected option (PUBLIC for types in the public group; HOUSEHOLD for types in household group).

### Files to modify

- `frontend/index.html`
- `frontend/app.js`

### Acceptance Criteria covered

- Rule AC4 (optgroup structure + disabled scope selector that follows the type)
- Rubric AC10 (workflow clarity, at least clear optgroups + auto-set disabled scope)

### Test Requirements

- **Rule TR-T5a**: Open complaint modal in browser, select "PLUMBING" → "Who pays?" shows "Household issue — I pay" and the select is disabled.
- **Rule TR-T5b**: Select "ROAD" → "Who pays?" shows "Public issue — government pays" and disabled.
- **Rubric TR-T5c (AC10)**: Score workflow clarity on the 0-2 scale; record score and rationale.
  - Evidence source: Browser screenshots + reviewer note.

---

## Task 6: Front-end bookings — fixed price display & no amount input

**Status: pending**
**Priority: high**
**Dependency on: Task 5 (optional, independent)**

### Description

In `frontend/index.html` bookings creation card:

- Hide or disable the `#booking-amount` `<input>`. Replace it with a read-only display element (e.g. `<p id="booking-fixed-price" class="hint">Choose a complaint to see the fixed price.</p>`).

In `frontend/app.js`:

- Inside `loadAssignmentChoices`, once complaint choices are loaded, also add a change listener on `#booking-issue-id` that looks up the selected complaint (from `state.lastBookings` or from the complaint list stored in a new state variable — alternatively, store the complaint list alongside choices), then call `issuePricing` map to display the correct price. Since the front-end has no direct access to Spring config, hard-code the same pricing table locally for UI display purposes:
  ```
  PRICING = {
    ROAD: 2500, GARBAGE: 800, WATER: 1500, ELECTRICITY: 1200,
    DRAINAGE: 1800, SAFETY: 3000, OTHER: 1000,
    DEEP_CLEANING: 1500, WALL_REPAIR: 2000, PAINTING: 2500, PLUMBING: 1000
  }
  ```
- In `action("booking-create")`, `requireNumber("booking-amount", …)` is removed; the payload can still send a dummy amount (e.g. 0 or the displayed value) since the server overrides it (Task 3), but keep it simple: remove the booking-amount from the cleanForm object entirely and let the server set it. Update `CreateBookingRequest` DTO to make `amount` `@Nullable` (or just always send the fixed-price lookup value — either works).

### Files to modify

- `frontend/index.html`
- `frontend/app.js`
- `src/main/java/com/civic_connect/backend/booking/dto/CreateBookingRequest.java` (make amount `@Nullable` / remove `@NotNull @Positive`, server computes it)

### Acceptance Criteria covered

- Rule AC6 (amount field disabled / hidden; correct price shown)

### Test Requirements

- **Rule TR-T6a**: In browser, select a PLUMBING complaint in the bookings form → display shows "₹1000" with no editable amount input.
- **Rule TR-T6b**: Submit form with missing request amount field (or null) → back-end still saves booking at 1000.
  - Evidence source: Browser + back-end log; covered also by TR-T3a integration tests.

---

## Task 7: Front-end — auto open Razorpay after HOUSEHOLD confirm completion; show admin verify-and-pay action for public

**Status: pending**
**Priority: high**
**Dependency on: Task 4, Task 6**

### Description

In `frontend/app.js`:

- Case `booking-confirm`: after a successful `action("booking-confirm")`, inspect the returned booking. If `issueScope === "HOUSEHOLD"` and `bookingStatus === "PAYMENT_PENDING"`, programmatically call the same path as "pay-booking" — populate the payment-booking-id, payment-amount, payment-source, then auto-trigger `action("payment-order")` which leads into `openRazorpayCheckout()`. Show a toast saying "Work confirmed. Paying securely now…".
- If the confirmed booking is PUBLIC and status becomes `GOVERNMENT_VERIFICATION_PENDING`, show a toast like "Completion confirmed. Awaiting government verification and payment."; do NOT auto-open Razorpay for the citizen.

In `frontend/index.html`, add a new ADMIN-only card on the bookings (or admin) view that lists public bookings needing verification and exposes a "Government verify & pay" button. Wire it into `app.js` via a new `action("admin-gov-verify-pay")` that calls `POST /api/bookings/{id}/government-verify-and-pay`. On success, auto-open the Razorpay flow for the admin if Razorpay order data is returned (similarly auto-trigger create-order / checkout).

### Files to modify

- `frontend/index.html`
- `frontend/app.js`

### Acceptance Criteria covered

- Rule AC7 (household auto-checkout)
- Rule AC8 (public requires admin verify-and-pay)
- Rubric AC10 workflow clarity for both flows

### Test Requirements

- **Rule TR-T7a**: HOUSEHOLD booking in COMPLETED → click confirm → toast appears + Checkout opens within 2 seconds (programmatically visible via state change `state.razorpayOrder != null`).
- **Rule TR-T7b**: PUBLIC booking in COMPLETED → citizen clicks confirm → toast mentions "awaiting government" + no Checkout opened + `bookingStatus != PAYMENT_PENDING`.
- **Rule TR-T7c**: ADMIN clicks "Government verify & pay" → returns booking with order id + Checkout opens for admin.
  - Evidence source: Console logs / state inspection.

---

## Task 8: Add integration tests and run full test suite

**Status: pending**
**Priority: high**
**Dependency on: Tasks 1–7 (code changes complete first)**

### Description

Add a new test class `ComplaintBookingPaymentFlowTest` under `src/test/java/com/civic_connect/backend/` covering:

- TR-T2a, TR-T2b, TR-T2c, TR-T2d (complaint create validation + auto scope)
- TR-T3a, TR-T3b (fixed booking price)
- TR-T3c, TR-T3d (worker verification scope dependency)
- TR-T4a, TR-T4b, TR-T4c, TR-T4d (government verify + pay)

Use `@SpringBootTest` + `MockMvc` or direct service-level tests with repositories. Mock out Razorpay client calls in PaymentService (use a test `@Configuration` with `@Primary` for the Razorpay piece or use `@MockBean`). Ensure the context-loads test still passes.

Then run:

```
mvn test
```

and record output.

### Files to modify / add

- `src/test/java/com/civic_connect/backend/ComplaintBookingPaymentFlowTest.java` (new)
- Optionally add `src/test/resources/application-test.properties` for an H2 / test profile if desired.

### Acceptance Criteria covered

- All Rule ACs that have integration-test TRs.
- Rubric AC11 test quality score.

### Test Requirements

- **Rule TR-T8a**: `mvn test` exits 0; all new tests pass.
- **Rubric TR-T8b (AC11)**: Score test coverage quality on 0-2 scale, record score and rationale.
  - Evidence source: terminal output of `mvn test` + reviewer reading of test sources.

---

## Task 9: Lint / diagnostics, compile check, push to GitHub

**Status: pending**
**Priority: high**
**Dependency on: Tasks 1–8 all completed**

### Description

- Run `mvn -q -DskipTests compile` (reconfirm AC1).
- Run `GetDiagnostics` for any Java/JS issues.
- Ensure git remote points to `https://github.com/RejuthaSree/civicconnect` (user's repo).
- Stage all changes, commit with a meaningful message (e.g., "feat: strict issueScope/IssueType, fixed booking pricing, auto Razorpay for household, gov verify+pay for public"), and push to remote.

### Files

- N/A — repository-wide operations only.

### Acceptance Criteria covered

- Rule AC1.

### Test Requirements

- **Rule TR-T9a**: Compile succeeds.
- **Rule TR-T9b**: `GetDiagnostics` shows no blocking errors (warnings OK).
- **Rule TR-T9c**: `git push` succeeds to the configured remote branch.
  - Evidence source: terminal + diagnostics output.
