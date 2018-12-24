# アプリケーションのコンセプト

本アプリケーションは小型軽量のATOM端末における

「WIFIアクセスルータとしての使い勝手を向上させる」

事を最大目的にして設計しています。基本的にイベントドリブンで動作するので待機電力等の心配は要りません。

ポケットの中でテザリング切り替えが誤動作しないように、操作はPTTボタンだけとなります。

※画面タップのアクションはアプリが終了（UIが裏に隠れる）です

# アプリケーションの概要

unihertz Atom専用 テザリング 制御アプリケーションです。ATOM端末以外での使用は意味はありません。

※ソースコードレベルで他機種への応用は可能ですが、PTTボタン（HW KEY）を前提とした設計になっております

ATOMに「手軽なテザリングの切替」フォアグランドサービスを提供します。

・いつでもPTTボタンを押す事で切替画面が起動し、再度PTTを押す事でテザリングの状態を切替ます

・本アプリは画面ロック状態でも機能します（ロック解除はされません）

・休憩時間などの短時間利用時のテザリング切り忘れを回避する為の自動切断（５分、１５分）が可能です

# アプリ操作画面

本アプリの操作は

![demo](https://user-images.githubusercontent.com/30016234/50389197-a359a600-076a-11e9-9ebd-ba0394b69f54.PNG)

# アプリケーションのインストール

ReleaseからAtomTether.Apkをダウンリードしてインストールしてください

![install](https://user-images.githubusercontent.com/30016234/50396257-47b00c80-07ac-11e9-98c1-c459c3631a8f.PNG)

UIなどを変更したい方はSRCツリーを改造してAPKをビルドして下さい

本ソースコードはサービス層（atomTetheringServiceモジュール）とUI層は完全に独立した実装になっております。

APKをダウンロードしてインストールされる方は、ATOM（OS OREO）ではブラウザアプリ側にインストール権限が必要です

※大抵はインストール権限を持っていると思います。
※ストア以外の「野良アプリ」のインストールの権限は、従来の「セキュリティ設定」から「インストールを実行するアプリ」側に移っています。
※インストール権限を持たないアプリでのAPKインストールはできません。

# ATOM端末のシステム設定変更

本アプリを機能させる為には、下記の４点のシステム設定の変更が必須です

１．設定変更権限

テザリング設定にアクセスする為の権限です。インストール後に本アプリを起動させると自動的に権限取得画面に入ります
権限をONにして下さい

![SETTING1](https://user-images.githubusercontent.com/30016234/50396608-57305500-07ae-11e9-85ac-acccc82e3dfe.PNG)

BACKボタンで再度アプリ画面まで戻します

２．ユーザ補助設定

次に画面上のPTTボタンがグレー色の場合（ユーザ補助未設定）は、その画面ボタンをタップします

![SETTING2](https://user-images.githubusercontent.com/30016234/50397033-ef2f3e00-07b0-11e9-9937-422c05ccf05b.PNG)

BACKボタンで再度アプリ画面に戻るとPttボタン色が黄色に変化します（グレーの場合は設定変更を再度実行して下さい）

３．パワーセーブのホワイトリストに登録

本端末はインストール直後の初期状態はスリープするとアプリケーションがOSにより動作無効にされます（※２の設定などが元に戻される）
アプリ動作が無効にされると機能障害が発生して動作しませんので、このパワーセーブから本アプリを除外してやる必要があります。
具体的にはホワイトリストに本アプリを登録（初期時は未登録）してやる事になります。

アプリから抜けて標準の端末設定を起動して下さい

![SETTING3](https://user-images.githubusercontent.com/30016234/50397047-fe15f080-07b0-11e9-89ce-a87396609f5d.PNG)

これでアプリはスリープしても動作が継続されます。

※アプリをアンインストールしてもホワイトリストには残る仕様なようで、一度追加すると以降は変更不要です。
※ファーム更新時とかは再度確認してみてください。

４．PTTのショートカット設定

PTTボタンは画面消灯時にはアプリ側から見えなくなります。これでは画面オフ時にPTTボタンを押してもアプリには何も伝わりません。

これを解決する為にUnihertz ATOMのショートカット機能を使います。本機能を設定すればPTTの操作が画面消灯時でも検知できるようになります。

※具体的にはINTENTと呼ばれる信号を発信するようになります。アプリ側は本信号を受けて活動トリガにします

※アプリ画面を全面に出すトリガ（起動要因）としてショートカット信号を使いますので設定されてなければ使い勝手が非常に悪くなります

ショートカット設定で一番トップにあるCUSTOMを選択します

![SETTING4](https://user-images.githubusercontent.com/30016234/50397062-0f5efd00-07b1-11e9-9329-f4379a976c10.PNG)

設定は以上です


# アプリ側の設定

基本的に設定変更は不要ですが、場合によってオリジナルから変更して下さい。

画面を横にスワイプする事で設定画面が現れます。設定画面では画面タップしてもアプリは終了しません。

１．自動起動/手動起動の切替

端末再起動した場合に本アプリを自動起動するかしないかの設定です。
自動起動を選択しても、起動からアプリ起動までは結構な時間がかかります。再起動後にOSにより自動起動トリガが発動するのは「起動時のPIN入力」が終わった
あとになります。

２．フォアグランドサービスの有効/無効

フォアグランドサービスにするとステータスバーに通知が表示されるかわりに、OSによりサービス強制停止から除外されます。
（パワーマネージャによる停止は拒絶できませんのでホワイトリスト登録が必須になります）

フォアグランドサービスを無効にした場合は「OS側の都合」でアプリを機能停止させられる場合があります。

※本フォアグランドサービスの切替は、アプリ自体を強制再起動（サービスの作り替え）します

３．背景選択

初期背景は黒ですが、ここに任意の壁紙を指定する事ができます。
指定した映像データをATOM画面サイズにリサイズしてセットしますので、画像の縦横比がATOMにあった映像を指定して下さい。

※歪んで良いのであれば比率はどうでもよいです

# 通知の設定

フォアグランドサービスの出す通知はOSの設定画面でその方法を変える事ができます。

標準設定アプリ⇒　アプリ設定　⇒　Atom Service

を選ぶと、ユーザ操作（ユーザの意思）による通知レベル変更ができます。

・ヘッドアップ通知（画面上にポップアップ）する （消えるのに５秒かかるのでウザいです）

・通知時に任意の音を出す （やかましいです）

・通知自体を出さない　（これはステータスバーの通知自体を横にスライドさせても切替可能です）

初期通知状態はミドル（通知は出すが音は出さない）です。

アプリ側の初期値として「通知なし」にはできません（アプリが勝手に常駐サービスを起動する事をOS側は許しません）
