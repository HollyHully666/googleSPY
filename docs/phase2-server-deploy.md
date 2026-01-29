# Фаза 2: Деплой backend на VPS

**Важно:** Устройство (Android) и веб-интерфейс подключаются к серверу по домену **https://big-brother.pro**. Сервер должен быть развёрнут на VPS, к которому привязан этот домен.

---

## Как залить /server на VPS и запустить

### Что должно быть на VPS

- Ubuntu (или другой Linux), доступ по SSH.
- Установлены: **Node.js 18+**, **npm**, **PM2**, **Nginx**, **certbot** (SSL для big-brother.pro уже получен в Фазе 1).
- Домен **big-brother.pro** указывает A-записью на IP этого VPS.

Замени в командах ниже:
- `root` или `ubuntu` — твой пользователь на VPS;
- `big-brother.pro` или IP сервера — хост для SSH.

---

### Шаг 1. Залить папку server на VPS

**Вариант А — с твоего ПК через SCP (PowerShell или CMD):**

```powershell
# Из корня проекта AndroidSPY (где лежит папка server)
scp -r server root@big-brother.pro:~/
```

Или если логинишься по ключу и пользователь не root:

```powershell
scp -r server ubuntu@big-brother.pro:~/
```

Папка `server` окажется на VPS в домашнем каталоге: `~/server`.

**Вариант Б — через Git (если проект в репозитории):**

На VPS:

```bash
ssh root@big-brother.pro
git clone https://github.com/ТВОЙ_ЛОГИН/AndroidSPY.git
# или git pull, если уже клонировал
```

Тогда путь к серверу будет что-то вроде `~/AndroidSPY/server`.

**Вариант В — архивом:**

На своём ПК в папке проекта:

```powershell
# Создать архив (в PowerShell, из папки где лежит server)
Compress-Archive -Path server -DestinationPath server.zip
scp server.zip root@big-brother.pro:~/
```

На VPS:

```bash
unzip server.zip
# получится папка server в домашнем каталоге
```

---

### Шаг 2. На VPS: установить зависимости и создать .env

Подключись по SSH и перейди в папку server:

```bash
ssh root@big-brother.pro
cd ~/server
# или cd ~/AndroidSPY/server — если заливал через Git
```

Создай `.env` из примера и задай логин/пароль (те же, что будешь вводить в вебе и в приложении):

```bash
cp .env.example .env
nano .env
```

В `.env` должно быть (подставь свои значения):

```
PORT=3000
LOGIN=твой_логин
PASSWORD=твой_пароль
```

Сохрани (в nano: Ctrl+O, Enter, Ctrl+X).

Установи зависимости и запусти через PM2:

```bash
npm install --production
pm2 start server.js --name device-control
pm2 save
pm2 startup
```

Последняя команда выведет инструкцию вроде `sudo env PATH=... pm2 startup` — выполни её, чтобы приложение поднималось после перезагрузки сервера.

Проверь, что процесс запущен:

```bash
pm2 status
pm2 logs device-control
```

Сервер слушает порт 3000 только локально; снаружи к нему идут через Nginx.

---

### Шаг 3. Nginx (прокси на big-brother.pro)

Если Nginx для big-brother.pro ещё не настроен:

```bash
sudo nano /etc/nginx/sites-available/big-brother.pro
```

Вставь содержимое из `docs/phase2-nginx-socketio.conf` (уже с big-brother.pro и путями к SSL). Сохрани.

Включи сайт и перезагрузи Nginx:

```bash
sudo ln -sf /etc/nginx/sites-available/big-brother.pro /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Если certbot делал сертификат для big-brother.pro, пути к сертификатам уже будут правильные (`/etc/letsencrypt/live/big-brother.pro/...`).

---

### Если curl пишет: SSL certificate problem: self-signed certificate

Значит для **big-brother.pro** ещё нет сертификата Let's Encrypt (или Nginx подхватил самоподписанный). Нужно один раз получить сертификат через certbot.

**На VPS выполни:**

1. **Проверить, есть ли уже сертификат:**
   ```bash
   sudo ls -la /etc/letsencrypt/live/big-brother.pro/
   ```
   Если папки нет или там пусто — сертификата нет, переходи к шагу 2.

2. **Установить certbot (если ещё не стоит):**
   ```bash
   sudo apt update
   sudo apt install -y certbot
   ```

3. **Временно остановить Nginx** (чтобы порт 80 был свободен для проверки домена):
   ```bash
   sudo systemctl stop nginx
   ```

4. **Получить сертификат** (домен big-brother.pro должен указывать A-записью на IP этого сервера):
   ```bash
   sudo certbot certonly --standalone -d big-brother.pro
   ```
   Введи email, согласись с условиями. Certbot создаст файлы в `/etc/letsencrypt/live/big-brother.pro/`.

5. **Запустить Nginx снова:**
   ```bash
   sudo systemctl start nginx
   sudo nginx -t
   ```

6. **Проверка:**
   ```bash
   curl https://big-brother.pro/health
   ```
   Должно вернуть `ok` без ошибки SSL.

Если ошибка остаётся — убедись, что в конфиге Nginx указаны именно эти пути:
- `ssl_certificate /etc/letsencrypt/live/big-brother.pro/fullchain.pem;`
- `ssl_certificate_key /etc/letsencrypt/live/big-brother.pro/privkey.pem;`
и перезагрузи Nginx: `sudo nginx -t && sudo systemctl reload nginx`.

---

### Если порт 443 занят (xray/VPN и т.п.)

Если Nginx не стартует из‑за `bind() to 0.0.0.0:443 failed` (порт 443 занят, например xray), используй **порт 8443** для HTTPS.

**На VPS:**

1. Заменить конфиг big-brother.pro на вариант для 8443:
   ```bash
   sudo nano /etc/nginx/sites-available/big-brother.pro
   ```
   Вставь содержимое из **`docs/phase2-nginx-port8443.conf`** (Nginx слушает 8443, редирект с 80 на `https://big-brother.pro:8443`). Сохрани.

2. Открыть порт 8443 в файрволе:
   ```bash
   sudo ufw allow 8443/tcp
   sudo ufw reload
   ```

3. Запустить Nginx:
   ```bash
   sudo nginx -t && sudo systemctl start nginx
   ```

4. Проверка:
   ```bash
   curl https://big-brother.pro:8443/health
   ```
   Должно вернуть `ok`.

**Дальше везде используй адрес с портом:**  
**https://big-brother.pro:8443** — в браузере и в приложении на телефоне (URL сервера: `https://big-brother.pro:8443`).

---

### Шаг 4. Проверка

- На VPS или с любого ПК: `curl https://big-brother.pro/health` (или `curl https://big-brother.pro:8443/health` при варианте на 8443) — в ответ должно быть `ok`.
- В браузере открой **https://big-brother.pro** (или **https://big-brother.pro:8443**) — форма логина; введи LOGIN и PASSWORD из `.env`.
- В приложении на телефоне укажи тот же URL (с портом :8443, если используешь его) и те же логин/пароль.

---

## Локальная проверка (опционально)

1. В `server/` скопировать `.env.example` в `.env` и задать `LOGIN`, `PASSWORD`.
2. Установить зависимости и запустить:
   ```bash
   cd server
   npm install
   npm start
   ```
3. Открыть http://localhost:3000 — статика из `public/` и `/health` для отладки.
