self.addEventListener('push', (event) => {
    // 서버가 어떤 내용을 보내더라도 공용 기기 잠금 화면에는 민감정보를 노출하지 않는다.
    const options = {
        body: '새 알림이 있어요. 로그인 후 확인해 주세요.',
        data: { url: '/notifications' },
        tag: 'hanbaguni-notification',
        renotify: false
    };
    event.waitUntil(self.registration.showNotification('시흥한바구니 알림', options));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const target = new URL('/notifications', self.location.origin);
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
