# Деплой веб-версии Жирпем на Vercel

Vercel размещает только сайт (папка `web`, Next.js). Android-приложение собирается отдельно в Android Studio.

## 1. Репозиторий
Залейте проект на GitHub/GitLab/Bitbucket. Файлы `.env.local`, `local.properties` и `node_modules` в репозиторий попадать не должны.

## 2. Новый проект в Vercel
1. Vercel -> Add New -> Project -> выберите репозиторий.
2. **Root Directory: `web`** (обязательно, иначе Vercel не найдёт сайт).
3. Framework Preset: Next.js (определится сам). Build Command, Output Directory и Install Command оставьте по умолчанию.

## 3. Переменные окружения
Settings -> Environment Variables, добавьте все переменные из `web/.env.example` (значения из вашего `web/.env.local`) для Production, Preview и Development.
Без них сайт соберётся, но будет работать с «заглушкой» вместо Firebase.

## 4. Firebase
Firebase Console -> Authentication -> Settings -> Authorized domains -> Add domain: ваш домен `*.vercel.app` (и свой домен, если подключите).
Правила Firestore для веб-версии те же, что и для приложения.

## 5. Деплой
Нажмите Deploy. Каждый push в основную ветку будет выкладываться автоматически, а ветки и pull request получают свои превью-адреса.

## 6. Что нужно настроить для Shorts, звуков и уведомлений
1. **Cloudinary:** пресет `ProfilePIC` (или свой в `NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET`) должен быть неподписанным и принимать фото, видео и аудио. На бесплатном тарифе видео до 100 МБ, поэтому запись в браузере идёт с битрейтом 2,5 Мбит/с (3 минуты около 56 МБ).
2. **Правила Firestore:** добавьте коллекции `shorts`, `sounds`, `shorts_comments` и `user_interests`. Вошедшим пользователям нужно чтение, создание, а владельцам ещё и удаление своих видео и звуков.
3. **Камера в браузере** работает только по https. Vercel выдаёт https автоматически.
4. **Ссылка из QR-кода:** в Android-приложении впишите адрес сайта в `ProfileLinks.WEB_BASE` (файл `ProfileCard.kt`), например `https://ваш-проект.vercel.app`. Тогда QR ведёт на `/u/ник`: на Android откроется приложение, на остальных устройствах профиль в вебе.
5. **Индексы Firestore** не нужны: все новые запросы простые, сортировка делается на устройстве.

## Если сборка упала
Откройте лог сборки (Deployments -> нужный деплой -> Build Logs) и пришлите ошибку.
