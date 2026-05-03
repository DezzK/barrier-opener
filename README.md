# Открыватель шлагбаумов

Android-приложение для головных устройств в автомобиле. Автоматически распознаёт подъезд к настроенному шлагбауму по координатам и направлению движения и предлагает позвонить на привязанный номер для открытия.

## Возможности

- Список шлагбаумов с координатами, телефоном и радиусом обнаружения.
- Двунаправленные и односторонние шлагбаумы (с проверкой направления подъезда).
- Попап подтверждения поверх любого приложения и поверх локскрина (звук + full-screen notification).
- Кнопка-FAB «Сохранить» в нижней части экрана редактирования, карта Yandex MapKit на пол-экрана.
- Работает без Google Play Services (платформенный `LocationManager`, GPS + сетевой провайдер).
- Тёмная и светлая темы (Material 3, фирменная жёлтая палитра).
- Адаптивная иконка (foreground + monochrome для Android 13+).

## Требования

- Android 9 (API 28) и новее.
- GPS на устройстве. Network-провайдер используется как страховка.
- Yandex MapKit API key — получите бесплатно в [Yandex Cloud](https://developer.tech.yandex.ru/).

## Локальная сборка

API-ключ MapKit можно положить в `local.properties` (он в `.gitignore`):

```properties
YANDEX_MAPKIT_API_KEY=ваш-ключ
```

либо передавать через переменную окружения. Для отладочной сборки:

```bash
./gradlew assembleDebug
```

Для подписанного релиза нужен `keystore.jks` в корне репозитория и переменная `KEY_PASSWORD`:

```bash
VERSION_NAME=v0.3.0 \
KEY_PASSWORD=пароль \
YANDEX_MAPKIT_API_KEY=ключ \
./gradlew assembleRelease
```

`VERSION_NAME` парсится в `versionCode` (`v0.3.1` → `301`). Готовый APK: `app/build/outputs/apk/release/barrier-opener-${VERSION_NAME}-release.apk`.

Установка на ГУ через ADB:

```bash
adb install -r app/build/outputs/apk/release/barrier-opener-v0.3.0-release.apk
```

Страница: <https://dezzk.github.io/barrier-opener/>.

## Лицензия

GPL-3.0 — см. [LICENSE](LICENSE).
