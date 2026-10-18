<div align="center">
<img src="docs/images/icon.svg" width="88" alt="Magazine Library" />

# Magazine Library

Журналы приходят в виде PDF. Телефон должен позволять читать их офлайн.

[![CI](https://github.com/kongdayan/magazine-library-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kongdayan/magazine-library-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kongdayan/magazine-library-android)](https://github.com/kongdayan/magazine-library-android/releases/latest)
[![MIT](https://img.shields.io/badge/license-MIT-14675d)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-1d2628?logo=android)](https://github.com/kongdayan/magazine-library-android/releases/latest)
[![Languages](https://img.shields.io/badge/UI-5%20languages-eab68a)](docs/ARCHITECTURE.md#localization)

[English](README.md) · [简体中文](README.cn.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Русский](README.ru.md)

</div>

**Небольшой локальный Android-клиент: просмотр сайта журналов, загрузка PDF и чтение офлайн.**

Я хотел перестать жонглировать вкладками браузера, папкой загрузок и отдельной читалкой PDF ради
одного-единственного выпуска в электричке. Magazine Library хранит сессию сайта в WebView, превращает
запрошенную страницу в нативные элементы управления, скачивает файл в фоне и открывает результат в
читалке с сохранением прогресса. Без аккаунта, рекламы, аналитики и облака.

- **Обзор без веб-шелухи.** Поиск, категории, месяц выпуска, обложки, подробности, поиск по серии,
  сортировка внутри страницы и постраничная навигация — всё в нативных представлениях. Запрашивается
  только та страница, которую вы открыли.
- **Загрузки, которые переживут сайт.** Загрузчик сохраняет сессию браузера для каждого хоста,
  повторяет шаг подготовки файла, который выполняет сайт, возобновляет загрузку, когда сервер это
  разрешает, и проверяет PDF, прежде чем сохранить его в «Загрузки» или в выбранную вами папку.
- **Библиотека, а не просто папка.** Обложки, сетка или список, группировка по журналу,
  переименование, сортировка и прогресс чтения. Файлы остаются там, куда вы их положили.
- **Читайте как следует.** Листание по одной странице или непрерывная прокрутка, масштабирование
  двумя пальцами, переход к странице и позиция чтения, которая сохраняется после перезапуска.
- **Пять языков, один небольшой APK.** Английский, упрощённый китайский, японский, корейский и
  русский — переключаются в настройках или через системный выбор языка для приложения. Без AndroidX,
  без сторонних библиотек, без рекламы.

<div align="center">
  <img src="docs/images/discover.jpg" width="250" alt="Обзор с результатами поиска" />
  <img src="docs/images/detail.jpg" width="250" alt="Подробности журнала и ссылки на PDF" />
  <img src="docs/images/settings.jpg" width="250" alt="Настройки" />
  <br />
  <img src="docs/images/language.jpg" width="250" alt="Language picker" />
</div>

На скриншотах — упрощённый китайский, один из пяти доступных языков. Снято на реальном устройстве
во время проверки версии 1.3; см. [объём проверки](docs/VERIFICATION.md).

## Установка

Android 10 (API 29) или новее.

| Устройство | Загрузка |
| --- | --- |
| Любое поддерживаемое устройство (**рекомендуется**) | [MagazineLibrary-universal.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-universal.apk) |
| arm64-v8a | [MagazineLibrary-arm64-v8a.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-arm64-v8a.apk) |
| armeabi-v7a | [MagazineLibrary-armeabi-v7a.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-armeabi-v7a.apk) |
| x86_64 (эмуляторы) | [MagazineLibrary-x86_64.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-x86_64.apk) |

Приложение не содержит нативных библиотек, поэтому каждый APK работает на любом поддерживаемом
устройстве; универсальная сборка — самый простой выбор. Релизы подписаны публичным
[ключом сообщества](signing/keystore.properties), поэтому каждый следующий обновляет ранее
установленную вручную версию. Сверяйте загруженный файл с `SHA256SUMS` на странице релиза. Не
распространяется через Google Play.

## Как пользоваться

1. Откройте **Обзор**, выполните поиск или выберите категорию и месяц, затем откройте журнал.
2. Нажмите на запись PDF. Сайт может недолго готовить файл; загрузка продолжится в фоне и появится
   в **Загрузках**.
3. Готовый файл окажется в `Download / Magazine Library` или в выбранной вами папке, а выпуск
   появится в **Библиотеке**.
4. Нажмите на обложку, чтобы начать чтение. В режиме одной страницы листайте по горизонтали, в
   непрерывном режиме прокручивайте, нажмите на номер страницы, чтобы перейти. Последняя страница
   запоминается.

Если появится страница проверки сайта, пройдите её в разделе **Сайт** внутри приложения; это ручной
шаг по замыслу. Когда сайт меняет разметку, раздел «Сайт» остаётся запасным рабочим вариантом, пока
обновляется нативный извлекатель.

## Как это устроено

Системный WebView — это и есть сессия браузера. После загрузки запрошенной пользователем страницы
скрипт только для чтения извлекает ответ текущего журнала в JSON, а нативные представления
отрисовывают его. Приложение не сканирует сайт и ничего не предзагружает. Обложки используют ту же
сессию. Загрузчик читает cookie именно для этого URL, сам обрабатывает перенаправления, возобновляет
загрузку с помощью `Range`, когда это возможно, и публикует файл только после проверки `%PDF-` и
`PdfRenderer`.

Подробнее: [архитектура](docs/ARCHITECTURE.md) · [приватность](PRIVACY.md) ·
[политика безопасности](SECURITY.md).

## Сборка

Требования: JDK 17, Android SDK platform 36, Build Tools 35.0.0. Gradle 8.11.1 поставляется вместе с
wrapper.

```sh
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:lintDebug
python3 scripts/check-translations.py
```

Релизные сборки подписаны ключом сообщества, чтобы установленные вручную версии продолжали
обновляться. Задайте переменные окружения `SIGNING_*`, чтобы использовать собственный ключ.
[Заметки о сборке](docs/BUILDING.md) · [процесс релиза](docs/RELEASING.md).

## Языки

| Язык | Интерфейс | README |
| --- | --- | --- |
| Английский (по умолчанию) | `values/` | этот файл |
| 简体中文 | `values-zh-rCN/` | [README.cn.md](README.cn.md) |
| 日本語 | `values-ja/` | [README.ja.md](README.ja.md) |
| 한국어 | `values-ko/` | [README.ko.md](README.ko.md) |
| Русский | `values-ru/` | [README.ru.md](README.ru.md) |

Переводы проверяются в CI: ключи, плейсхолдеры и непереводимые строки должны совпадать с
английским источником. Новые языки приветствуются — см.
[участие в проекте](CONTRIBUTING.md#translation-rules).

## Проект

- [История изменений](CHANGELOG.md)
- [Архитектура](docs/ARCHITECTURE.md)
- [Сборка](docs/BUILDING.md) · [Релизы](docs/RELEASING.md)
- [Объём проверки](docs/VERIFICATION.md) · [исторические логи с устройств](docs/verification/)
- [Приватность](PRIVACY.md) · [Безопасность](SECURITY.md) · [Участие](CONTRIBUTING.md)

## Автор

**kongdayan** — независимый разработчик.

Я делаю небольшие инструменты, которые уважают приватность и работают офлайн. Magazine Library —
один из них; [WristBeam](https://github.com/kongdayan/wristbeam) (腕映) — нативный компаньон для
часов Garmin на macOS.

[GitHub](https://github.com/kongdayan) ·
[Обратная связь](https://github.com/kongdayan/magazine-library-android/issues)

## Отказ от ответственности

Magazine Library — независимый неофициальный клиент `magazinelib.com`, не связанный с этим сайтом.
Это браузер и менеджер загрузок; он не размещает и не индексирует контент. На загрузки
распространяются условия исходного сайта и законодательство об авторском праве. Используйте
приложение только для материалов, к которым у вас есть законный доступ. Автор не несёт
ответственности за то, как используется сторонний контент.

## Лицензия

[MIT](LICENSE) © 2026 kongdayan и участники проекта.
