const BASE = (window.env && window.env.BACKEND_URL) || "/api";

async function req(path, opts = {}) {
  const r = await fetch(BASE + path, { headers: { "Content-Type": "application/json" }, ...opts });
  if (!r.ok) throw new Error("HTTP " + r.status);
  const ct = r.headers.get("content-type") || "";
  if (ct.includes("application/json")) return r.json();
  return r.text();
}

export const Api = {
  hasAccess: (uid) => req(`/ha/has-access?uid=${encodeURIComponent(uid)}`),
  cardRegisterByName: (uid, name) => req(`/ha/card/register-by-name?uid=${encodeURIComponent(uid)}&name=${encodeURIComponent(name)}`),
  cardScan: (uid) => req(`/ha/card/scan?uid=${encodeURIComponent(uid)}`, { method: "POST" }),
  nfcToggle: (uid, device) => req(`/ha/nfc/toggle?uid=${encodeURIComponent(uid)}&device=${encodeURIComponent(device)}`, { method: "POST" }),
  faceToggle: (face, device) => req(`/ha/face/toggle?face=${encodeURIComponent(face)}&device=${encodeURIComponent(device)}`, { method: "POST" }),
  devicesList: () => req(`/devices`),
  personnelList: () => req(`/personnel`),
  cardsByPerson: (personId) => req(`/cards/by-person/${personId}`),
  eventsRecent: () => req(`/events?limit=50`) // при необходимости добавить параметр
};

