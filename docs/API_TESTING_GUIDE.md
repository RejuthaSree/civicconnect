# CivicConnect API Testing Guide

Base URL: `http://localhost:8080`  
API authentication header: `Authorization: Bearer {{jwt}}`  
JSON header, whenever a body is sent: `Content-Type: application/json`

## Security and Postman setup

`SecurityConfig` permits only `/`, `/oauth2/**`, `/login/**`, `/api/test`, and static content without authentication. Every endpoint documented below is therefore authenticated and needs a JWT. The project obtains its JWT through the existing Google OAuth login success flow. No API controller uses `@PreAuthorize`; authorization is enforced inside services.

Create these Postman variables: `baseUrl`, `jwtCitizen`, `jwtWorker`, `jwtAdmin`, `complaintId`, `workerId`, `assignmentId`, `bookingId`, `paymentOrderId`.

### Common error response

Application business errors use the form below. Validation errors are Spring validation responses and may vary slightly by Spring Boot version.

```json
{ "message": "Complaint not found" }
```

| HTTP status | Typical reason |
|---|---|
| 400 | Invalid lifecycle transition, invalid enum/value, missing business prerequisite |
| 401 | Missing/invalid JWT, or user not found for the authenticated email |
| 403 | JWT user lacks the required role or does not own the resource |
| 404 | Referenced complaint, worker, assignment, booking, notification, or payment does not exist |
| 409 | Duplicate assignment, payment, or review |
| 422/400 | Bean validation failure such as blank required data or invalid rating |
| 502 | Razorpay order creation failed |
| 503 | Razorpay credentials are not configured |

## OAuth / Login

| Endpoint name | Method and full URL | Headers / request / validation | Success response | Auth / authorization / JWT / OAuth | Testing order and common errors |
|---|---|---|---|---|---|
| Start Google login | GET `{{baseUrl}}/oauth2/authorization/google` | No headers or JSON body. | Browser redirects to Google. After successful login the configured success handler redirects to the frontend with `?token=<JWT>`. | Auth: No. Role: none. JWT: No. OAuth: this **is** the OAuth entry point. | 1. Open in a browser (not raw Postman). 2. Sign in with Google. 3. Copy the returned token to the matching Postman variable. Errors: OAuth client configuration or Google consent failure. |
| Current user | GET `{{baseUrl}}/api/users/me` | `Authorization: Bearer {{jwtCitizen}}`; no query/body. | `{"username":"Asha","id":1,"provider":"GOOGLE","email":"asha@example.com"}` | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No per request; token originates from OAuth. | Run immediately after login. 401 for missing/invalid token; current code can return a server error if the JWT email has no stored user. |

## ComplaintController — `/api/complaints`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Create complaint | POST `{{baseUrl}}/api/complaints` | Headers: auth + JSON. Query/path: none. Body: `title`, `description`, `address`, `area`, `city`, `latitude`, `longitude`, optional `imageUrl`, `issueType`, optional `priority`. | Request: `{"title":"Broken streetlight","description":"Light pole is dark at night","address":"12 MG Road","area":"Central Ward","city":"Pune","latitude":18.5204,"longitude":73.8567,"imageUrl":"https://cdn.example.com/light.jpg","issueType":"ELECTRICITY","priority":"HIGH"}`. Response (201): `{"id":101,"title":"Broken streetlight","description":"Light pole is dark at night","address":"12 MG Road","area":"Central Ward","city":"Pune","latitude":18.5204,"longitude":73.8567,"imageUrl":"https://cdn.example.com/light.jpg","status":"REPORTED","priority":"HIGH","issueType":"ELECTRICITY","reportedAt":"2026-08-23T10:00:00Z","resolvedAt":null,"upvotes":0,"aiClassification":null,"reporterId":1,"assignedWorkerId":null}` | Auth: Yes. Role: `CITIZEN`. JWT: Yes. OAuth: No. | `title`, `description`, `address`, `area`, `city` must be nonblank; coordinates and `issueType` are required; enum values must be valid. Run after citizen login; save `id` as `complaintId`. Errors: 400 validation, 403 non-citizen. |
| List complaints | GET `{{baseUrl}}/api/complaints?status=REPORTED&page=0&size=20&sort=reportedAt,desc` | Auth header. Query: optional `status` (`REPORTED`, `UNDER_REVIEW`, `ASSIGNED`, `WORK_ACCEPTED`, `IN_PROGRESS`, `WORK_COMPLETED`, `CITIZEN_VERIFICATION`, `PAYMENT_APPROVED`, `RESOLVED`, `REJECTED`), Spring pagination `page`, `size`, `sort`. No body/path. | Response (200): `{"content":[{"id":101,"title":"Broken streetlight","status":"REPORTED","priority":"HIGH","issueType":"ELECTRICITY","upvotes":0,"reporterId":1,"assignedWorkerId":null}],"totalElements":1,"totalPages":1,"size":20,"number":0}` | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | Run after login or complaint creation. Invalid status/page parameters return 400. |
| My complaints | GET `{{baseUrl}}/api/complaints/mine?page=0&size=20` | Auth header. Query: Spring pagination. No body/path. | Response: same paged `ComplaintResponse` structure as list. | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | Run with citizen token after creating a complaint. 401 invalid JWT. |
| Vote complaint | POST `{{baseUrl}}/api/complaints/{{complaintId}}/vote` | Auth header; path `id`; no body/query. | Response: complaint response with incremented `upvotes`, e.g. `{"id":101,"upvotes":1,"status":"REPORTED"}` plus all complaint fields. | Auth: Yes. Role: `CITIZEN`. JWT: Yes. OAuth: No. | Run after create/list. The implementation does not prevent the same citizen voting repeatedly. Errors: 403 role, 404 complaint. |
| Verify complaint | POST `{{baseUrl}}/api/complaints/{{complaintId}}/verify` | Auth header; path `id`; no body/query. | Response: complaint response. | Auth: Yes. Role: reporting citizen (ownership); JWT: Yes. OAuth: No. | Only works when complaint status is `RESOLVED`. Errors: 403 non-owner, 400 not resolved, 404 missing complaint. |

