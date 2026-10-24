document.addEventListener('submit', function (event) {
    const message = event.target.dataset.confirm;
    if (message && !window.confirm(message)) event.preventDefault();
});

// Fixed positioning lets table menus escape the responsive table's overflow.
document.querySelectorAll('[data-table-actions]').forEach(function (toggle) {
    new bootstrap.Dropdown(toggle, {
        popperConfig: function (config) {
            return { ...config, strategy: 'fixed' };
        }
    });
});
