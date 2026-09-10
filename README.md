# CivicConnect

CivicConnect is a civic issue-resolution platform. Citizens can report household or public problems, work with verified local workers, track progress with before-and-after evidence, confirm completion, and make secure Razorpay payments. Administrators manage public issues, worker verification, assignments, and government-funded payments.

The project contains:

- A Spring Boot + PostgreSQL backend in `src/`
- frontend in `frontend/` using HTML, CSS, and JavaScript
- Google OAuth sign-in with JWT API access
- Razorpay Checkout with server-side signature verification(test api key-> after deployment live key)

## Features

- Role-aware citizen, worker, and administrator workspaces
- Google sign-in and protected API calls
- Location-aware civic complaint reporting
- Household and public issue payment responsibility
- One upvote per citizen account for each complaint
- Worker registration, verification, availability, portfolio, and reviews
- Assignment workflow with acceptance, before/after proof, citizen verification, and reviews
- Booking workflow with worker progress updates and completion confirmation
- Razorpay Checkout; payment is marked successful only after server-side signature verification
- Notifications and payment history
- **AI complaint classification** powered by Google Gemini — suggests category, severity, suggested department, and confidence. Stored as JSON in each complaint; admin override promotes it to the live issue type and priority.
- **Duplicate complaint detection** — combines text similarity (Sørensen–Dice bigram coefficient) with geographic proximity (Haversine, 2 km radius) or area/city match. Citizens see a post-submit warning panel with similar reports; admins approve grouping them under a `DUP-N` group key.

## Prerequisites

- docker destop
- Java 21 or newer
- PostgreSQL 14 or newer
- Node.js 18 or newer
- A Google OAuth client configured for local development
- A Razorpay account and keys
- 
## 0.Docker setup and PostgreSQL Docker Commands

Start PostgreSQL:

```powershell/terminal
docker start civic-connect-new
```
Connect to the database:

```docker exec -it civic-connect-new psql -U postgres -d civicconnect```

Useful PostgreSQL commands:

```\dt```
```SELECT * FROM complaints;```
```SELECT id, username, email, role FROM users;```
```SELECT id, user_id, skill, verification_status, available FROM workers;```
```SELECT * FROM assignments;```
```SELECT * FROM bookings;```
```SELECT * FROM payments;```
```SELECT * FROM complaint_votes;```
```\q```


## 1. Create the database

Open PostgreSQL and create the database:

```sql
CREATE DATABASE civicconnect;
```

The default backend connection is:

```text
Host: localhost
Port: 5432
Database: civicconnect
Username: postgres
Password: postgres
```

Change `spring.datasource.*` in `src/main/resources/application.properties` if your PostgreSQL credentials differ. Hibernate is configured with `ddl-auto=update`, so required tables are created or updated at startup.

## 2. Configure environment variables

Set the following in the same terminal used to start the backend. Do not commit these secrets.

### PowerShell

```powershell
$env:CLIENT_ID="your_google_client_id"
$env:CLIENT_SECRET="your_google_client_secret"
$env:JWT_SECRET="a_long_random_secret_at_least_32_characters"
$env:GEMINI_API_KEY="your_gemini_key"
$env:RAZORPAY_KEY_ID="rzp_live_your_key_id"
$env:RAZORPAY_KEY_SECRET="your_razorpay_secret"
$env:FRONTEND_CALLBACK_URL="http://localhost:5500"
```

For test development, use `rzp_test_...` keys. For genuine payments, use your `rzp_live_...` key pair. Never use live credentials until the project is deployed over HTTPS and your Razorpay account is ready to accept payments.

In Google Cloud Console, add this redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

## 3. Start the backend

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend starts at `http://localhost:8080`.

## 4. Start the frontend

Open a second terminal from the project root:

```powershell
node .\frontend\server.js
```