## WorkerController — `/api/workers`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Register worker | POST `{{baseUrl}}/api/workers/register` | Auth + JSON. Body: `skill`, `serviceArea`, optional location/profile/verification fields. | Request: `{"skill":"ELECTRICIAN","serviceArea":"Central Ward","latitude":18.5204,"longitude":73.8567,"workRadiusKm":8,"city":"Pune","phoneNumber":"9876543210","address":"Pune","profilePhotoUrl":"https://cdn.example.com/profile.jpg","aadhaarMasked":"XXXX-XXXX-1234","governmentIdReference":"https://cdn.example.com/id.pdf","certificates":"Electrical licence","experienceYears":5}`. Response: `{"id":21,"userId":2,"name":"Ravi","skill":"ELECTRICIAN","serviceArea":"Central Ward","city":"Pune","profilePhotoUrl":"https://cdn.example.com/profile.jpg","experienceYears":5,"certificates":"Electrical licence","latitude":18.5204,"longitude":73.8567,"workRadiusKm":8,"available":true,"verificationStatus":"PENDING","completedTasks":0,"rating":0.0,"totalReviews":0,"totalEarnings":0.0,"totalGovernmentPaymentsReceived":0.0}` | Auth: Yes. Role: any non-`ADMIN` authenticated user; service changes the user to `WORKER`. JWT: Yes. OAuth: No. | `skill` and `serviceArea` required; worker remains `PENDING`. Run after worker login; save `id` as `workerId`. 403 if admin. |
| My worker profile | GET `{{baseUrl}}/api/workers/me` | Auth header only. | Response: `WorkerResponse` shown above. | Auth: Yes. Role: user with worker profile. JWT: Yes. OAuth: No. | Run after registration. 404 if no profile. |
| Browse verified workers | GET `{{baseUrl}}/api/workers?skill=ELECTRICIAN&page=0&size=20` | Auth header. Query: optional `skill`; pagination parameters. | Response: paged `WorkerResponse`: `{"content":[{"id":21,"name":"Ravi","skill":"ELECTRICIAN","verificationStatus":"VERIFIED","available":true}],"totalElements":1,"number":0}` | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | Only available `VERIFIED` workers are returned. Run after admin approval. Invalid enum/page: 400. |
| Worker public profile | GET `{{baseUrl}}/api/workers/{{workerId}}` | Auth header; path `id`; no body/query. | Response: `WorkerResponse`. | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | Only `VERIFIED` workers are returned. 404 for missing/unverified worker. |

## WorkerPortfolioController — `/api/workers`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Worker portfolio | GET `{{baseUrl}}/api/workers/{{workerId}}/portfolio` | Auth header; path `workerId`; no body/query. | Response: `[ {"assignmentId":51,"complaintName":"Broken streetlight","area":"Central Ward","beforeImageUrl":"https://cdn.example.com/before.jpg","afterImageUrl":"https://cdn.example.com/after.jpg","completionDate":"2026-08-23T12:00:00Z","citizenRating":5,"citizenReview":"Great work"} ]` | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | Returns only assignments with `CompletionStatus.APPROVED`; empty list is valid. Run after assignment completion, citizen approval, and review. |

