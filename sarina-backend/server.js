/* sarinacreates — Express server.
   Serves the static site from /public and a JSON API under /api/*.

   Products, orders, admin login, and product PHOTOS are all stored in
   MongoDB Atlas (a free cloud database) instead of local files. This is
   the permanent fix for the "everything resets after a while" problem:
   on free hosts (Render, Railway, etc.) the server's own disk gets wiped
   on every redeploy or restart, but MongoDB Atlas lives outside the
   server entirely, so nothing you add ever disappears.

   Photos are stored as base64 data directly inside each product's
   document (fine at this shop's scale — images are compressed on the
   admin side before upload). No more /uploads folder needed.

   Setup: create a free cluster at https://www.mongodb.com/cloud/atlas,
   grab its connection string, and put it in .env as MONGODB_URI
   (see .env.example). */

const express = require("express");
const multer = require("multer");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { MongoClient } = require("mongodb");

const app = express();
const PORT = process.env.PORT || 3000;
const MONGODB_URI = process.env.MONGODB_URI;
const ADMIN_PASSWORD_DEFAULT = process.env.ADMIN_PASSWORD || "sarina2026";

if (!MONGODB_URI) {
  console.error(
    "\nMissing MONGODB_URI.\n" +
    "1) Create a free cluster at https://www.mongodb.com/cloud/atlas\n" +
    "2) Copy its connection string\n" +
    "3) Put it in your .env file as MONGODB_URI=... (see .env.example)\n"
  );
  process.exit(1);
}

let db; // set once initDB() connects

function sha256(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}
function stripMongoId(doc) {
  if (!doc) return doc;
  const { _id, ...rest } = doc;
  return rest;
}

/* ---------------- one-time startup: connect + seed + migrate ---------------- */

async function initDB() {
  const client = new MongoClient(MONGODB_URI);
  await client.connect();
  db = client.db("sarinacreates");

  const admin = await db.collection("admin").findOne({ _id: "admin" });
  if (!admin) {
    await db.collection("admin").insertOne({
      _id: "admin",
      email: "you@sarinacreates.com",
      passwordHash: sha256(ADMIN_PASSWORD_DEFAULT),
      sessions: []
    });
  }

  // If Mongo has no products yet, pull in anything from the old local
  // data/db.json + uploads/ folder (from a previous version of this app),
  // so upgrading doesn't lose whatever you already had set up locally.
  const productCount = await db.collection("products").countDocuments();
  if (productCount === 0) await migrateFromLocalFiles();
}

async function migrateFromLocalFiles() {
  const DB_PATH = path.join(__dirname, "data", "db.json");
  const UPLOADS_DIR = path.join(__dirname, "uploads");
  if (!fs.existsSync(DB_PATH)) return;
  let old;
  try {
    old = JSON.parse(fs.readFileSync(DB_PATH, "utf8"));
  } catch (e) {
    return;
  }
  if (!old.products || !old.products.length) return;

  console.log(`Migrating ${old.products.length} product(s) from data/db.json into MongoDB...`);
  for (const p of old.products) {
    const images = [];
    for (const imgPath of p.images || []) {
      if (typeof imgPath === "string" && imgPath.startsWith("/uploads/")) {
        const filePath = path.join(UPLOADS_DIR, path.basename(imgPath));
        if (fs.existsSync(filePath)) {
          const buf = fs.readFileSync(filePath);
          const ext = (path.extname(filePath).slice(1) || "jpg").toLowerCase();
          images.push(`data:image/${ext};base64,${buf.toString("base64")}`);
          continue;
        }
      }
      images.push(imgPath); // already a data URI, or file missing — keep as-is
    }
    await db.collection("products").updateOne(
      { id: p.id },
      { $set: { ...p, images, _ts: Date.now() } },
      { upsert: true }
    );
  }
  if (old.orders && old.orders.length) {
    for (const o of old.orders) {
      await db.collection("orders").updateOne({ id: o.id }, { $set: o }, { upsert: true });
    }
  }
  console.log("Migration into MongoDB complete.");
}

/* ---------------- admin auth ---------------- */

async function requireAdmin(req, res, next) {
  const auth = req.headers.authorization || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : null;
  if (!token) return res.status(401).json({ error: "Not authorized" });
  const admin = await db.collection("admin").findOne({ _id: "admin" });
  if (!admin || !admin.sessions.includes(token)) return res.status(401).json({ error: "Not authorized" });
  next();
}

const upload = multer({
  storage: multer.memoryStorage(), // photos go straight into MongoDB, not disk
  limits: { fileSize: 15 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!file.mimetype.startsWith("image/")) return cb(new Error("Only image files are allowed"));
    cb(null, true);
  }
});

app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "public")));

app.post("/api/admin/login", async (req, res) => {
  const { password } = req.body || {};
  const admin = await db.collection("admin").findOne({ _id: "admin" });
  if (!password || sha256(password) !== admin.passwordHash) {
    return res.status(401).json({ error: "Incorrect password" });
  }
  const token = crypto.randomBytes(24).toString("hex");
  await db.collection("admin").updateOne({ _id: "admin" }, { $push: { sessions: token } });
  res.json({ token });
});

app.post("/api/admin/logout", requireAdmin, async (req, res) => {
  const token = req.headers.authorization.slice(7);
  await db.collection("admin").updateOne({ _id: "admin" }, { $pull: { sessions: token } });
  res.json({ ok: true });
});

