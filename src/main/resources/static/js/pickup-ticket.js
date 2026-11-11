document.addEventListener('DOMContentLoaded', () => {
  const qr = document.getElementById('orderPickupQr');
  if (qr?.dataset.pickupCode) {
    try {
      if (typeof QRCode === 'undefined') throw new Error('QR unavailable');
      new QRCode(qr, {text: qr.dataset.pickupCode, width: 192, height: 192,
        colorDark: '#193c2d', colorLight: '#ffffff', correctLevel: QRCode.CorrectLevel.H});
    } catch {
      qr.textContent = 'QR을 표시하지 못했어요. 오른쪽 또는 아래의 수령 코드를 직원에게 알려 주세요.';
    }
  }
  document.getElementById('copyPickupCode')?.addEventListener('click', async () => {
    const code = document.getElementById('orderPickupCodeText');
    const status = document.getElementById('pickupCopyStatus');
    try {
      await navigator.clipboard.writeText(code.textContent.trim());
      status.textContent = '수령 코드를 복사했어요.';
    } catch {
      const range = document.createRange();
      range.selectNodeContents(code);
      const selection = window.getSelection();
      selection.removeAllRanges(); selection.addRange(range);
      status.textContent = '복사를 허용하지 않는 환경이에요. 선택된 코드를 직접 복사해 주세요.';
    }
  });
});
