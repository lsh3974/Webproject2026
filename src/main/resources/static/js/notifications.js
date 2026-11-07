(() => {
    const csrfToken = document.querySelector('.notification-csrf-token')?.value
        || document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

    const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);

    function formatTime(value) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        const diff = Math.max(0, Date.now() - date.getTime());
        const minutes = Math.floor(diff / 60000);
        if (minutes < 1) return '방금 전';
        if (minutes < 60) return `${minutes}분 전`;
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return `${hours}시간 전`;
        const days = Math.floor(hours / 24);
        if (days < 7) return `${days}일 전`;
        return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
    }

    function iconFor(type) {
        const icons = {
            PAYMENT_COMPLETED: '💳', GROUP_SUCCEEDED: '🎉',
            GROUP_FAILED: '📢', REFUND_COMPLETED: '↩️',
            PICKUP_READY: '📦', PICKUP_COMPLETED: '✅'
        };
        return icons[type] || '🔔';
    }

    async function updateUnreadCounts() {
        const badges = document.querySelectorAll('.notification-unread-count');
        if (!badges.length) return;
        try {
            const response = await fetch('/notifications/api/unread-count', { credentials: 'same-origin' });
            if (!response.ok) return;
            const data = await response.json();
            const count = Number(data.count || 0);
            badges.forEach((badge) => {
                badge.textContent = count > 99 ? '99+' : String(count);
                badge.classList.toggle('d-none', count === 0);
            });
            document.querySelectorAll('.notification-unread-summary').forEach((element) => {
                element.textContent = count > 0 ? `읽지 않은 알림 ${count > 99 ? '99+' : count}개` : '새 알림이 없습니다';
            });
        } catch (_) { /* 알림 개수 조회 실패는 현재 화면을 막지 않습니다. */ }
    }

    const dropdown = document.getElementById('notificationDropdown');
    const dropdownList = dropdown?.querySelector('.notification-dropdown-list');
    const bellButton = document.querySelector('.notification-bell-button');
    let recentLoaded = false;
    let loadingRecent = false;

    async function loadRecent(force = false) {
        if (!dropdownList || loadingRecent || (recentLoaded && !force)) return;
        loadingRecent = true;
        dropdownList.setAttribute('aria-busy', 'true');
        dropdownList.innerHTML = '<div class="notification-dropdown-state">알림을 불러오는 중...</div>';
        try {
            const response = await fetch('/notifications/api/recent?limit=6', { credentials: 'same-origin' });
            if (!response.ok) throw new Error('알림을 불러오지 못했습니다.');
            const notifications = await response.json();
            if (!Array.isArray(notifications) || notifications.length === 0) {
                dropdownList.innerHTML = '<div class="notification-dropdown-state">아직 알림이 없습니다.</div>';
            } else {
                dropdownList.innerHTML = notifications.map((item) => `
                    <button type="button" class="notification-dropdown-item ${item.read ? '' : 'unread'}"
                            data-notification-id="${escapeHtml(item.id)}">
                        <span class="notification-dropdown-icon" aria-hidden="true">${iconFor(item.type)}</span>
                        <span class="notification-dropdown-copy">
                            <span class="notification-dropdown-title">${escapeHtml(item.title)}</span>
                            <span class="notification-dropdown-body">${escapeHtml(item.body)}</span>
                            <span class="notification-dropdown-time">${escapeHtml(formatTime(item.createdAt))}</span>
                        </span>
                        ${item.read ? '' : '<span class="notification-dropdown-new" aria-label="읽지 않음"></span>'}
                    </button>`).join('');
            }
            recentLoaded = true;
        } catch (error) {
            dropdownList.innerHTML = `<div class="notification-dropdown-state">${escapeHtml(error.message || '알림을 불러오지 못했습니다.')}</div>`;
        } finally {
            loadingRecent = false;
            dropdownList.setAttribute('aria-busy', 'false');
        }
    }

    const bellWrap = document.querySelector('.notification-bell-wrap');
    function setDropdownOpen(open) {
        if (!dropdown || !bellButton) return;
        dropdown.classList.toggle('is-open', open);
        bellButton.setAttribute('aria-expanded', String(open));
        if (open) loadRecent();
    }
    bellButton?.addEventListener('click', (event) => {
        event.stopPropagation();
        setDropdownOpen(!dropdown?.classList.contains('is-open'));
    });
    document.addEventListener('click', (event) => {
        if (bellWrap && !bellWrap.contains(event.target)) setDropdownOpen(false);
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            setDropdownOpen(false);
            bellButton?.focus();
        }
    });
    dropdownList?.addEventListener('click', async (event) => {
        const itemButton = event.target.closest('[data-notification-id]');
        if (!itemButton) return;
        const notificationId = itemButton.dataset.notificationId;
        itemButton.disabled = true;
        try {
            const headers = {};
            if (csrfToken) headers[csrfHeader] = csrfToken;
            const response = await fetch(`/notifications/api/${encodeURIComponent(notificationId)}/read`, {
                method: 'POST', credentials: 'same-origin', headers
            });
            if (!response.ok) throw new Error('알림을 읽음 처리하지 못했습니다.');
            const result = await response.json();
            recentLoaded = false;
            await updateUnreadCounts();
            window.location.assign(result.redirectUrl || '/notifications');
        } catch (error) {
            itemButton.disabled = false;
            itemButton.title = error.message || '잠시 후 다시 시도해주세요.';
        }
    });

    function urlBase64ToUint8Array(base64String) {
        const padding = '='.repeat((4 - base64String.length % 4) % 4);
        const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
        const raw = atob(base64);
        return Uint8Array.from([...raw].map((char) => char.charCodeAt(0)));
    }

    const status = document.getElementById('pushStatus');
    const enableButton = document.getElementById('enablePushButton');
    const disableButton = document.getElementById('disablePushButton');

    enableButton?.addEventListener('click', async () => {
        try {
            if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
                throw new Error('이 브라우저는 Web Push를 지원하지 않습니다.');
            }
            if (!csrfToken) throw new Error('보안 토큰을 찾을 수 없습니다. 페이지를 새로고침하세요.');
            if (Notification.permission === 'denied') {
                throw new Error('브라우저 알림 권한이 차단되어 있습니다. 브라우저 사이트 설정에서 허용해주세요.');
            }
            const permission = await Notification.requestPermission();
            if (permission !== 'granted') throw new Error('브라우저 알림 권한을 허용해야 합니다.');
            const registration = await navigator.serviceWorker.register('/service-worker.js');
            let subscription = await registration.pushManager.getSubscription();
            if (!subscription) {
                subscription = await registration.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: urlBase64ToUint8Array(enableButton.dataset.vapidPublicKey)
                });
            }
            const response = await fetch('/notifications/api/push-subscriptions', {
                method: 'POST', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
                body: JSON.stringify({ endpoint: subscription.endpoint, keys: subscription.toJSON().keys })
            });
            if (!response.ok) throw new Error('푸시 구독 등록에 실패했습니다. (' + response.status + ')');
            if (status) status.textContent = '이 브라우저의 푸시 구독이 등록되었습니다. 수신 설정에서 푸시 알림도 켜주세요.';
        } catch (error) {
            if (status) status.textContent = error.message || '푸시 설정 중 오류가 발생했습니다.';
        }
    });

    disableButton?.addEventListener('click', async () => {
        try {
            const registration = await navigator.serviceWorker.getRegistration();
            const subscription = await registration?.pushManager.getSubscription();
            if (!subscription) { if (status) status.textContent = '연결된 푸시 구독이 없습니다.'; return; }
            const endpoint = subscription.endpoint;
            await subscription.unsubscribe();
            const response = await fetch('/notifications/api/push-subscriptions?endpoint=' + encodeURIComponent(endpoint), {
                method: 'DELETE', credentials: 'same-origin', headers: { [csrfHeader]: csrfToken || '' }
            });
            if (!response.ok) throw new Error('서버의 구독 정보를 삭제하지 못했습니다.');
            if (status) status.textContent = '이 브라우저의 푸시 연결을 해제했습니다.';
        } catch (error) {
            if (status) status.textContent = error.message || '푸시 연결 해제에 실패했습니다.';
        }
    });

    updateUnreadCounts();
    window.setInterval(updateUnreadCounts, 30000);
})();
