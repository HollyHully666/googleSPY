# Фаза 1: Команды для настройки сервера (VPS)

Подключайся к серверу: `ssh root@YOUR_SERVER_IP` (или `ssh ubuntu@YOUR_SERVER_IP`).

---

## Шаг 1. Обновление системы и firewall

```bash
apt update && apt upgrade -y
ufw allow 22
ufw allow 80
ufw allow 443
ufw enable
```

Проверка: `ufw status` — должны быть 22, 80, 443 (allow).

---

## Шаг 2. Node.js v20 LTS

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
apt install -y nodejs
node -v
npm -v
```

Ожидаемо: `v20.x.x` и версия npm.

---

## Шаг 3. Git, Nginx, Certbot, PM2

```bash
apt install -y git nginx certbot python3-certbot-nginx
npm install -g pm2
```

Проверка: `nginx -v`, `certbot --version`, `pm2 -v`.

---

## Шаг 4. Nginx: конфиг до SSL

Замени `YOUR_DOMAIN.com` на свой домен.

```bash
nano /etc/nginx/sites-available/YOUR_DOMAIN.com
```

Вставь (подставь свой домен):

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN.com;
    location / {
        return 200 'ok';
        add_header Content-Type text/plain;
    }
}
```

Включи сайт и проверь конфиг:

```bash
ln -sf /etc/nginx/sites-available/YOUR_DOMAIN.com /etc/nginx/sites-enabled/
nginx -t
systemctl reload nginx
```

С локальной машины: `curl -I http://YOUR_DOMAIN.com` — должен быть ответ 200. Если нет — проверь A-запись домена (`dig YOUR_DOMAIN.com`) и firewall (80 открыт).

---

## Шаг 5. SSL (Let's Encrypt)

Снова подставь свой домен:

```bash
certbot --nginx -d YOUR_DOMAIN.com
```

Укажи email, согласись с условиями. Certbot сам изменит конфиг Nginx (добавит listen 443 и пути к сертификатам).

Проверка автообновления:

```bash
certbot renew --dry-run
```

Проверка HTTPS с локальной машины: открой в браузере `https://YOUR_DOMAIN.com` — должна быть страница (пока пустая или дефолтная Nginx).

---

## Шаг 6. Конфиг Nginx после Certbot (для Фазы 2)

После Фазы 2 тебе понадобится проксировать запросы на Node.js (порт 3000). Пример готового блока для добавления в тот же server (после того как certbot уже настроил SSL):

В файле `/etc/nginx/sites-available/YOUR_DOMAIN.com` будет что-то вроде:

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name YOUR_DOMAIN.com;
    ssl_certificate /etc/letsencrypt/live/YOUR_DOMAIN.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/YOUR_DOMAIN.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Сейчас (Фаза 1) достаточно того, что сделал certbot; этот блок понадобится, когда поднимешь `server.js` на порту 3000.

---

## Чеклист Фазы 1 (сервер)

- [ ] `node -v` → v18 или v20
- [ ] `nginx -t` → ok
- [ ] `curl -I https://YOUR_DOMAIN.com` с локальной машины → 200
- [ ] `certbot renew --dry-run` → успех
