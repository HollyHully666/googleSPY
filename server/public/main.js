(function () {
    'use strict';

    var socket = null;
    var map = null;
    var marker = null;

    var loginView = document.getElementById('login-view');
    var panelView = document.getElementById('panel-view');
    var loginForm = document.getElementById('login-form');
    var serverUrlInput = document.getElementById('server-url');
    var loginInput = document.getElementById('login');
    var passwordInput = document.getElementById('password');
    var loginError = document.getElementById('login-error');
    var statusEl = document.getElementById('status');
    var logoutBtn = document.getElementById('logout-btn');
    var btnGps = document.getElementById('btn-gps');
    var gpsMapEl = document.getElementById('gps-map');
    var gpsTextEl = document.getElementById('gps-text');

    function setServerUrlDefault() {
        if (!serverUrlInput.value.trim()) {
            serverUrlInput.value = window.location.origin || 'https://big-brother.pro';
        }
    }

    function showLogin(err) {
        panelView.classList.add('hidden');
        loginView.classList.remove('hidden');
        loginError.textContent = err || '';
        loginError.classList.toggle('hidden', !err);
        if (socket) {
            socket.disconnect();
            socket = null;
        }
        if (map) {
            map.remove();
            map = null;
            marker = null;
        }
    }

    function showPanel() {
        loginView.classList.add('hidden');
        panelView.classList.remove('hidden');
        loginError.classList.add('hidden');
    }

    function setStatus(text, className) {
        statusEl.textContent = text;
        statusEl.className = 'status' + (className ? ' ' + className : '');
    }

    function initMap(lat, lon) {
        if (map) {
            map.setView([lat, lon], map.getZoom());
            if (marker) marker.setLatLng([lat, lon]);
            else marker = L.marker([lat, lon]).addTo(map);
            return;
        }
        map = L.map('gps-map').setView([lat, lon], 15);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(map);
        marker = L.marker([lat, lon]).addTo(map);
    }

    function onGpsData(data) {
        var lat = data.lat;
        var lon = data.lon;
        var err = data.error;
        if (err) {
            gpsTextEl.textContent = 'Ошибка: ' + err;
            return;
        }
        gpsTextEl.textContent = lat + ', ' + lon;
        initMap(lat, lon);
    }

    function connect(serverUrl, login, password) {
        setStatus('Подключение…', 'connecting');
        var url = serverUrl.replace(/\/$/, '');
        socket = io(url, {
            query: { login: login, password: password },
            transports: ['websocket', 'polling']
        });

        socket.on('connect', function () {
            setStatus('Подключено', 'connected');
        });

        socket.on('connect_error', function (err) {
            var msg = err.message || 'подключение не удалось';
            setStatus('Ошибка: ' + msg, 'error');
            if (msg.toLowerCase().indexOf('auth') !== -1) {
                setTimeout(function () { showLogin(msg); }, 1500);
            }
        });

        socket.on('disconnect', function (reason) {
            setStatus(reason === 'io server disconnect' ? 'Отключено' : 'Разрыв соединения', 'disconnected');
        });

        socket.on('gps_data', onGpsData);

        btnGps.onclick = function () {
            gpsTextEl.textContent = 'Запрос…';
            socket.emit('request_gps');
        };
    }

    loginForm.onsubmit = function (e) {
        e.preventDefault();
        setServerUrlDefault();
        var serverUrl = serverUrlInput.value.trim();
        var login = loginInput.value.trim();
        var password = passwordInput.value;
        if (!serverUrl || !login || !password) {
            loginError.textContent = 'Заполните все поля';
            loginError.classList.remove('hidden');
            return;
        }
        loginError.classList.add('hidden');
        loginForm.querySelector('button[type="submit"]').disabled = true;
        connect(serverUrl, login, password);
        showPanel();
        loginForm.querySelector('button[type="submit"]').disabled = false;
    };

    logoutBtn.onclick = function () {
        showLogin();
    };

    setServerUrlDefault();
})();