Open [http://localhost:5500](http://localhost:5500) in a browser. Do not open `index.html` directly from the filesystem: Google sign-in and browser API requests require the local web server.

## Roles and workflows

### Citizen workflow

1. Sign in with Google. New accounts begin as `CITIZEN`.
2. Report an issue and select its payment responsibility:
   - **Household**: the reporting citizen pays after completed work is confirmed.
   - **Public**: an administrator/government account pays after completed work is confirmed.
3. Browse verified workers and choose one from the readable worker list.
4. Request a worker, track the assignment, and view before/after evidence.
5. Verify or reject completed work.
6. For household issues, complete payment with Razorpay Checkout and leave a review.

### Worker workflow

1. Register a worker profile and wait for administrator verification.
2. Maintain availability and view the worker profile on the dashboard.
3. Open only assignments and bookings belonging to the signed-in worker.
4. Accept work, upload before/after image URLs, and submit completion notes.
5. Track citizen verification, ratings, earnings, and reviews.

### Administrator workflow

1. Review reported civic issues and adjust their priority.
2. Review AI classification suggestions (category, severity, confidence, suggested department) and optionally **override** them to apply the AI's suggestion directly to the complaint's issue type and priority.
3. Detect and resolve duplicate complaints:
   - Scan any complaint to list the top-5 text/geo matches.
   - Group two or more complaints under a single `DUP-{minId}` group key.
   - Remove individual complaints from a group when they don't belong.
4. View the protected workforce list, approve/reject worker applications, and set availability.
5. Assign verified workers to public complaints.
6. Monitor assignment and booking status.
7. For a completed **public** issue, pay through Razorpay using the administrator/government flow.

## Payment rules

Payment responsibility is enforced on the server; users cannot change it from the browser.

| Issue type | Authorized payer | Payment source |
| --- | --- | --- |
| Household | The citizen who created the booking | `CITIZEN` |
| Public | An administrator | `GOVERNMENT` |

A booking becomes payable only after the worker completes the work and the citizen confirms completion. The backend creates a Razorpay order, and marks the payment successful only after it validates Razorpay's signature.

## Main API routes

All `/api/**` routes require `Authorization: Bearer <JWT>` unless stated otherwise.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/oauth2/authorization/google` | Start Google sign-in (browser redirect) |
| GET | `/api/users/me` | Current user and role |
| POST | `/api/complaints` | Create a complaint (response includes potential duplicates) |
| GET | `/api/complaints` | Browse complaints |
| GET | `/api/complaints/mine` | Current citizen's complaints |
| GET | `/api/complaints/{id}` | Single complaint detail |
| GET | `/api/complaints/{id}/with-duplicates` | Single complaint + potential duplicate matches (citizen-accessible) |
| POST | `/api/complaints/{id}/vote` | Upvote once per citizen account |
| POST | `/api/complaints/{id}/verify` | Verify a resolved complaint |
| POST | `/api/workers/register` | Register a worker profile |
| GET | `/api/workers` | Browse verified workers |
| GET | `/api/workers/me` | Current worker profile |
| GET | `/api/assignments/mine` | Assignments relevant to the current role |
| POST | `/api/assignments/request` | Citizen requests a worker |
| POST | `/api/assignments/{id}/accept` | Worker accepts assignment |
| POST | `/api/assignments/{id}/complete` | Worker submits work proof |
| POST | `/api/assignments/{id}/citizen-verification` | Citizen approves/rejects work |
| POST | `/api/bookings` | Citizen creates a booking |
| GET | `/api/bookings/mine` | Relevant bookings for the current role |
| POST | `/api/bookings/{id}/confirm-completion` | Citizen confirms booking completion |
| POST | `/api/payments/create-order` | Create Razorpay order |
| POST | `/api/payments/verify` | Verify Razorpay payment signature |
| GET | `/api/admin/workers` | Administrator workforce list |
| POST | `/api/admin/workers/{id}/verification` | Approve or reject worker |
| PATCH | `/api/admin/complaints/{id}/priority` | Change complaint priority |
| PATCH | `/api/admin/complaints/{id}/classification?category=&priority=&apply=true` | Override AI classification (optionally writes to issue type + priority) |
| GET | `/api/admin/complaints/{id}/duplicates` | Run duplicate scan for a complaint |
| POST | `/api/admin/complaints/duplicates/group?ids=1,2,3` | Tag a list of complaints with the same `DUP-N` group key |
| DELETE | `/api/admin/complaints/{id}/duplicates/group` | Remove one complaint from its duplicate group |

## Development checks

```powershell
.\mvnw.cmd test -DskipTests
node --check .\frontend\app.js
```

## Security notes

- Keep OAuth, JWT, Razorpay, database, and Gemini credentials in environment variables.
- Do not commit `.env` files, keys, secrets, or payment details.
- Use Razorpay test keys for local development.
- Use HTTPS, secure secret storage, and production OAuth redirect URIs before accepting live payments.
