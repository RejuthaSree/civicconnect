# CivicConnect Ordered Testing Guide

This guide tests the current backend in the correct dependency order. Use Postman and the existing Google OAuth login flow.

## 1. Start prerequisites

1. Start PostgreSQL using the existing Docker configuration:

   ```powershell
   docker compose up -d postgres
   ```

2. Set the existing environment variables before starting the backend:

   ```properties
   CLIENT_ID=<google-client-id>
   CLIENT_SECRET=<google-client-secret>
   JWT_SECRET=<jwt-secret>
   RAZORPAY_KEY_ID=<razorpay-test-key-id>
   RAZORPAY_KEY_SECRET=<razorpay-test-key-secret>
   ```

3. Start the application:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

Base URL: `http://localhost:8080`

## 2. Create Postman variables

Create an environment with these variables:

| Variable | Initial value |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `citizenJwt` | Empty |
| `workerJwt` | Empty |
| `adminJwt` | Empty |
| `complaintId` | Empty |
| `workerId` | Empty |
| `assignmentId` | Empty |
| `bookingId` | Empty |
| `paymentOrderId` | Empty |
| `notificationId` | Empty |

Use this header on all `/api/**` requests:

```http
Authorization: Bearer {{citizenJwt}}
```

Replace `citizenJwt` with `workerJwt` or `adminJwt` whenever a step specifies it.

## 3. Obtain three JWT tokens

You need three separate accounts:

| Account | Required role | How to obtain |
|---|---|---|
| Citizen | `CITIZEN` | Google login creates this role by default. |
| Worker | initially `CITIZEN`, then becomes `WORKER` during worker registration | Use a different Google account. |
| Government authority | `ADMIN` | Use an account whose role is already set to `ADMIN` in PostgreSQL. There is no public admin registration endpoint. |

For each account, open this URL in a browser:

```text
{{baseUrl}}/oauth2/authorization/google
```

After Google login, the frontend callback URL receives `?token=<JWT>`. Copy each token to the appropriate Postman variable.

Verify the current token:

```http
GET {{baseUrl}}/api/users/me
Authorization: Bearer {{citizenJwt}}
```

Expected response:

```json
{
  "username": "Citizen Name",
  "id": 1,
  "provider": "GOOGLE",
  "email": "citizen@example.com"
}
```

## 4. Register and approve a worker

### 4.1 Register worker

```http
POST {{baseUrl}}/api/workers/register
Authorization: Bearer {{workerJwt}}
Content-Type: application/json
```

```json
{
  "skill": "ELECTRICIAN",
  "serviceArea": "Central Ward",
  "latitude": 18.5204,
  "longitude": 73.8567,
  "workRadiusKm": 10,
  "city": "Pune",
  "phoneNumber": "9876543210",
  "address": "Central Ward, Pune",
  "profilePhotoUrl": "https://example.com/profile.jpg",
  "aadhaarMasked": "XXXX-XXXX-1234",
  "governmentIdReference": "https://example.com/id-proof.pdf",
  "certificates": "Electrical licence",
  "experienceYears": 5
}
```

Expected: `200 OK` with `"verificationStatus": "PENDING"`. Save response `id` as `workerId`.

### 4.2 Government approves worker

```http
POST {{baseUrl}}/api/admin/workers/{{workerId}}/verification?approved=true&notes=Documents%20validated
Authorization: Bearer {{adminJwt}}
```

Expected: `200 OK` with `"verificationStatus": "VERIFIED"`.

### 4.3 Confirm worker is publicly visible

```http
GET {{baseUrl}}/api/workers?skill=ELECTRICIAN&page=0&size=20
Authorization: Bearer {{citizenJwt}}
```

Expected: worker appears in `content` with `verificationStatus: VERIFIED`.

## 5. Citizen complaint flow

### 5.1 Create a complaint

```http
POST {{baseUrl}}/api/complaints
Authorization: Bearer {{citizenJwt}}
Content-Type: application/json
```

```json
{
  "title": "Broken streetlight",
  "description": "Streetlight has been off for two nights.",
  "address": "12 MG Road",
  "area": "Central Ward",
  "city": "Pune",
  "latitude": 18.5204,
  "longitude": 73.8567,
  "imageUrl": "https://example.com/broken-light.jpg",
  "issueType": "ELECTRICITY",
  "priority": "HIGH"
}
```

Expected: `201 Created`. Save response `id` as `complaintId`.

### 5.2 Check complaint APIs

```http
GET {{baseUrl}}/api/complaints?page=0&size=20
GET {{baseUrl}}/api/complaints/mine?page=0&size=20
POST {{baseUrl}}/api/complaints/{{complaintId}}/vote
```

Use the citizen JWT for all three. The vote response should have an incremented `upvotes` value.

## 6. Assignment flow — option A: citizen requests worker

```http
POST {{baseUrl}}/api/assignments/request?complaintId={{complaintId}}&workerId={{workerId}}
Authorization: Bearer {{citizenJwt}}
```

Expected: `200 OK`. Save response `id` as `assignmentId`.

Use this instead if government assigns the worker:

```http
POST {{baseUrl}}/api/assignments?complaintId={{complaintId}}&workerId={{workerId}}
Authorization: Bearer {{adminJwt}}
```

Do not execute both against the same complaint; a second assignment returns `409 Conflict`.

### 6.1 Worker accepts assignment

```http
POST {{baseUrl}}/api/assignments/{{assignmentId}}/accept
Authorization: Bearer {{workerJwt}}
```

Expected: `acceptedAt` is populated.

### 6.2 Worker completes assignment

```http
POST {{baseUrl}}/api/assignments/{{assignmentId}}/complete
Authorization: Bearer {{workerJwt}}
Content-Type: application/json
```

