// Офлайн-кэш: страница работает без интернета после первого открытия.
// При изменении файлов увеличьте версию, чтобы телефоны подхватили обновление.
const CACHE = "dks-obhod-v18";
const FILES = ["./", "index.html", "manifest.webmanifest", "apple-touch-icon.png", "icon-192.png", "icon-512.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
// Сначала сеть (чтобы получать обновления); нет связи или ответ дольше 3 с — кэш.
const fromCache = req => caches.match(req, { ignoreSearch: true }).then(r => r || caches.match("index.html"));
self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  const net = fetch(e.request).then(res => {
      if (res && (res.ok || res.type === "opaque")) {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(e.request, copy));
      }
      return res;
    });
  const timeout = new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), 3000));
  e.respondWith(
    Promise.race([net, timeout]).catch(() => fromCache(e.request).then(r => r || net))
  );
});
