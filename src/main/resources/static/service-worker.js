self.addEventListener('push', (event) => {
    let data = {};
    try { data = event.data ? event.data.json() : {}; } catch (_) { data = { body: event.data?.text() || '' }; }
    const title = data.title || '시흥한바구니 알림';
    const options = {
        body: data.body || '새로운 알림이 도착했습니다.',
        data: { url: data.url || '/notifications' },
        tag: data.tag || undefined,
        renotify: false
    };
    event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const target = new URL(event.notification.data?.url || '/notifications', self.location.origin);
    if (target.origin !== self.location.origin) return;
    event.waitUntil((async () => {
        const clientsList = await clients.matchAll({ type: 'window', includeUncontrolled: true });
        for (const client of clientsList) {
            if (new URL(client.url).origin === self.location.origin) {
                await client.navigate(target.href);
                return client.focus();
            }
        }
        return clients.openWindow(target.href);
    })());
});
