document.addEventListener('DOMContentLoaded', () => {
  const rows = [...document.querySelectorAll('[data-pickup-status]')];
  const filters = [...document.querySelectorAll('[data-pickup-filter]')];
  document.querySelectorAll('[data-pickup-count]').forEach(counter => {
    const state = counter.dataset.pickupCount;
    counter.textContent = state === 'ALL' ? rows.length : rows.filter(row => row.dataset.pickupStatus === state).length;
  });
  const filter = button => {
    let count = 0;
    rows.forEach(row => {
      row.hidden = button.dataset.pickupFilter !== 'ALL' && row.dataset.pickupStatus !== button.dataset.pickupFilter;
      if (!row.hidden) count++;
    });
    filters.forEach(item => {
      const active = item === button;
      item.classList.toggle('is-active', active);
      item.setAttribute('aria-pressed', String(active));
    });
    document.getElementById('pickupFilterStatus').textContent = count + '건의 수령 내역을 표시하고 있어요.';
    document.getElementById('pickupFilterEmpty').hidden = count > 0 || rows.length === 0;
  };
  filters.forEach(button => button.addEventListener('click', () => filter(button)));
  if (filters.length) filter(filters[0]);
  const scanner = document.getElementById('qr-reader');
  if (scanner && !window.isSecureContext) {
    const hint = document.createElement('p');
    hint.className = 'ui-scanner-hint';
    hint.textContent = '이 주소에서는 카메라 사용이 제한될 수 있어요. localhost 또는 HTTPS에서 접속하거나 수령 코드로 조회해 주세요.';
    scanner.after(hint);
  }
});
