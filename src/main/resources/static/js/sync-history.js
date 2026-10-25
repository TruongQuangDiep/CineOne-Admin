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
    function notice(message, type = 'info') {
        feedback.className = 'alert alert-' + type;
        feedback.textContent = message;
    }
    async function request(url, options) {
        const response = await fetch(url, { ...options, headers: { Accept: 'application/json' } });
        if (response.redirected || !response.headers.get('content-type')?.includes('application/json'))
            throw new Error('Không thể đọc phản hồi. Hãy kiểm tra kết nối và phiên đăng nhập.');
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.');
        return body;
    }
    function watch(id) {
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
                    badge.textContent = labels[log.status] || log.status;
                    badge.className = 'badge text-bg-' + (log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'danger' : 'warning');
                    row.querySelector('[data-log-summary]').textContent = log.summary;
                }
                if (log.status !== 'PROCESSING') {
                    watched.delete(id);
                    // Refresh options, pagination and rows while preserving the current filters.
                    if (activeId === id) {
                        sessionStorage.removeItem(key);
                        sessionStorage.setItem(key + ':result', log.providerName + ': ' + labels[log.status] + '. ' + log.summary);
                    }
                    window.location.reload();
                    return;
                }
                if (activeId === id) notice(log.providerName + ': Đang đồng bộ…');
                window.setTimeout(poll, 2000);
            } catch (error) {
                notice(error.message + ' Hệ thống sẽ thử kiểm tra trạng thái lại; bạn chưa cần bấm đồng bộ lần nữa.', 'warning');
                window.setTimeout(poll, 10000);
            }
        }
        poll();
    }
    const result = sessionStorage.getItem(key + ':result');
    if (result) { notice(result); sessionStorage.removeItem(key + ':result'); }
    if (activeId) watch(activeId);
    document.querySelectorAll('[data-sync-status="PROCESSING"]').forEach(row => watch(row.dataset.syncLog));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (submit.disabled) return;
        submit.disabled = true;
        document.getElementById('sync-start-error').textContent = '';
        try {
            const log = await request(form.action, { method: 'POST', body: new FormData(form) });
            activeId = String(log.id);
            sessionStorage.setItem(key, activeId);
            selection.selectedOptions[0].disabled = true;
            bootstrap.Modal.getInstance(document.getElementById('sync-start-modal')).hide();
            notice('Đã tiếp nhận yêu cầu. Đang đồng bộ…');
            watch(activeId);
        } catch (error) {
            document.getElementById('sync-start-error').textContent = error.message;
        } finally { submit.disabled = false; }
    });
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
    document.getElementById('sync-filter').addEventListener('submit', event => {
        const from = document.getElementById('from-date');
        const to = document.getElementById('to-date');
        to.setCustomValidity(from.value && to.value && from.value > to.value ? 'Ngày kết thúc phải từ ngày bắt đầu trở đi.' : '');
        if (!to.checkValidity()) { event.preventDefault(); to.reportValidity(); }
    });
    document.getElementById('to-date').addEventListener('input', event => event.target.setCustomValidity(''));
    document.getElementById('from-date').addEventListener('input', () => document.getElementById('to-date').setCustomValidity(''));
});
