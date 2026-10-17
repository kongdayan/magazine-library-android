<div align="center">
<img src="docs/images/icon.svg" width="88" alt="Magazine Library" />

# Magazine Library

雑誌は PDF で届きます。スマートフォンなら、オフラインで読めるはずです。

[![CI](https://github.com/kongdayan/magazine-library-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kongdayan/magazine-library-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kongdayan/magazine-library-android)](https://github.com/kongdayan/magazine-library-android/releases/latest)
[![MIT](https://img.shields.io/badge/license-MIT-14675d)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-1d2628?logo=android)](https://github.com/kongdayan/magazine-library-android/releases/latest)
[![Languages](https://img.shields.io/badge/UI-5%20languages-eab68a)](docs/ARCHITECTURE.md#localization)

[English](README.md) · [简体中文](README.cn.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Русский](README.ru.md)

</div>

**雑誌サイトを閲覧し、PDF をダウンロードして、オフラインで読むための、小さくローカルな Android クライアントです。**

電車の中で1冊読むためだけに、ブラウザのタブとダウンロードフォルダと別の PDF ビューアを行き来するのはやめにしたかった。Magazine Library は、サイトのセッションを WebView に保ち、開いたページをネイティブのコントロールに変換し、ダウンロードをバックグラウンドで進めて、読書位置を保存したリーダーで結果を開きます。アカウントなし、広告なし、アクセス解析なし、クラウドなし。

- **ウェブの雑然さに邪魔されずに見つける。** 検索、カテゴリ、発行月、表紙、詳細、同じ雑誌の参照、ページ内の並べ替えとページ送りは、すべてネイティブビューで描画されます。リクエストされるのは、開いたページだけです。
- **サイトに邪魔されないダウンロード。** ダウンローダーはホストごとにブラウザセッションを保ち、サイトのファイル準備ステップに従い、サーバーが許す場合にはレジュームし、PDF を検証してから Downloads または指定したフォルダに保存します。
- **フォルダではなく、ライブラリ。** 表紙、グリッドまたはリスト、雑誌ごとのグループ化、名前の変更、並べ替え、読書の進捗。ファイルは置いた場所にそのまま残ります。
- **きちんと読める。** 1ページずつのスワイプまたは連続スクロール、ピンチズーム、ページジャンプ、再起動しても保持される読書位置。
- **5つの言語、1つの小さな APK。** 英語、簡体中国語、日本語、韓国語、ロシア語。設定からでも、Android のアプリごとの言語設定からでも切り替えられます。AndroidX なし、ランタイムライブラリなし、広告なし。

<div align="center">
  <img src="docs/images/discover.jpg" width="250" alt="検索結果を表示した「見つける」画面" />
  <img src="docs/images/detail.jpg" width="250" alt="雑誌の詳細と PDF リンク" />
  <img src="docs/images/settings.jpg" width="250" alt="設定" />
  <br />
  <img src="docs/images/about.jpg" width="250" alt="About and author" />
  <img src="docs/images/language.jpg" width="250" alt="Language picker" />
</div>

画面は、5つの対応言語のうち簡体中国語で表示しています。1.3 の検証実行時に実機で撮影しました。詳しくは[検証範囲](docs/VERIFICATION.md)をご覧ください。

## インストール

Android 10（API 29）以降。

| デバイス | ダウンロード |
| --- | --- |
| 対応するすべてのデバイス（**推奨**） | [MagazineLibrary-universal.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-universal.apk) |
| arm64-v8a | [MagazineLibrary-arm64-v8a.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-arm64-v8a.apk) |
| armeabi-v7a | [MagazineLibrary-armeabi-v7a.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-armeabi-v7a.apk) |
| x86_64（エミュレータ） | [MagazineLibrary-x86_64.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-x86_64.apk) |

アプリにはネイティブライブラリが含まれていないため、どの APK も対応するすべてのデバイスで動作します。もっとも簡単なのは universal ビルドです。リリースは公開されている[コミュニティキー](signing/keystore.properties)で署名されているので、どのリリースも以前にサイドロードした版をそのまま上書き更新できます。ダウンロードしたファイルは、リリースページの `SHA256SUMS` で照合してください。Google Play では配布していません。

## 使い方

1. **見つける**を開き、検索するかカテゴリと月を選んで、雑誌を開きます。
2. PDF の項目をタップします。サイトがファイルを準備するまで少し時間がかかることがあります。ダウンロードはバックグラウンドで続き、**ダウンロード**に表示されます。
3. 完了したファイルは `Download / Magazine Library` または選択したフォルダに保存され、その号は**ライブラリ**に追加されます。
4. 表紙をタップして読みます。1ページ表示モードでは横にスワイプ、連続モードではスクロール、ページ番号をタップするとジャンプします。最後に読んだページは記憶されます。

サイトの確認ページが表示された場合は、アプリ内の**ウェブサイト**ビューで完了してください。これは意図的に手動のステップにしています。サイトのマークアップが変わったときも、ネイティブの抽出処理が更新されるまでは**ウェブサイト**ビューがそのまま使えます。

## 仕組み

システムの WebView がブラウザセッションになります。ユーザーが要求したページの読み込みが終わると、読み取り専用のスクリプトが現在の雑誌レスポンスを JSON として抽出し、ネイティブビューがそれを描画します。サイトをクロールしたり事前取得したりすることはありません。表紙も同じセッションを使います。ダウンローダーは対象 URL の Cookie を読み、リダイレクトは自前で処理し、可能なときは `Range` でレジュームし、`%PDF-` と `PdfRenderer` による検証を通過した後にだけファイルを公開します。

詳細：[アーキテクチャ](docs/ARCHITECTURE.md) · [プライバシー](PRIVACY.md) · [セキュリティポリシー](SECURITY.md)。

## ビルド

必要なもの：JDK 17、Android SDK Platform 36、Build Tools 35.0.0。Gradle 8.11.1 は wrapper に含まれています。

```sh
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:lintDebug
python3 scripts/check-translations.py
```

リリースビルドはコミュニティキーで署名されるため、サイドロードした版もそのまま上書き更新できます。独自のキーを使うには、`SIGNING_*` 環境変数を設定してください。[ビルドの注意点](docs/BUILDING.md) · [リリース手順](docs/RELEASING.md)。

## 言語

| 言語 | アプリの UI | README |
| --- | --- | --- |
| 英語（デフォルト） | `values/` | このファイル |
| 简体中文 | `values-zh-rCN/` | [README.cn.md](README.cn.md) |
| 日本語 | `values-ja/` | [README.ja.md](README.ja.md) |
| 한국어 | `values-ko/` | [README.ko.md](README.ko.md) |
| Русский | `values-ru/` | [README.ru.md](README.ru.md) |

翻訳は CI で検証されます。キー、プレースホルダー、翻訳対象外の文字列が英語のソースと一致している必要があります。新しい言語も歓迎します。詳しくは[コントリビューション](CONTRIBUTING.md#translation-rules)をご覧ください。

## プロジェクト

- [変更履歴](CHANGELOG.md)
- [アーキテクチャ](docs/ARCHITECTURE.md)
- [ビルド](docs/BUILDING.md) · [リリース](docs/RELEASING.md)
- [検証範囲](docs/VERIFICATION.md) · [過去の実機ログ](docs/verification/)
- [プライバシー](PRIVACY.md) · [セキュリティ](SECURITY.md) · [コントリビューション](CONTRIBUTING.md)

## 作者

**kongdayan · @kongdayan** —— 上海を拠点とする独立系開発者です。

プライバシーを大切にし、オフラインでも動く小さなツールを作っています。Magazine Library もそのひとつで、[WristBeam](https://github.com/kongdayan/wristbeam)（腕映）は Garmin ウォッチ向けのネイティブ macOS コンパニオンアプリです。

[GitHub](https://github.com/kongdayan) · [メール](mailto:19552144+kongdayan@users.noreply.github.com) · [Issue](https://github.com/kongdayan/magazine-library-android/issues)

## 免責事項

Magazine Library は `magazinelib.com` の独立した非公式クライアントであり、同サイトと提携関係にありません。本アプリはブラウザおよびダウンロードマネージャーであって、コンテンツのホストもインデックスも行いません。ダウンロードした内容には、提供元サイトの利用規約と著作権法が適用されます。合法的にアクセスできる資料にのみご利用ください。第三者のコンテンツがどのように利用されるかについて、作者は責任を負いません。

## ライセンス

[MIT](LICENSE) © 2026 kongdayan and contributors.
