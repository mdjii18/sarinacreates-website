# sarinacreates — permanent-storage version

Products, orders, admin login, and product photos are all stored in
**MongoDB Atlas** — a free cloud database that lives completely outside
your hosting server. This is the permanent fix for photos/products
"resetting" after a while: that used to happen because free hosts (Render,
Railway, etc.) wipe the server's own disk on every redeploy or restart.
MongoDB Atlas is unaffected by that, so nothing you add ever disappears.

Customer accounts and the shopping cart still live in the browser
(localStorage) — that's fine, since those are naturally per-visitor.

## One-time setup: create your free MongoDB Atlas database

1. Go to https://www.mongodb.com/cloud/atlas and sign up (free).
2. Create a new **free (M0) cluster** — takes a couple of minutes to spin up.
3. Under **Database Access**, create a database user (username + password).
4. Under **Network Access**, add `0.0.0.0/0` (allow access from anywhere) —
   simplest option for a small shop's server.
5. Click **Connect** on your cluster → **Drivers** → copy the connection
   string. It looks like:
   `mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/`
6. Paste that into your `.env` file (copy `.env.example` to `.env` first)
   as `MONGODB_URI=...`, with your real username/password filled in.

## Running it locally

```
npm install
npm start
```

Then open `http://localhost:3000`. The first time it runs, it connects to
your MongoDB database and creates the admin account automatically.

## Admin login

Go to `/admin/login.html`.
- **Default password:** `sarina2026` (or set `ADMIN_PASSWORD` in `.env`
  before the very first run to choose your own from day one)
- Change it from **Settings** inside the dashboard after logging in.

## Deploying (Render, free tier)

1. Push this project to a GitHub repo (or upload it directly if your host
   supports that).
2. On Render: New → Web Service → connect the repo.
3. Build command: `npm install`
4. Start command: `npm start`
5. Add environment variables (Render dashboard → Environment):
   - `MONGODB_URI` — your Atlas connection string (required)
   - `ADMIN_PASSWORD` — optional, custom admin password
6. Deploy. Render gives you a URL like `sarinacreates.onrender.com`.

Because everything now lives in MongoDB Atlas instead of the server's own
disk, redeploys, restarts, and free-tier idle spin-downs no longer wipe
your products, photos, or orders.

## A note on photo sizes

Photos are stored as base64 data directly in each product's record in
MongoDB. The admin panel already compresses images before upload, which
keeps this fast and well within MongoDB's free-tier limits for a shop of
this size. If the catalog grows very large in the future (hundreds of
products with many photos each), moving images to a dedicated file/image
host (like Cloudinary) is the next natural upgrade — ask any time.