## AdminController — `/api/admin` (`ADMIN` represents government authority)

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Change complaint priority | PATCH `{{baseUrl}}/api/admin/complaints/{{complaintId}}/priority?priority=CRITICAL` | Admin auth header; path `id`; required enum query `priority`; no body. | Response: complaint response with `"priority":"CRITICAL"`. | Auth: Yes. Role: `ADMIN`. JWT: Yes. OAuth: No. | Run after complaint creation. 403 non-admin; 404 complaint; 400 invalid priority. |
| Approve/reject worker | POST `{{baseUrl}}/api/admin/workers/{{workerId}}/verification?approved=true&notes=Documents%20validated` | Admin auth header; path `id`; required boolean `approved`; optional `notes`; no body. | Response: `WorkerResponse` with `"verificationStatus":"VERIFIED"` if approved, or `"REJECTED"` and `available:false` if rejected. | Auth: Yes. Role: `ADMIN`. JWT: Yes. OAuth: No. | Run after worker registration. 403 non-admin; 404 worker; invalid boolean gives 400. |
| Set worker availability | POST `{{baseUrl}}/api/admin/workers/{{workerId}}/availability?available=true` | Admin auth header; path `id`; required boolean `available`; no body. | Response: `WorkerResponse` with requested availability. | Auth: Yes. Role: `ADMIN`. JWT: Yes. OAuth: No. | Run after approval if needed. 403 non-admin; 404 worker. |

## AssignmentController — `/api/assignments`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Government assign worker | POST `{{baseUrl}}/api/assignments?complaintId={{complaintId}}&workerId={{workerId}}` | Admin auth header. Required query IDs. No body/path. | Response: `{"id":51,"complaintId":101,"workerId":21,"assignedAt":"2026-08-23T10:00:00Z","acceptedAt":null,"completedAt":null,"remarks":null,"proofImageUrl":null,"beforeImageUrl":null,"afterImageUrl":null,"completionStatus":null,"citizenVerifiedAt":null}` | Auth: Yes. Role: `ADMIN`. JWT: Yes. OAuth: No. | Worker must be available and `VERIFIED`; complaint must be unassigned. Errors: 400 worker unavailable/unverified, 403 role, 404 IDs, 409 existing assignment. |
| Citizen request worker | POST `{{baseUrl}}/api/assignments/request?complaintId={{complaintId}}&workerId={{workerId}}` | Citizen auth header. Required query IDs. No body/path. | Response: `AssignmentResponse` as above. | Auth: Yes. Role: complaint-owning `CITIZEN`. JWT: Yes. OAuth: No. | Citizen must own complaint; worker must be available/verified; no existing assignment. Errors: 403 role/ownership, 400 worker, 404 IDs, 409 assigned. |
| Accept assignment | POST `{{baseUrl}}/api/assignments/{{assignmentId}}/accept` | Worker auth header; path `id`; no body/query. | Response: `AssignmentResponse` with `acceptedAt` set. | Auth: Yes. Role: assigned worker via ownership check. JWT: Yes. OAuth: No. | Run after either assignment endpoint. 403 other worker; 404 assignment. Repeated call is currently accepted/idempotent. |
| Complete assignment | POST `{{baseUrl}}/api/assignments/{{assignmentId}}/complete` | Worker auth + JSON; path `id`; body optional. | Request: `{"remarks":"Replaced damaged fixture","beforeImageUrl":"https://cdn.example.com/before.jpg","afterImageUrl":"https://cdn.example.com/after.jpg"}`. Response: `AssignmentResponse` with URLs, `completedAt`, and `completionStatus:"PENDING_CITIZEN_APPROVAL"`. | Auth: Yes. Role: assigned worker. JWT: Yes. OAuth: No. | Assignment must first be accepted. Errors: 400 not accepted, 403 wrong worker, 404 assignment. |
| Citizen verify assignment | POST `{{baseUrl}}/api/assignments/{{assignmentId}}/citizen-verification?approved=true` | Citizen auth header; path `id`; required boolean `approved`; no body. | Response: `AssignmentResponse` with `completionStatus:"APPROVED"` and `citizenVerifiedAt` when approved; `REJECTED` when false. | Auth: Yes. Role: reporting citizen / ownership. JWT: Yes. OAuth: No. | Must be awaiting citizen approval. Errors: 400 invalid state, 403 wrong citizen, 404 assignment. |

