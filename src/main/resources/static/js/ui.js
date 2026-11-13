(function () {
  'use strict';
  document.addEventListener('DOMContentLoaded', function () {
    const replaceImage = image => {
      image.hidden = true;
      let fallback = image.parentElement.querySelector('[data-image-fallback]');
      if (!fallback) {
        fallback = document.createElement('div');
        fallback.className = 'ui-media-placeholder';
        fallback.textContent = '등록된 상품 사진 없음';
        image.parentElement.appendChild(fallback);
      }
      fallback.hidden = false;
    };
    document.querySelectorAll('img[data-product-image]').forEach(image => {
      image.addEventListener('error', () => replaceImage(image));
      if (image.complete && image.naturalWidth === 0) replaceImage(image);
    });
    document.querySelectorAll('.ui-nav').forEach(nav => {
      let selected = null;
      let longestPath = -1;
      nav.querySelectorAll('a').forEach(link => {
        link.removeAttribute('aria-current');
        const path = new URL(link.href, location.href).pathname;
        const matches = path === location.pathname || (path !== '/' && location.pathname.startsWith(path + '/'));
        if (matches && path.length > longestPath) {
          selected = link;
          longestPath = path.length;
        }
      });
      if (selected) selected.setAttribute('aria-current', 'page');
    });
  });
})();