```json
{
  "remarks": "Replaced the faulty fixture.",
  "beforeImageUrl": "https://example.com/before.jpg",
  "afterImageUrl": "https://example.com/after.jpg"
}
```

Expected: `completionStatus` is `PENDING_CITIZEN_APPROVAL`.

### 6.3 Citizen verifies assignment

```http
POST {{baseUrl}}/api/assignments/{{assignmentId}}/citizen-verification?approved=true
Authorization: Bearer {{citizenJwt}}
```

Expected: `completionStatus` becomes `APPROVED`.

### 6.4 Finalize complaint

```http
POST {{baseUrl}}/api/complaints/{{complaintId}}/verify
Authorization: Bearer {{citizenJwt}}
```

Expected: complaint status becomes `RESOLVED`.

## 7. Booking and payment flow

Bookings are separate from assignments. To test payment, create a new booking with the same verified worker and an issue owned by the citizen.

### 7.1 Create booking

```http
POST {{baseUrl}}/api/bookings
Authorization: Bearer {{citizenJwt}}
Content-Type: application/json
```

```json
{
  "workerId": {{workerId}},
  "issueId": {{complaintId}},
  "amount": 750.00
}
```

Expected: `201 Created` and `bookingStatus: PENDING`. Save response `id` as `bookingId`.

### 7.2 Worker completes booking lifecycle

```http
POST {{baseUrl}}/api/bookings/{{bookingId}}/accept
POST {{baseUrl}}/api/bookings/{{bookingId}}/start
POST {{baseUrl}}/api/bookings/{{bookingId}}/complete
```

Use `Authorization: Bearer {{workerJwt}}` for every request. Expected statuses: `ACCEPTED`, `IN_PROGRESS`, then `COMPLETED`.

### 7.3 Citizen confirms booking completion

```http
POST {{baseUrl}}/api/bookings/{{bookingId}}/confirm-completion
Authorization: Bearer {{citizenJwt}}
```

Expected: `bookingStatus: PAYMENT_PENDING`.

### 7.4 Create Razorpay order

```http
POST {{baseUrl}}/api/payments/create-order
Authorization: Bearer {{citizenJwt}}
Content-Type: application/json
```

```json
{
  "bookingId": {{bookingId}},
  "amount": 750.00,
  "paymentSource": "CITIZEN"
}
```

Expected:

```json
{
  "orderId": "order_...",
  "amount": 750.0,
  "currency": "INR",
  "razorpayKey": "rzp_test_..."
}
```

Save `orderId` as `paymentOrderId`.

### 7.5 Complete Razorpay test checkout

Use Razorpay Checkout in a test frontend with the returned `orderId` and `razorpayKey`. Razorpay returns:

- `razorpay_order_id`
- `razorpay_payment_id`
- `razorpay_signature`

### 7.6 Verify payment

```http
POST {{baseUrl}}/api/payments/verify
Authorization: Bearer {{citizenJwt}}
Content-Type: application/json
```

```json
{
  "razorpay_order_id": "{{paymentOrderId}}",
  "razorpay_payment_id": "pay_...",
  "razorpay_signature": "<signature from Razorpay Checkout>"
}
```

Expected:

```json
{ "success": true }
```

The booking must now be `PAYMENT_COMPLETED` and the worker earnings update.

### 7.7 Check payment history

```http
GET {{baseUrl}}/api/payments/history
Authorization: Bearer {{citizenJwt}}
```

Repeat with `workerJwt`; both sides should see the transaction.

## 8. Notifications, reviews, and portfolio

### 8.1 Notifications

```http
GET {{baseUrl}}/api/notifications
Authorization: Bearer {{workerJwt}}
```

Save any returned notification `id` as `notificationId`, then:

```http
POST {{baseUrl}}/api/notifications/{{notificationId}}/read
Authorization: Bearer {{workerJwt}}
```

### 8.2 Review approved assignment

```http
POST {{baseUrl}}/api/assignments/{{assignmentId}}/reviews
Authorization: Bearer {{citizenJwt}}
Content-Type: application/json
```

```json
{
  "rating": 5,
  "comment": "Fast and professional work."
}
```

### 8.3 List reviews and portfolio

```http
GET {{baseUrl}}/api/workers/{{workerId}}/reviews
GET {{baseUrl}}/api/workers/{{workerId}}/portfolio
Authorization: Bearer {{citizenJwt}}
```

## 9. Required negative tests

Run these to confirm endpoint protection:

| Test | Expected result |
|---|---|
| Call any `/api/**` endpoint without a JWT | `401 Unauthorized` |
| Register worker using admin JWT | `403 Forbidden` |
| Book a worker before government verification | `400 Bad Request` |
| Use another worker's token to accept a booking/assignment | `403 Forbidden` |
| Complete booking before accepting it | `400 Bad Request` |
| Create payment before booking is `PAYMENT_PENDING` | `400 Bad Request` |
| Create a second payment for the same booking | `409 Conflict` |
| Verify payment with a fabricated signature | `{"success":false}` |
| Review an unapproved assignment | `400 Bad Request` |
| Create a second review for one assignment | `409 Conflict` |

## 10. Troubleshooting

| Symptom | Check |
|---|---|
| Application fails at startup | Ensure PostgreSQL is running on `localhost:5432` and database is `civicconnect`. |
| Every API returns 401 | Use the `Authorization: Bearer <JWT>` header and ensure token has not expired. |
| Worker cannot be selected | Approve the worker as admin; verify `available=true` and `verificationStatus=VERIFIED`. |
| Razorpay create-order returns 503 | Configure `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`. |
| Razorpay verify returns false | Use values actually returned by Razorpay Checkout; never create the signature manually. |
