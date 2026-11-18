document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('sync-start-form');
    if (!form) return;
    const base = form.action.replace(/\/start$/, '');
    const feedback = document.getElementById('sync-feedback');
    const submit = document.getElementById('sync-submit');
    const selection = document.getElementById('start-provider');
    const key = 'cineone-manual-sync:' + base;
    const watched = new Set();
    let activeId = sessionStorage.getItem(key);
    const labels = { SUCCESS: 'Thành công', FAILED: 'Thất bại', PROCESSING: 'Đang xử lý' };

    let timerInterval = null;

    // --- 1. BỘ ĐẾM THỜI GIAN TRÔI QUA (ELAPSED TIMER) ---
    function startTimer() {
        // Lưu mốc thời gian bắt đầu vào sessionStorage (nếu người dùng bấm F5 reload trang thì đồng hồ vẫn chạy tiếp chứ không bị reset về 0)
        let startTime = sessionStorage.getItem(key + ':start-time');
        if (!startTime) {
            startTime = Date.now();
            sessionStorage.setItem(key + ':start-time', startTime);
        } else {
            startTime = parseInt(startTime, 10);
        }

        if (timerInterval) clearInterval(timerInterval);

        function tick() {
            const elapsedSeconds = Math.max(0, Math.floor((Date.now() - startTime) / 1000));
            const mm = String(Math.floor(elapsedSeconds / 60)).padStart(2, '0');
            const ss = String(elapsedSeconds % 60).padStart(2, '0');
            const timerEl = document.getElementById('sync-elapsed-timer');
            if (timerEl) {
                timerEl.textContent = `${mm}:${ss}`;
            }
        }

        tick();
        timerInterval = setInterval(tick, 1000);
    }

    function stopTimer() {
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
        }
        sessionStorage.removeItem(key + ':start-time');
    }

    // --- 2. HIỂN THỊ GIAO DIỆN ĐANG ĐỒNG BỘ 2 DÒNG ---
    function showSyncing(providerName, subMessage) {
        feedback.className = 'alert alert-info border border-info-subtle shadow-sm p-3 mb-4';
        feedback.classList.remove('d-none');
        feedback.innerHTML = `
            <div class="d-flex align-items-center">
                <div class="spinner-border spinner-border-sm text-primary me-3 flex-shrink-0" style="width: 1.75rem; height: 1.75rem;" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <div class="flex-grow-1">
                    <!-- DÒNG TRÊN: Tiêu đề + Dấu ... hiệu ứng động + Thời gian trôi qua từng giây -->
                    <div class="d-flex align-items-center flex-wrap gap-2">
                        <span class="fw-bold fs-6 text-dark">
                            Đang đồng bộ ${providerName ? '[' + providerName + ']' : ''}<span class="sync-dots"></span>
                        </span>
                        <span class="badge bg-primary text-white rounded-pill px-2 py-1">
                            <i class="bi bi-clock-history me-1"></i><span id="sync-elapsed-timer">00:00</span>
                        </span>
                    </div>
                    <!-- DÒNG DƯỚI: Mô tả trạng thái chi tiết -->
                    <div class="text-muted small mt-1" id="sync-sub-status">
                        ${subMessage || 'Hệ thống đang kết nối và đồng bộ dữ liệu (phim, rạp, phòng, suất chiếu, ghế...). Quá trình này có thể mất vài chục giây, vui lòng không đóng trình duyệt.'}
                    </div>
                </div>
            </div>
        `;
        startTimer();
    }

    // --- 3. THÔNG BÁO THƯỜNG (KẾT QUẢ THÀNH CÔNG / THẤT BẠI) ---
    function notice(message, type = 'info') {
        stopTimer();
        feedback.className = 'alert alert-' + type + ' border shadow-sm p-3 mb-4';
        feedback.classList.remove('d-none');
        const icon = type === 'success' ? 'bi-check-circle-fill text-success' :
                     (type === 'danger' ? 'bi-exclamation-triangle-fill text-danger' : 'bi-info-circle-fill text-info');
        feedback.innerHTML = `
            <div class="d-flex align-items-center gap-2">
                <i class="bi ${icon} fs-5"></i>
                <div class="fw-semibold">${message}</div>
            </div>
        `;
    }

    async function request(url, options) {
        const response = await fetch(url, { ...options, headers: { Accept: 'application/json' } });
        if (response.redirected || !response.headers.get('content-type')?.includes('application/json'))
            throw new Error('Không thể đọc phản hồi. Hãy kiểm tra kết nối và phiên đăng nhập.');
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.');
        return body;
    }

    function watch(id, initialProviderName) {
        id = String(id);
        if (!/^\d+$/.test(id) || watched.has(id)) return;
        watched.add(id);

        async function poll() {
            try {
                const log = await request(base + '/' + id);
                const row = document.querySelector('[data-sync-log="' + id + '"]');
                if (row) {
                    row.dataset.syncStatus = log.status;
                    const badge = row.querySelector('[data-log-status]');
                    badge.innerHTML = log.status === 'PROCESSING' 
                        ? `<span class="spinner-border spinner-border-sm me-1" role="status"></span>Đang xử lý<span class="sync-dots"></span>`
                        : (labels[log.status] || log.status);
                    badge.className = 'badge text-bg-' + (log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'danger' : 'warning');
                    row.querySelector('[data-log-summary]').textContent = log.summary;
                }

                if (log.status !== 'PROCESSING') {
                    watched.delete(id);
                    stopTimer();
                    if (activeId === id) {
                        sessionStorage.removeItem(key);
                        sessionStorage.setItem(key + ':result', log.providerName + ': ' + labels[log.status] + '. ' + log.summary);
                        sessionStorage.setItem(key + ':result-type', log.status === 'SUCCESS' ? 'success' : 'danger');
                    }
                    window.location.reload();
                    return;
                }

                if (activeId === id) {
                    showSyncing(log.providerName || initialProviderName, log.summary);
                }
                window.setTimeout(poll, 2000);
            } catch (error) {
                notice(error.message + ' Hệ thống sẽ tự động thử lại; bạn chưa cần bấm đồng bộ lần nữa.', 'warning');
                window.setTimeout(poll, 10000);
            }
        }
        poll();
    }

    // Hiển thị kết quả của lần đồng bộ vừa kết thúc sau khi trang reload
    const result = sessionStorage.getItem(key + ':result');
    const resultType = sessionStorage.getItem(key + ':result-type') || 'info';
    if (result) {
        notice(result, resultType);
        sessionStorage.removeItem(key + ':result');
        sessionStorage.removeItem(key + ':result-type');
    }

    // Nếu đang có tiến trình đồng bộ dở dang khi vừa mở lại trang
    if (activeId) {
        showSyncing('', 'Đang theo dõi tiến trình đồng bộ...');
        watch(activeId, '');
    }
    document.querySelectorAll('[data-sync-status="PROCESSING"]').forEach(row => watch(row.dataset.syncLog, ''));

    // Bắt sự kiện khi bấm nút "Xác nhận đồng bộ"
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (submit.disabled) return;
        submit.disabled = true;
        document.getElementById('sync-start-error').textContent = '';

        const providerSelect = document.getElementById('start-provider');
        const selectedProviderName = providerSelect.selectedOptions[0]?.text?.replace(/ — Đang đồng bộ$/, '') || '';

        try {
            const log = await request(form.action, { method: 'POST', body: new FormData(form) });
            activeId = String(log.id);
            sessionStorage.setItem(key, activeId);
            selection.selectedOptions[0].disabled = true;
            bootstrap.Modal.getInstance(document.getElementById('sync-start-modal')).hide();
            
            // Hiển thị ngay hộp thông báo 2 dòng & bắt đầu đếm giây
            showSyncing(selectedProviderName, 'Đã gửi yêu cầu đồng bộ. Đang khởi tạo và thu thập dữ liệu...');
            watch(activeId, selectedProviderName);
        } catch (error) {
            document.getElementById('sync-start-error').textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    });

    // Xem chi tiết nhật ký trong Modal
    document.querySelectorAll('[data-log-detail]').forEach(button => button.addEventListener('click', async () => {
        const summary = document.getElementById('sync-detail-summary');
        const body = document.getElementById('sync-detail-body');
        summary.textContent = 'Đang tải…'; body.textContent = '';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('sync-detail-modal')).show();
        try {
            const log = await request(base + '/' + button.dataset.logDetail);
            summary.textContent = log.providerName + ' · ' + labels[log.status] + ' · ' + log.summary;
            body.textContent = log.detail || 'Chưa có thông điệp.';
        } catch (error) { summary.textContent = error.message; }
    }));

    // Bộ lọc ngày
    document.getElementById('sync-filter').addEventListener('submit', event => {
        const from = document.getElementById('from-date');
        const to = document.getElementById('to-date');
        to.setCustomValidity(from.value && to.value && from.value > to.value ? 'Ngày kết thúc phải từ ngày bắt đầu trở đi.' : '');
        if (!to.checkValidity()) { event.preventDefault(); to.reportValidity(); }
    });
    document.getElementById('to-date').addEventListener('input', event => event.target.setCustomValidity(''));
    document.getElementById('from-date').addEventListener('input', () => document.getElementById('to-date').setCustomValidity(''));
});