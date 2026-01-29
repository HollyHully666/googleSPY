# Фаза 1: Локальный чеклист (домен, Android, репозиторий)

## Домен

- В панели регистратора домена добавь **A-запись**: хост `@` (или поддомен) → IP твоего VPS.
- Подожди 5–30 минут, проверь: `nslookup YOUR_DOMAIN.com` или [dnschecker.org](https://dnschecker.org) — должен быть твой IP.

## Android-проект (Android Studio)

1. File → New → New Project.
2. Выбери **Empty Activity** (Compose).
3. Language: **Kotlin**, Build configuration: **Kotlin DSL** (или Groovy — по желанию).
4. Minimum SDK: **28**, Compile SDK / Target SDK: **34**.
5. Сохрани проект в папку репозитория, например: `AndroidSPY/android-app` (или отдельно — тогда не добавляй в этот репо).

После создания проверь в `android/app/build.gradle.kts`: `minSdk = 28`, `targetSdk = 34`, `compileSdk = 34`.

## Репозиторий (Git)

В корне проекта (где лежат `server/`, `development-plan.json`):

```bash
git init
git add .
git commit -m "chore: phase 1 infrastructure (server stub, docs, nginx examples)"
```

Если используешь GitHub (private):

```bash
git remote add origin https://github.com/YOUR_USER/AndroidSPY.git
git branch -M main
git push -u origin main
```

## Файлы, созданные на Фазе 1

| Путь | Назначение |
|------|------------|
| `docs/phase1-server-setup.md` | Команды для VPS (Node, Nginx, certbot, PM2) |
| `docs/phase1-nginx-example.conf` | Nginx до SSL (копировать на сервер) |
| `docs/phase1-nginx-after-ssl.conf` | Образец Nginx с SSL и proxy на 3000 (Фаза 2) |
| `docs/phase1-local-checklist.md` | Этот чеклист |
| `server/package.json` | Заготовка backend (main, version) |
| `.gitignore` | Исключения для Node, Android, .env |

На сервере создаёшь вручную: конфиг в `/etc/nginx/sites-available/`, симлинк в `sites-enabled/`; сертификаты в `/etc/letsencrypt/` (через certbot).
