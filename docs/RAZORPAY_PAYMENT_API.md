# CivicConnect Razorpay Payment API

All endpoints require the existing Bearer JWT. Amounts are expressed in INR major units.

## Booking lifecycle

`PENDING -> ACCEPTED -> IN_PROGRESS -> COMPLETED -> PAYMENT_PENDING -> PAYMENT_COMPLETED`

The citizen creates a booking and only the selected, verified worker can accept, start, or complete it. The citizen's completion confirmation changes the booking to `PAYMENT_PENDING`, which enables payment.

## Create Razorpay order

`POST /api/payments/create-order`

```json
{
  "bookingId": 41,
  "amount": 750.00,
  "paymentSource": "CITIZEN"
}
```

`paymentSource` is optional and defaults to `CITIZEN`. `GOVERNMENT` requires the existing `ADMIN` role, which represents a government authority in this project.

```json
{
  "orderId": "order_Qa123456789",
  "amount": 750.0,
  "currency": "INR",
  "razorpayKey": "rzp_test_..."
}
```

## Verify payment

`POST /api/payments/verify`

```json
{
  "razorpay_order_id": "order_Qa123456789",
  "razorpay_payment_id": "pay_Qa123456789",
  "razorpay_signature": "signature-returned-by-razorpay"
}
```

```json
{ "success": true }
```

The backend verifies the Razorpay signature before recording `SUCCESS`, updating worker earnings, and moving the booking to `PAYMENT_COMPLETED`.

## Payment history

`GET /api/payments/history`

Returns payments paid by the current citizen/government authority or earned by the current worker.

## Required environment variables

```properties
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=...
```

No payment credentials are returned other than the publishable `razorpayKey` required by Razorpay Checkout.