## BookingController — `/api/bookings`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Create booking | POST `{{baseUrl}}/api/bookings` | Citizen auth + JSON. Body: `workerId`, `issueId`, `amount`. | Request: `{"workerId":21,"issueId":101,"amount":750.00}`. Response (201): `{"id":71,"citizenId":1,"workerId":21,"issueId":101,"bookingStatus":"PENDING","amount":750.0,"paymentRequired":true,"createdAt":"2026-08-23T10:00:00Z","updatedAt":"2026-08-23T10:00:00Z"}` | Auth: Yes. Role: complaint-owning `CITIZEN`. JWT: Yes. OAuth: No. | IDs and positive amount required. Worker must be available and `VERIFIED`; citizen must own issue. Save `id` as `bookingId`. |
| My bookings | GET `{{baseUrl}}/api/bookings/mine` | Auth header only. | Response: `[BookingResponse]`, e.g. `[ {"id":71,"bookingStatus":"PENDING","amount":750.0} ]`. | Auth: Yes. Role: citizen or worker; other roles receive bookings where they are citizen. JWT: Yes. OAuth: No. | Run after booking creation. |
| Accept booking | POST `{{baseUrl}}/api/bookings/{{bookingId}}/accept` | Worker auth; path `id`; no body/query. | Response: booking response with `"bookingStatus":"ACCEPTED"`. | Auth: Yes. Role: booked worker. JWT: Yes. OAuth: No. | Must currently be `PENDING`. 400 state; 403 wrong worker; 404 booking. |
| Start booking | POST `{{baseUrl}}/api/bookings/{{bookingId}}/start` | Worker auth; path `id`; no body/query. | Response: booking response with `"bookingStatus":"IN_PROGRESS"`. | Auth: Yes. Role: booked worker. JWT: Yes. OAuth: No. | Must be `ACCEPTED`. |
| Complete booking | POST `{{baseUrl}}/api/bookings/{{bookingId}}/complete` | Worker auth; path `id`; no body/query. | Response: booking response with `"bookingStatus":"COMPLETED"`. | Auth: Yes. Role: booked worker. JWT: Yes. OAuth: No. | Must be `IN_PROGRESS`. |
| Confirm booking completion | POST `{{baseUrl}}/api/bookings/{{bookingId}}/confirm-completion` | Citizen auth; path `id`; no body/query. | Response: booking response with `"bookingStatus":"PAYMENT_PENDING"`. | Auth: Yes. Role: booking citizen. JWT: Yes. OAuth: No. | Must be `COMPLETED`; this enables payment. 400 state; 403 wrong citizen. |

## PaymentController — `/api/payments`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Create Razorpay order | POST `{{baseUrl}}/api/payments/create-order` | Payer auth + JSON. No query/path. Body: `bookingId`, `amount`, optional `paymentSource` (`CITIZEN` default or `GOVERNMENT`). | Request: `{"bookingId":71,"amount":750.00,"paymentSource":"CITIZEN"}`. Response: `{"orderId":"order_QA123","amount":750.0,"currency":"INR","razorpayKey":"rzp_test_..."}` | Auth: Yes. Role: booking citizen for `CITIZEN`; `ADMIN` for `GOVERNMENT`. JWT: Yes. OAuth: No. | Booking must be `PAYMENT_PENDING`, payment required, and amount exactly match booking. One payment per booking. Errors: 400 lifecycle/amount, 403 payer role, 409 duplicate, 502 Razorpay failure, 503 missing keys. |
| Verify Razorpay payment | POST `{{baseUrl}}/api/payments/verify` | Payer auth + JSON. No query/path. | Request: `{"razorpay_order_id":"{{paymentOrderId}}","razorpay_payment_id":"pay_QA123","razorpay_signature":"<Razorpay Checkout signature>"}`. Response: `{"success":true}`. | Auth: Yes. Role: original payer. JWT: Yes. OAuth: No. | All three body strings must be nonblank. Signature must be generated by Razorpay. On valid signature payment becomes `SUCCESS`, worker earnings update, booking becomes `PAYMENT_COMPLETED`. Invalid signature returns `{"success":false}` and marks payment `FAILED`; 403 for other user; 404 unknown order. |
| Payment history | GET `{{baseUrl}}/api/payments/history` | Auth header only. | Response: `[ {"id":91,"bookingId":71,"payerId":1,"workerId":21,"amount":750.0,"currency":"INR","paymentSource":"CITIZEN","paymentStatus":"SUCCESS","razorpayOrderId":"order_QA123","razorpayPaymentId":"pay_QA123","createdAt":"2026-08-23T10:00:00Z","updatedAt":"2026-08-23T10:05:00Z"} ]` | Auth: Yes. Role: payer or worker. JWT: Yes. OAuth: No. | Run after verification. Invalid JWT: 401. |

