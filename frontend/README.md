# CivicConnect frontend

A framework-free, dark UI for the CivicConnect Spring Boot API. It uses only HTML, CSS, and JavaScript.

## Run it

The backend CORS policy currently allows `http://localhost:5500` only. From this `frontend` folder, run any static server on port 5500, for example:

```powershell
node server.js
```

Then open `http://localhost:5500`.

The Google OAuth callback is already configured to return to this address. Configure the backend environment variables (`CLIENT_ID`, `CLIENT_SECRET`, `JWT_SECRET`, database settings and Razorpay test keys) before using live features.

## Note about roles

The current `GET /api/users/me` response does not include a role and the JWT does not contain a role claim. Therefore, this UI presents the available workspaces and leaves role enforcement to the backend, which correctly returns `403` for an unauthorized action. Add a `role` field to `UserResponse` later if role-specific navigation is needed.
