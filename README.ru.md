<p align="center">
  <img src="assets/logo-git.png" width="128" alt="Resona">
</p>

<h1 align="center">Resona</h1>

<p align="center">
  <a href="README.md">English</a> · Русский
</p>

<p align="center">
  Опенсорсный клиент SoundCloud для Android, версия для iOS в бете.<br>
  Без рекламы. Без обязательной регистрации.
  <a href="https://keepandroidopen.org" target="_blank" rel="noopener noreferrer">
    Resona поддерживает #KeepAndroidOpen
</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26+-3DDC84?logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/iOS-16+-000000?logo=apple&logoColor=white">
  <img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white">
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg">
</p>

## Скриншоты

<p align="center">
  <img src="screenshots/ru_main.png" width="220">
  <img src="screenshots/player_both.png" width="220">
  <img src="screenshots/ru_customization.png" width="220">
</p>

## Установка

Скачайте последний APK со страницы [GitHub Releases](https://github.com/savo-o/resona/releases/latest). Resona нет в Google Play, поэтому нужно разрешить установку из файлового менеджера или браузера.

Ещё можно следить за обновлениями автоматически через [Obtainium](https://github.com/ImranR98/Obtainium), просто укажите ему этот репозиторий.

### Версия для iOS (бета)

Скачайте IPA со страницы [GitHub Releases](https://github.com/savo-o/resona/releases/latest) (или более свежую сборку из [canary](https://github.com/savo-o/resona/releases/tag/canary)) и установите через [Sideloadly](https://sideloadly.io) (рекомендуем), [AltStore](https://altstore.io) или [SideStore](https://sidestore.io). IPA не подписан, в App Store Resona не появится. С бесплатным Apple ID подпись действует 7 дней, поэтому её нужно продлевать раз в неделю (все три умеют делать это автоматически).

Никогда не ставили приложения так? Вот [пошаговая инструкция по установке](https://savo-o.github.io/resona/ios.html).

Версия для iOS это отдельный порт Android-приложения, и обновляется она реже, поэтому её версия может отставать (например, `Resona-ios-1.7.ipa` в релизе 1.9). Исправления, когда SoundCloud что-то ломает, туда приходят, а новые функции сначала появляются на Android, и некоторые до iOS могут не дойти.

## Возможности

- **Открытый исходный код, без рекламы, без телеметрии.**
- Регистрация не нужна, войти можно, если хотите синхронизировать свои лайки
- Скачивание для офлайна, плюс слежение за папкой, которое само подхватывает локальные файлы
- Импорт треков прямо из чатов Telegram (только Android)
- Микс, который постоянно подкидывает новое от артистов, которых вы уже слушаете
- Тексты песен почти для чего угодно, даже для нишевых треков.
- Избранное и плейлисты, с мультивыбором и свайпами, чтобы управлять быстро
- Статистика прослушивания
- Свои цветовые темы и иконки приложения, динамический цвет, который подстраивается под обложку трека

## Сборка

Debug:

```bash
./gradlew assembleDebug
```

iOS (нужен macOS с Xcode): откройте `ios/iosApp/iosApp.xcodeproj` и запустите схему `iosApp`.

## Философия

Resona всегда будет бесплатной и с открытым исходным кодом. Никаких подписок, платных стен, телеметрии и аналитики. Никогда.

## Сообщество

Вопросы, баги, идеи: [t.me/resona_tg](https://t.me/resona_tg)

## История Звезд

<a href="https://www.star-history.com/?repos=savo-o%2Fresona&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=savo-o/resona&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=savo-o/resona&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=savo-o/resona&type=date&legend=top-left" />
 </picture>
</a>


## Лицензия

Проект распространяется под лицензией GNU General Public License v3.0.
Подробности в файле `LICENSE`.
