/* sarinacreates — client-side layer.
   Products and orders now live on the server (shared across every device);
   customer accounts and the shopping cart still live in this browser's
   localStorage, which is fine since they're per-visitor anyway. */

const SC = (() => {
  const KEYS = {
    users: "sc_users",
    session: "sc_session",
    adminSession: "sc_admin_token",
    cart: "sc_cart"
  };

  function read(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (e) { return fallback; }
  }
  function write(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (e) {
      return false;
    }
  }

  async function hash(text) {
    const enc = new TextEncoder().encode(text);
    const buf = await crypto.subtle.digest("SHA-256", enc);
    return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
  }

  function init() {
    if (!localStorage.getItem(KEYS.users)) write(KEYS.users, []);
    if (!localStorage.getItem(KEYS.cart)) write(KEYS.cart, []);
  }

  // ---------- products (server-backed) ----------
  async function getProducts() {
    const res = await fetch("/api/products");
    if (!res.ok) return [];
    return res.json();
  }
  async function getProduct(id) {
    const res = await fetch("/api/products/" + encodeURIComponent(id));
    if (!res.ok) return null;
    return res.json();
  }
  // fields: {id, name, category, price, dims, stock, desc, palette:[a,b,c], angle}
  // newImageBlobs: array of {blob, filename} to upload
  // existingImages: array of image URLs already on the product to keep (editing only)
  async function saveProduct(fields, newImageBlobs, existingImages) {
    const token = getAdminToken();
    const form = new FormData();
    form.append("id", fields.id || "");
    form.append("name", fields.name);
    form.append("category", fields.category);
    form.append("price", fields.price);
    form.append("dims", fields.dims);
    form.append("stock", fields.stock);
    form.append("desc", fields.desc);
    form.append("c1", fields.palette[0]);
    form.append("c2", fields.palette[1]);
    form.append("c3", fields.palette[2]);
    form.append("angle", fields.angle);
    form.append("existingImages", JSON.stringify(existingImages || []));
    (newImageBlobs || []).forEach(item => form.append("images", item.blob, item.filename));

    const res = await fetch("/api/products", {
      method: "POST",
      headers: { Authorization: "Bearer " + token },
      body: form
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err.error || "Couldn't save this piece." };
    }
    return { ok: true, product: await res.json() };
  }
  async function deleteProduct(id) {
    const token = getAdminToken();
    await fetch("/api/products/" + encodeURIComponent(id), {
      method: "DELETE",
      headers: { Authorization: "Bearer " + token }
    });
  }
  // Shrinks a photo to a reasonable max dimension before it's uploaded —
  // keeps uploads fast, especially over a slow connection.
  function resizeImage(file, maxDim = 1400, quality = 0.82) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      const reader = new FileReader();
      reader.onerror = () => reject(reader.error);
      reader.onload = () => {
        img.onerror = () => reject(new Error("Couldn't read that image."));
        img.onload = () => {
          let { width, height } = img;
          if (width > maxDim || height > maxDim) {
            const scale = maxDim / Math.max(width, height);
            width = Math.round(width * scale);
            height = Math.round(height * scale);
          }
          const canvas = document.createElement("canvas");
          canvas.width = width; canvas.height = height;
          canvas.getContext("2d").drawImage(img, 0, 0, width, height);
          canvas.toBlob(
            blob => blob ? resolve({ blob, dataUrl: canvas.toDataURL("image/jpeg", quality) }) : reject(new Error("Couldn't process that image.")),
            "image/jpeg", quality
          );
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  // ---------- gradient / photo tile art ----------
  function paintTile(el, product) {
    if (!product) return;
    const cover = (product.images && product.images.length) ? product.images[0] : null;
    if (cover) {
      el.innerHTML = `<img src="${cover}" alt="${product.name}" style="width:100%;height:100%;object-fit:cover;position:absolute;inset:0;">
        <div class="sheen"></div>`;
      el.style.background = "#e4decd";
      return;
    }
    if (!product.palette) return;
    const [a, b, c] = product.palette;
    const angle = product.angle || 120;
    el.innerHTML = `
      <div class="pour" style="position:absolute;inset:-25%;
        background:
          radial-gradient(circle at 25% 30%, ${a}cc, transparent 55%),
          radial-gradient(circle at 75% 70%, ${c}bb, transparent 60%),
          linear-gradient(${angle}deg, ${a}, ${b} 55%, ${c});
        "></div>
      <div class="sheen"></div>`;
    el.style.background = b;
  }
  async function renderTilesOnPage() {
    const els = document.querySelectorAll("[data-art]");
    if (!els.length) return;
    const products = await getProducts();
    els.forEach(el => {
      const id = el.getAttribute("data-art");
      paintTile(el, products.find(p => p.id === id));
    });
  }

  // ---------- customer auth (still local — per-device accounts) ----------
  function getUsers() { return read(KEYS.users, []); }
  function getSession() { return read(KEYS.session, null); }
  function currentUser() {
    const s = getSession();
    if (!s) return null;
    return getUsers().find(u => u.email === s.email) || null;
  }
  async function signup(name, email, password) {
    const users = getUsers();
    email = email.trim().toLowerCase();
    if (users.some(u => u.email === email)) return { ok: false, error: "An account with that email already exists." };
    const passwordHash = await hash(password);
    const user = { name, email, passwordHash, createdAt: Date.now() };
    users.push(user);
    write(KEYS.users, users);
    write(KEYS.session, { email });
    return { ok: true };
  }
  async function login(email, password) {
    email = email.trim().toLowerCase();
    const users = getUsers();
    const user = users.find(u => u.email === email);
    if (!user) return { ok: false, error: "No account found with that email." };
    const h = await hash(password);
    if (h !== user.passwordHash) return { ok: false, error: "That password doesn't match." };
    write(KEYS.session, { email });
    return { ok: true };
  }
  function logout() { localStorage.removeItem(KEYS.session); }

  // ---------- cart (local — cart contents are per-device/per-visitor) ----------
  function getCart() { return read(KEYS.cart, []); }
  function cartCount() { return getCart().reduce((n, l) => n + l.qty, 0); }
  async function addToCart(id, qty = 1) {
    const product = await getProduct(id);
    const stock = product ? product.stock : Infinity;
    const cart = getCart();
    const line = cart.find(l => l.id === id);
    if (line) line.qty = Math.min(stock, line.qty + qty);
    else cart.push({ id, qty: Math.min(stock, qty) });
    write(KEYS.cart, cart);
  }
  function updateCartQty(id, qty) {
    let cart = getCart();
    if (qty <= 0) cart = cart.filter(l => l.id !== id);
    else cart.forEach(l => { if (l.id === id) l.qty = qty; });
    write(KEYS.cart, cart);
  }
  function removeFromCart(id) { write(KEYS.cart, getCart().filter(l => l.id !== id)); }
  function clearCart() { write(KEYS.cart, []); }
  async function cartLines() {
    const products = await getProducts();
    return getCart().map(l => ({ ...l, product: products.find(p => p.id === l.id) })).filter(l => l.product);
  }
  async function cartTotal() {
    const lines = await cartLines();
    return lines.reduce((sum, l) => sum + l.product.price * l.qty, 0);
  }

  // ---------- orders (server-backed) ----------
  async function getOrders() {
    const token = getAdminToken();
    const res = await fetch("/api/orders", { headers: { Authorization: "Bearer " + token } });
    if (!res.ok) return [];
    return res.json();
  }
  async function getOrder(id) {
    const res = await fetch("/api/orders/" + encodeURIComponent(id));
    if (!res.ok) return null;
    return res.json();
  }
  async function createOrder(customer) {
    const lines = await cartLines();
    if (!lines.length) return null;
    const res = await fetch("/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items: lines.map(l => ({ id: l.id, qty: l.qty })), customer })
    });
    if (!res.ok) return null;
    const order = await res.json();
    clearCart();
    return order;
  }
  async function setOrderStatus(id, status) {
    const token = getAdminToken();
    await fetch("/api/orders/" + encodeURIComponent(id) + "/status", {
      method: "PUT",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
      body: JSON.stringify({ status })
    });
  }
  async function ordersForEmail(email) {
    const res = await fetch("/api/orders/mine?email=" + encodeURIComponent(email));
    if (!res.ok) return [];
    return res.json();
  }

  // ---------- admin (server-backed) ----------
  function getAdminToken() { return localStorage.getItem(KEYS.adminSession); }
  function isAdmin() { return !!getAdminToken(); }
  async function adminLogin(password) {
    const res = await fetch("/api/admin/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password })
    });
    if (!res.ok) return false;
    const data = await res.json();
    localStorage.setItem(KEYS.adminSession, data.token);
    return true;
  }
  function adminLogout() {
    const token = getAdminToken();
    localStorage.removeItem(KEYS.adminSession);
    if (token) fetch("/api/admin/logout", { method: "POST", headers: { Authorization: "Bearer " + token } }).catch(() => {});
  }
  async function setAdminPassword(newPassword) {
    const token = getAdminToken();
    const res = await fetch("/api/admin/password", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
      body: JSON.stringify({ newPassword })
    });
    return res.ok;
  }

  // ---------- shared UI wiring ----------
  function money(n) { return "\u20b9" + Math.round(n).toLocaleString("en-IN"); }
  function updateNav() {
    const countEl = document.querySelector("[data-cart-count]");
    if (countEl) countEl.textContent = cartCount();
    const authEl = document.querySelector("[data-auth-link]");
    if (authEl) {
      const user = currentUser();
      if (user) { authEl.textContent = user.name.split(" ")[0]; authEl.href = "account.html"; }
      else { authEl.textContent = "Log in"; authEl.href = "login.html"; }
    }
    const toggle = document.querySelector(".mobile-toggle");
    const links = document.querySelector(".nav-links");
    if (toggle && links && !toggle._wired) {
      toggle._wired = true;
      toggle.addEventListener("click", () => links.classList.toggle("open"));
    }
  }

  function showEmailPickerModal(email) {
    let modal = document.getElementById("email-picker-modal");
    if (!modal) {
      modal = document.createElement("div");
      modal.id = "email-picker-modal";
      modal.className = "modal-backdrop";
      modal.innerHTML = `
        <div class="modal" style="max-width:420px;text-align:left;">
          <h3 style="margin-top:0;font-size:1.3rem;">Contact Studio</h3>
          <p style="color:#3a4a45;margin-bottom:18px;font-size:0.92rem;">Choose how to send email to <strong id="ep-target-email"></strong>:</p>
          <div style="display:flex;flex-direction:column;gap:10px;">
            <a id="ep-gmail" target="_blank" rel="noopener" class="btn btn-primary" style="justify-content:center;display:flex;align-items:center;gap:8px;">
              <span>🔴</span> Send via Gmail (Web)
            </a>
            <a id="ep-outlook-live" target="_blank" rel="noopener" class="btn btn-teal" style="justify-content:center;display:flex;align-items:center;gap:8px;">
              <span>🔵</span> Send via Outlook.com
            </a>
            <a id="ep-outlook-office" target="_blank" rel="noopener" class="btn btn-ghost" style="justify-content:center;display:flex;align-items:center;gap:8px;border:1px solid var(--teal-2);">
              <span>💼</span> Send via Outlook (Office 365)
            </a>
            <a id="ep-default" class="btn btn-ghost" style="justify-content:center;display:flex;align-items:center;gap:8px;">
              <span>💻</span> Open Default Desktop App
            </a>
            <button type="button" id="ep-copy" class="btn btn-ghost" style="justify-content:center;display:flex;align-items:center;gap:8px;">
              <span>📋</span> Copy Email Address
            </button>
          </div>
          <div style="margin-top:18px;text-align:right;">
            <button type="button" id="ep-close" class="btn btn-ghost btn-sm">Close</button>
          </div>
        </div>`;
      document.body.appendChild(modal);

      modal.querySelector("#ep-close").addEventListener("click", () => modal.classList.remove("open"));
      modal.addEventListener("click", (e) => { if (e.target === modal) modal.classList.remove("open"); });
    }

    const gmailUrl = `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(email)}`;
    const outlookLiveUrl = `https://outlook.live.com/mail/0/deeplink/compose?to=${encodeURIComponent(email)}`;
    const outlookOfficeUrl = `https://outlook.office.com/mail/deeplink/compose?to=${encodeURIComponent(email)}`;
    const mailtoUrl = `mailto:${email}`;

    modal.querySelector("#ep-target-email").textContent = email;
    modal.querySelector("#ep-gmail").href = gmailUrl;
    modal.querySelector("#ep-outlook-live").href = outlookLiveUrl;
    modal.querySelector("#ep-outlook-office").href = outlookOfficeUrl;
    modal.querySelector("#ep-default").href = mailtoUrl;

    const copyBtn = modal.querySelector("#ep-copy");
    copyBtn.innerHTML = `<span>📋</span> Copy Email Address`;
    copyBtn.onclick = () => {
      navigator.clipboard.writeText(email);
      copyBtn.innerHTML = `<span>✅</span> Copied to clipboard!`;
      setTimeout(() => { copyBtn.innerHTML = `<span>📋</span> Copy Email Address`; }, 2000);
    };

    modal.classList.add("open");
  }

  function setupMailtoLinks() {
    document.addEventListener("click", (e) => {
      const link = e.target.closest('a[href^="mailto:"]');
      if (!link) return;
      e.preventDefault();
      const href = link.getAttribute("href");
      const email = href.replace("mailto:", "").split("?")[0] || "sarinaquadri71@gmail.com";
      showEmailPickerModal(email);
    });
  }

  init();
  document.addEventListener("DOMContentLoaded", () => {
    renderTilesOnPage();
    updateNav();
    setupMailtoLinks();
  });

  return {
    getProducts, getProduct, saveProduct, deleteProduct, paintTile, renderTilesOnPage, resizeImage,
    signup, login, logout, currentUser, getSession,
    getCart, cartCount, addToCart, updateCartQty, removeFromCart, clearCart, cartLines, cartTotal,
    getOrders, getOrder, createOrder, setOrderStatus, ordersForEmail,
    isAdmin, adminLogin, adminLogout, setAdminPassword,
    money, updateNav
  };
})();
