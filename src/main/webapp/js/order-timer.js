(function () {
    'use strict';

    var panels = document.querySelectorAll('.pay-panel[data-remain]');
    if (!panels.length) return;

    function pad(n) {
        return n < 10 ? '0' + n : '' + n;
    }

    Array.prototype.forEach.call(panels, function (panel) {
        var remain = parseInt(panel.getAttribute('data-remain'), 10);
        if (isNaN(remain) || remain < 0) remain = 0;
        if (remain > 300) remain = 300;

        var deadline = Date.now() + remain * 1000;
        var countdown = panel.querySelector('.pay-countdown');
        var expired = false;
        var timer = null;

        function tick() {
            if (expired) return;
            var left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
            if (countdown) {
                countdown.textContent = pad(Math.floor(left / 60)) + ':' + pad(left % 60);
            }
            if (left <= 60) panel.classList.add('urgent');
            if (left <= 0) {
                expired = true;
                if (timer) clearInterval(timer);
                panel.classList.add('expired');
                if (countdown) countdown.textContent = '已超时';
                Array.prototype.forEach.call(panel.querySelectorAll('button'), function (btn) {
                    btn.disabled = true;
                });
                // 重新加载订单页会触发服务端释放过期锁座
                window.setTimeout(function () {
                    window.location.reload();
                }, 1500);
            }
        }

        tick();
        if (!expired) timer = window.setInterval(tick, 1000);
    });
})();