app.post("/api/admin/password", requireAdmin, async (req, res) => {
  const { newPassword } = req.body || {};
  if (!newPassword || newPassword.length < 6) {
    return res.status(400).json({ error: "Password must be at least 6 characters." });
  }
  await db.collection("admin").updateOne({ _id: "admin" }, { $set: { passwordHash: sha256(newPassword) } });
  res.json({ ok: true });
});

/* ---------------- products ---------------- */

app.get("/api/products", async (req, res) => {
  const products = await db.collection("products").find({}).sort({ _ts: -1 }).toArray();
  res.json(products.map(stripMongoId));
});

app.get("/api/products/:id", async (req, res) => {
  const p = await db.collection("products").findOne({ id: req.params.id });
  if (!p) return res.status(404).json({ error: "Not found" });
  res.json(stripMongoId(p));
});

// Create or update a product. Multipart form: text fields + optional
// "images" files (new photos, stored as base64 in MongoDB) +
// "existingImages" (JSON array of image data to keep, when editing).
app.post("/api/products", requireAdmin, upload.array("images", 8), async (req, res) => {
  const body = req.body || {};
  let id = (body.id || "").trim();
  const existing = id ? await db.collection("products").findOne({ id }) : null;
  if (!existing) id = "p" + Date.now().toString(36);

  let keptImages = [];
  if (body.existingImages) {
    try { keptImages = JSON.parse(body.existingImages); } catch (e) { keptImages = []; }
  }
  const newImages = (req.files || []).map(
    f => `data:${f.mimetype};base64,${f.buffer.toString("base64")}`
  );
  const images = keptImages.concat(newImages);

  const product = {
    id,
    name: (body.name || "").trim(),
    category: (body.category || "").trim(),
    price: parseFloat(body.price) || 0,
    dims: (body.dims || "").trim(),
    stock: parseInt(body.stock, 10) || 0,
    desc: (body.desc || "").trim(),
    images,
    palette: [body.c1 || "#123B3B", body.c2 || "#1F7A6C", body.c3 || "#EFEAE0"],
    angle: body.angle ? parseInt(body.angle, 10) : Math.floor(Math.random() * 180),
    _ts: existing ? existing._ts : Date.now()
  };

  await db.collection("products").updateOne({ id }, { $set: product }, { upsert: true });
  res.json(product);
});

app.delete("/api/products/:id", requireAdmin, async (req, res) => {
  await db.collection("products").deleteOne({ id: req.params.id });
  res.json({ ok: true });
});

/* ---------------- orders ---------------- */

app.get("/api/orders", requireAdmin, async (req, res) => {
  const orders = await db.collection("orders").find({}).sort({ createdAt: -1 }).toArray();
  res.json(orders.map(stripMongoId));
});

app.get("/api/orders/mine", async (req, res) => {
  const email = (req.query.email || "").trim().toLowerCase();
  if (!email) return res.json([]);
  const orders = await db.collection("orders").find({}).sort({ createdAt: -1 }).toArray();
  const mine = orders.filter(o => o.customer && o.customer.email && o.customer.email.toLowerCase() === email);
  res.json(mine.map(stripMongoId));
});

app.get("/api/orders/:id", async (req, res) => {
  const o = await db.collection("orders").findOne({ id: req.params.id });
  if (!o) return res.status(404).json({ error: "Not found" });
  res.json(stripMongoId(o));
});

app.post("/api/orders", async (req, res) => {
  const { items, customer } = req.body || {};
  if (!Array.isArray(items) || !items.length) {
    return res.status(400).json({ error: "Your cart is empty." });
  }
  const lines = [];
  for (const it of items) {
    const p = await db.collection("products").findOne({ id: it.id });
    if (!p) continue;
    const qty = Math.min(it.qty || 0, p.stock);
    if (qty > 0) lines.push({ id: p.id, name: p.name, price: p.price, qty });
  }
  if (!lines.length) {
    return res.status(400).json({ error: "Everything in your cart just sold out." });
  }
  const order = {
    id: "SC" + Date.now().toString(36).toUpperCase(),
    createdAt: Date.now(),
    status: "Pending",
    customer: customer || {},
    items: lines,
    total: lines.reduce((s, l) => s + l.price * l.qty, 0)
  };
  await db.collection("orders").insertOne(order);
  for (const l of lines) {
    await db.collection("products").updateOne({ id: l.id }, { $inc: { stock: -l.qty } });
  }
  res.json(stripMongoId(order));
});

app.put("/api/orders/:id/status", requireAdmin, async (req, res) => {
  const o = await db.collection("orders").findOne({ id: req.params.id });
  if (!o) return res.status(404).json({ error: "Not found" });
  const status = (req.body && req.body.status) || o.status;
  await db.collection("orders").updateOne({ id: req.params.id }, { $set: { status } });
  res.json(stripMongoId({ ...o, status }));
});

// Multer / generic error handler so a bad upload returns JSON, not a crash.
app.use((err, req, res, next) => {
  console.error(err);
  res.status(400).json({ error: err.message || "Something went wrong." });
});

initDB()
  .then(() => {
    app.listen(PORT, () => {
      console.log(`sarinacreates running on port ${PORT} — connected to MongoDB Atlas`);
    });
  })
  .catch(err => {
    console.error("Failed to connect to MongoDB:", err.message);
    process.exit(1);
  });
