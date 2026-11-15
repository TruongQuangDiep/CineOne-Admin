// ==========================================================
// CineOne Admin - Script chính & Điều hướng SPA cho Sidebar
// ==========================================================

// 1. Xác nhận trước khi submit (nếu form có data-confirm)
document.addEventListener('submit', function (event) {
    const message = event.target.dataset.confirm;
    if (message && !window.confirm(message)) event.preventDefault();
});

// 2. Khởi tạo các helper cho bảng dữ liệu & hình ảnh
function initContentHelpers() {
    document.querySelectorAll('.catalog-image').forEach(function (image) {
        function fallback() { image.classList.add('image-failed'); }
        image.addEventListener('error', fallback);
        if (image.complete && image.naturalWidth === 0) fallback();
    });

    document.querySelectorAll('[data-table-actions]').forEach(function (toggle) {
        if (window.bootstrap && bootstrap.Dropdown) {
            new bootstrap.Dropdown(toggle, {
                popperConfig: function (config) {
                    return { ...config, strategy: 'fixed' };
                }
            });
        }
    });
}
initContentHelpers();

// ==========================================================
// 3. Cơ chế SPA: Giữ nguyên Sidebar 100%, chỉ hoán đổi nội dung
// ==========================================================
(function () {
    const sidebar = document.querySelector('.sidebar');
    const contentArea = document.querySelector('.content-area');
    if (!sidebar || !contentArea) return;

    // Cập nhật mục Active trên Sidebar
    function updateSidebarActive(targetPath) {
        sidebar.querySelectorAll('.menu-item').forEach(function (item) {
            const link = item.querySelector('a');
            if (link) {
                const linkPath = new URL(link.href, window.location.origin).pathname;
                if (linkPath === targetPath) {
                    item.classList.add('active');
                } else {
                    item.classList.remove('active');
                }
            }
        });
    }

    // Tải trang ngầm và chỉ thay thế phần nội dung bên phải
    async function loadPage(url, pushHistory = true) {
        const targetUrl = new URL(url, window.location.origin);
        
        try {
            // Hiệu ứng mờ nhẹ báo hiệu đang nạp
            contentArea.style.opacity = '0.6';

            const response = await fetch(targetUrl.href, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });

            // Nếu server yêu cầu đăng nhập lại (hết phiên)
            if (response.redirected && response.url.includes('/login')) {
                window.location.href = response.url;
                return;
            }

            if (!response.ok) {
                window.location.href = targetUrl.href;
                return;
            }

            const html = await response.text();
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');

            const newContent = doc.querySelector('.content-area');
            if (!newContent) {
                window.location.href = targetUrl.href;
                return;
            }

            // 1. Cập nhật Title trang trên tab trình duyệt
            if (doc.title) {
                document.title = doc.title;
            }

            // 2. CHỈ HOÁN ĐỔI NỘI DUNG CHÍNH (Sidebar đứng yên vĩnh viễn)
            contentArea.innerHTML = newContent.innerHTML;

            // 3. Cập nhật URL trên thanh địa chỉ
            if (pushHistory) {
                window.history.pushState(null, '', targetUrl.href);
            }

            // 4. Cập nhật class Active trên Sidebar
            updateSidebarActive(targetUrl.pathname);

            // 5. Kích hoạt lại các đoạn script nội bộ của trang con (nếu có)
            newContent.querySelectorAll('script').forEach(function (oldScript) {
                const newScript = document.createElement('script');
                Array.from(oldScript.attributes).forEach(function (attr) {
                    newScript.setAttribute(attr.name, attr.value);
                });
                newScript.textContent = oldScript.textContent;
                document.body.appendChild(newScript);
                newScript.remove();
            });

            // 6. Khởi tạo lại các component Bootstrap (dropdown, v.v.)
            initContentHelpers();

            // 7. Cuộn phần nội dung chính lên đầu
            window.scrollTo(0, 0);

        } catch (error) {
            console.error('SPA Error, fallback:', error);
            window.location.href = targetUrl.href; // Nếu có trục trặc thì tự động chuyển trang bình thường
        } finally {
            contentArea.style.opacity = '1';
        }
    }

    // Lắng nghe click trên các liên kết của Sidebar
    sidebar.addEventListener('click', function (event) {
        const link = event.target.closest('a');
        if (!link) return;

        // Bỏ qua nếu người dùng giữ Ctrl/Shift mở tab mới hoặc link ngoài
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
        if (link.target === '_blank') return;

        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;

        const targetUrl = new URL(link.href, window.location.origin);
        if (targetUrl.origin !== window.location.origin) return;

        event.preventDefault();

        // Chuyển highlight Active ngay lập tức trên Sidebar
        const menuItem = link.closest('.menu-item');
        if (menuItem) {
            sidebar.querySelectorAll('.menu-item').forEach(m => m.classList.remove('active'));
            menuItem.classList.add('active');
        }

        // Tải nội dung ngầm
        loadPage(targetUrl.href, true);
    });

    // Lắng nghe nút Back / Forward của trình duyệt
    window.addEventListener('popstate', function () {
        loadPage(window.location.href, false);
    });
})();