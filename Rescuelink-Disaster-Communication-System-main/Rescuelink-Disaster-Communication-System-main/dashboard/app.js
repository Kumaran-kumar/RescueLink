/* RescueLink rescuer dashboard (Phase 2).
   Polls /api/alerts every 4s, plots priority-coloured clustered markers, renders a
   sortable list, supports status actions, and a need-density heatmap toggle.

   Offline/degradation: if tiles fail to load, the base layer just shows grey — markers,
   list, and actions all keep working from the API (which is on the same host). */

const TOKEN = "demo-token"; // matches backend RESCUELINK_TOKEN default
const POLL_MS = 4000;
const AUTH = { "X-Auth-Token": TOKEN };

const TIER_COLOR = { CRITICAL: "#ff1744", HIGH: "#ffab00", MODERATE: "#00b0ff" };
const TIER_RANK = { CRITICAL: 0, HIGH: 1, MODERATE: 2 };

const map = L.map("map").setView([20.5937, 78.9629], 5); // India default view
const tiles = L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19, attribution: "© OpenStreetMap",
});
tiles.on("tileerror", () => showErr("Map tiles failed to load — markers still work."));
tiles.addTo(map);

const cluster = L.markerClusterGroup();
map.addLayer(cluster);
let heatLayer = null;
let heatOn = false;
let firstFit = true;

const errEl = document.getElementById("err");
function showErr(msg) { errEl.textContent = msg; errEl.style.display = "block"; }
function clearErr() { errEl.style.display = "none"; }

document.getElementById("heatToggle").addEventListener("change", (e) => {
  heatOn = e.target.checked;
  refresh(); // rebuild layers immediately
});

function fmtAgo(ms) {
  if (!ms) return "—";
  const s = Math.max(0, Math.floor((Date.now() - ms) / 1000));
  if (s < 60) return s + "s ago";
  if (s < 3600) return Math.floor(s / 60) + "m ago";
  return Math.floor(s / 3600) + "h ago";
}

function markerIcon(tier) {
  const color = TIER_COLOR[tier] || "#8fa0b8";
  return L.divIcon({
    className: "",
    html: `<div style="background:${color};width:18px;height:18px;border-radius:50%;
           border:2px solid #fff;box-shadow:0 0 6px ${color}"></div>`,
    iconSize: [18, 18], iconAnchor: [9, 9],
  });
}

function popupHtml(p) {
  return `<div style="min-width:200px">
    <div style="font-weight:700">${escapeHtml(p.senderName || "Unknown")} —
      <span style="color:${TIER_COLOR[p.priorityTier]}">${p.priorityTier}</span></div>
    <div style="font-size:12px;margin:4px 0">${escapeHtml(p.priorityReason || "")}</div>
    <div style="font-size:12px">Type: ${escapeHtml(p.emergencyType || "—")}</div>
    ${p.medicalNote ? `<div style="font-size:12px">Medical: ${escapeHtml(p.medicalNote)}</div>` : ""}
    ${p.bloodGroup ? `<div style="font-size:12px">Blood: ${escapeHtml(p.bloodGroup)}</div>` : ""}
    <div style="font-size:12px">Battery: ${p.batteryLevel ?? "—"}% · Hops: ${p.hopCount ?? 0}</div>
    <div style="font-size:12px">Status: <b>${p.status}</b> · seen ${fmtAgo(p.firstBridgedAt)}</div>
  </div>`;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

async function setStatus(uuid, status) {
  try {
    const r = await fetch(`/api/alerts/${uuid}/status`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...AUTH },
      body: JSON.stringify({ status }),
    });
    if (!r.ok) throw new Error(r.status);
    refresh();
  } catch (e) { showErr("Status update failed — retrying on next poll."); }
}

function renderList(features) {
  features.sort((a, b) => {
    const ta = TIER_RANK[a.properties.priorityTier] ?? 3;
    const tb = TIER_RANK[b.properties.priorityTier] ?? 3;
    if (ta !== tb) return ta - tb;                       // priority first
    return (b.properties.firstBridgedAt || 0) - (a.properties.firstBridgedAt || 0); // then recency
  });
  const list = document.getElementById("list");
  list.innerHTML = "";
  for (const f of features) {
    const p = f.properties;
    const c = f.geometry.coordinates;
    const div = document.createElement("div");
    div.className = "card";
    div.innerHTML = `
      <div class="row">
        <span class="name">${escapeHtml(p.senderName || "Unknown")}</span>
        <span class="tier ${p.priorityTier}">${p.priorityTier}</span>
      </div>
      <div class="reason">${escapeHtml(p.priorityReason || "")}</div>
      <div class="meta">${escapeHtml(p.emergencyType || "")} ·
        🔋${p.batteryLevel ?? "—"}% · ${p.hopCount ?? 0} hops ·
        <span class="status-chip">${p.status}</span> · ${fmtAgo(p.firstBridgedAt)}</div>
      <div class="actions">
        <button class="b-ack">Acknowledge</button>
        <button class="b-en">En route</button>
        <button class="b-res">Resolved</button>
      </div>`;
    div.querySelector(".b-ack").onclick = (e) => { e.stopPropagation(); setStatus(p.uuid, "ACKNOWLEDGED"); };
    div.querySelector(".b-en").onclick = (e) => { e.stopPropagation(); setStatus(p.uuid, "EN_ROUTE"); };
    div.querySelector(".b-res").onclick = (e) => { e.stopPropagation(); setStatus(p.uuid, "RESOLVED"); };
    div.onclick = () => map.setView([c[1], c[0]], 15);
    list.appendChild(div);
  }
}

function renderStats(features) {
  let active = 0, ackd = 0, resolved = 0;
  for (const f of features) {
    const s = f.properties.status;
    if (s === "RESOLVED") resolved++;
    else if (s === "ACKNOWLEDGED" || s === "EN_ROUTE") ackd++;
    else active++;
  }
  document.getElementById("s-active").textContent = active;
  document.getElementById("s-ackd").textContent = ackd;
  document.getElementById("s-resolved").textContent = resolved;
}

function renderMap(features) {
  cluster.clearLayers();
  if (heatLayer) { map.removeLayer(heatLayer); heatLayer = null; }

  if (heatOn) {
    const pts = features.map((f) => [f.geometry.coordinates[1], f.geometry.coordinates[0], 0.8]);
    heatLayer = L.heatLayer(pts, { radius: 35, blur: 25 }).addTo(map);
  } else {
    for (const f of features) {
      const c = f.geometry.coordinates;
      const m = L.marker([c[1], c[0]], { icon: markerIcon(f.properties.priorityTier) });
      m.bindPopup(popupHtml(f.properties));
      cluster.addLayer(m);
    }
  }

  if (firstFit && features.length) {
    const b = L.latLngBounds(features.map((f) => [f.geometry.coordinates[1], f.geometry.coordinates[0]]));
    map.fitBounds(b.pad(0.3));
    firstFit = false;
  }
}

async function refresh() {
  try {
    const r = await fetch("/api/alerts", { headers: AUTH });
    if (!r.ok) throw new Error(r.status);
    const geo = await r.json();
    const features = geo.features || [];
    clearErr();
    renderStats(features);
    renderList(features);
    renderMap(features);
  } catch (e) {
    showErr("Cannot reach backend — retrying…");
  }
}

refresh();
setInterval(refresh, POLL_MS);