## NotificationController — `/api/notifications`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| List notifications | GET `{{baseUrl}}/api/notifications` | Auth header; no query/path/body. | Response: `[ {"id":12,"title":"New assignment","message":"Broken streetlight","createdAt":"2026-08-23T10:00:00Z","read":false} ]` | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | Use after complaint matching, assignment, or work updates. 401 invalid JWT. |
| Mark notification read | POST `{{baseUrl}}/api/notifications/{{notificationId}}/read` | Auth header; path `id`; no body/query. | Response: empty body, normally 200. | Auth: Yes. Role: notification owner. JWT: Yes. OAuth: No. | 403 if notification belongs to another user; 404 if missing. |

## WorkerReviewController — `/api`

| Endpoint name | Method and full URL | Headers / query / path / body | Valid request and success response | Auth / authorization / JWT / OAuth | Validation, errors, testing order |
|---|---|---|---|---|---|
| Create worker review | POST `{{baseUrl}}/api/assignments/{{assignmentId}}/reviews` | Citizen auth + JSON; path `assignmentId`. | Request: `{"rating":5,"comment":"Fast and professional"}`. Response: `{"id":31,"assignmentId":51,"workerId":21,"complaintName":"Broken streetlight","rating":5,"comment":"Fast and professional","createdAt":"2026-08-23T12:00:00Z"}` | Auth: Yes. Role: reporting citizen / assignment owner. JWT: Yes. OAuth: No. | `rating` required, integer 1–5; work must be `APPROVED`; only one review per assignment. Errors: 400 status/rating, 403 ownership, 404 assignment, 409 duplicate. |
| List worker reviews | GET `{{baseUrl}}/api/workers/{{workerId}}/reviews` | Auth header; path `workerId`; no body/query. | Response: `[WorkerReviewResponse]`, e.g. `[ {"id":31,"assignmentId":51,"workerId":21,"complaintName":"Broken streetlight","rating":5,"comment":"Fast and professional","createdAt":"2026-08-23T12:00:00Z"} ]` | Auth: Yes. Role: any authenticated user. JWT: Yes. OAuth: No. | 404 if worker does not exist; an existing worker with no reviews returns `[]`. |

## Complete end-to-end Postman sequence

1. **Google login** — open `GET {{baseUrl}}/oauth2/authorization/google` in a browser, complete consent, capture JWT. Set `jwtCitizen`. Repeat with worker and government/admin accounts to set `jwtWorker` and `jwtAdmin`.
2. **Identify accounts** — call `GET /api/users/me` with each JWT. Confirm roles; the government account must already have `ADMIN` in the database because there is no public admin registration endpoint.
3. **Worker registration** — call `POST /api/workers/register` as worker with the registration JSON above. Save `id` as `workerId`.
4. **Government approval** — call `POST /api/admin/workers/{{workerId}}/verification?approved=true` as admin. Then test `GET /api/workers` and `GET /api/workers/{{workerId}}`.
5. **Citizen complaint** — call `POST /api/complaints` as citizen. Save `id` as `complaintId`. Test list, mine, and vote endpoints.
6. **Assignment flow** — test either `POST /api/assignments/request` as citizen or `POST /api/assignments` as admin. Save `assignmentId`; as worker call accept and complete; as citizen call citizen-verification; create and list review; inspect worker portfolio.
7. **Booking flow** — independently call `POST /api/bookings` as citizen for the verified worker and complaint. Save `bookingId`; as worker call accept, start, complete; as citizen call confirm-completion; test `GET /api/bookings/mine` for both users.
8. **Payment flow** — as citizen call `POST /api/payments/create-order`. Complete the actual Razorpay test Checkout using its order response; save its `order_id`, `payment_id`, and signature. Call `/api/payments/verify`, then `/api/payments/history`. Repeat the create-order test with `paymentSource:GOVERNMENT` while using the admin JWT and a separate `PAYMENT_PENDING` booking.
9. **Notifications** — call `GET /api/notifications` after each matching/assignment lifecycle event; use a returned ID with `POST /api/notifications/{{notificationId}}/read`.
10. **Negative checks** — repeat role-protected calls using the wrong JWT; try an unverified worker, duplicate review/payment/assignment, invalid booking transitions, wrong payment amount, and a fake Razorpay signature. Confirm the documented 400/403/409 responses.

## Practical Postman note

Postman can create the Razorpay order but cannot invent a valid signature. Use Razorpay Checkout in a test frontend or Razorpay's supported test-payment process, then copy the three returned fields into the Verify request. A fabricated signature is expected to return `{"success":false}`.
