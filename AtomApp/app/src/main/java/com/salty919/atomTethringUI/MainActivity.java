package com.salty919.atomTethringUI;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.salty919.atomTethringService.AtomService;
import com.salty919.atomTethringService.AtomStatus;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;

/*************************************************************************************************
 *
 *  ATOM UserInterface Class
 *
 *  PTTによるテザリング切り替えを提供するインターフェース層
 *
 *  本アプリケーションは常駐型サービスが主体であり本UI層は必要な場合にのみ生成される
 *
 * 　・サービスが起動していない場合に起動させる（初回アプリ起動時）
 * 　・サービスからの依頼でユーザに回線情報を提示する
 *
 *  本アクティビティはいつ落とされてもサービス機能に影響が出ない構造にすべきである
 *  ユーザの意図しないタイミングでのUI起動はない（他アプリから勝手に画面が切り替わらない）
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public class MainActivity extends AppCompatActivity implements ServiceConnection,
        AtomStatus.observer,AtomService.Listener,ViewPagerAdapter.Listener,
        AtomPreference.callBack
{
    private static String TAG;

    @SuppressWarnings("SameParameterValue")
    private String getClassName(Class<?> cls) { return cls.getName(); }

    // バンドルキー
    public static final String    KEY_boot              = "bootUp";

    public final int   RESULT_PICK_IMAGE                = 1;

    // lock
    private final Object                        mBindLock           = new Object();

    private ViewPager                           mPager              = null;
    private ViewPagerAdapter                    mAdapter            = null;
    // service
    private AtomService                         mService            = null;
    private boolean                             mIsServiceBind      = false;

    // UI handler
    private final Handler                       mHandler            = new Handler();

    // Status
    private AtomStatus                          mStatus             = null;

    // サービス停止（破棄時）
    private boolean                             mServiceStop        = false;

    // サービス起動インテント
    private Intent                              mServiceIntent      = null;

    // 自動スタートの場合はtrue
    private boolean                             mBoot               = false;

    // 永続層
    private AtomPreference                      mPreference         = null;

    private boolean                             mRestart            = false;

    private boolean                             mAdapterEnable      = false;

    private static int                          mCnt                = 0;

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     *********************************************************************************************/

    public MainActivity()
    {
        mCnt++;

        TAG =  MainActivity.class.getSimpleName()+ "["+mCnt+"]";
        Log.w(TAG, " new MainActivity");
    }

    /**********************************************************************************************
     *
     *  画面レイアウトをFULL-SCREEN切り替え
     *
     *********************************************************************************************/

    private void fullScreen()
    {
        View decor = this.getWindow().getDecorView();

        decor.setSystemUiVisibility(
                          View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

    }

    /***********************************************************************************************
     *
     *  ACTIVITY#CREATE                 アクティビティが新規生成
     *
     *  ユーザー指示でのランチャ起動（初回）
     *  PTTキーによるインテント起動（履歴から終了されたあとなど）
     *  OSによりBGで落とされた場合の自動起動
     *
     * @param savedInstanceState   recovery parameters
     *
     **********************************************************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Log.w(TAG,"onCreate");

        Intent intent = getIntent();

        if (intent != null)
        {
            mBoot =intent.getBooleanExtra(KEY_boot, false );
            intent.removeExtra(KEY_boot);

            if (mBoot)
            {
                Log.w(TAG,"BOOT UP");
            }
        }
        else
        {
            mBoot = false;
        }

        adjustFontScale(1.30f);

        // プリファレンス管理生成＆コールバック登録
        mPreference = new AtomPreference(getApplicationContext());

        mPreference.setCallBack(this);

        // フォアグランドサービスの反映
        boolean fore = mPreference.getPref_ForegroundService();

        Log.w(TAG,"foreground service " + fore);

        mServiceStop = !fore;

        //---------------------------------------------------------
        // Status-Observer
        //---------------------------------------------------------

        String pkgName = this.getPackageName();
        String clsName = getClassName(MainActivity.class);

        Log.w(TAG,"PKG " + pkgName + " CLS "+ clsName);

        mStatus = AtomStatus.shardInstance(pkgName, clsName);
        mStatus.uiActive(true);
        mStatus.resisterObserver(this);

        //---------------------------------------------------------
        // FULL SCREEN
        //---------------------------------------------------------

        fullScreen();

        //---------------------------------------------------------
        // ロックスクリーンの全面に出る
        //---------------------------------------------------------

        setShowWhenLocked(true);
        setTurnScreenOn(true);

        //--------------------------------------------------------
        // LAYOUT-SETTING
        //--------------------------------------------------------

        setContentView(R.layout.activity_main);

        mPager      = findViewById(R.id.viewPager);
        mAdapter    = new ViewPagerAdapter(getSupportFragmentManager(), mPreference,this);

        mPager.setAdapter(mAdapter);

        mAdapterEnable = true;

        //-------------------------------------------------------
        // bind ATOM-SERVICE
        //-------------------------------------------------------

        doBindService();
    }

    public void adjustFontScale(float scale)
    {
        Resources res = this.getResources();
        Configuration configuration = res.getConfiguration();

        if (scale != configuration.fontScale)
        {
            Log.w(TAG, "fontScale = " + configuration.fontScale + " -> " + scale);

            configuration.fontScale = scale;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(metrics);
            metrics.scaledDensity = configuration.fontScale * metrics.density;
            res.updateConfiguration(configuration, metrics);
        }
    }

    /***********************************************************************************************
     *
     *  ACTIVITY#START                 アクティビティが開始
     *
     **********************************************************************************************/

    @Override
    public void onStart()
    {
        Log.w(TAG,"onStart");

        super.onStart();
    }

    /***********************************************************************************************
     *
     *  ACTIVITY#RESUME                 アクティビティがFGへ遷移
     *
     **********************************************************************************************/

    @Override
    public void onResume()
    {
        Log.w(TAG,"onResume");


        //---------------------------------------------------------
        // FULL SCREEN
        //---------------------------------------------------------

        fullScreen();

        // ステータスバーなどがあれば閉じる
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(intent);

        if (!mAdapterEnable)
        {
            mPager.setAdapter(mAdapter);
            mAdapterEnable = true;
        }

        // 1ページ目
        //mPager.setCurrentItem(0);

        if (mBoot)
        {
            mBoot = false;
            moveToBackground();
        }

        // UIがFGになった
        mStatus.uiState(true);

        super.onResume();
    }

    /***********************************************************************************************
     *
     *  ACTIVITY#PAUSE                  アクティビティがBGへ遷移
     *
     **********************************************************************************************/

    @Override
    public void onPause()
    {
        Log.w(TAG,"onPause");

        // UIがBGに入った
        mStatus.uiState(false);

        //mAdapter.destroyAllItem(mPager);

        mPager.setAdapter(null);

        mAdapterEnable = false;

        super.onPause();
    }

    /***********************************************************************************************
     *
     *  ACTIVITY#STOP                  アクティビティが停止
     *
     **********************************************************************************************/

    @Override
    public  void onStop()
    {
        Log.w(TAG,"onStop");

        super.onStop();
    }
    /***********************************************************************************************
     *
     *  ACTIVITY#DESTROY                アクティビティ破棄
     *
     **********************************************************************************************/

    @Override
    public  void onDestroy()
    {
        Log.w(TAG,"onDestroy");
        super.onDestroy();

        // UIが完全終了した
        mStatus.uiActive(false);

        // ステータス破棄
        mStatus.freeInstance();

        // サービスとの結合を外す
        doUnbindService();

        // ステータス監視を登録解除
        mStatus.unregisterObserver();

        mPreference.clearCallBack();

        if (mAdapterEnable)
        {
            mPager.setAdapter(null);

            mAdapterEnable = false;
        }

        //  サービス停止するか？
        if (mServiceStop)
        {
            Log.e(TAG,"Stop AtomService");

            stopService(mServiceIntent);

            if (!mRestart)
            {
                String mess = getResources().getString(R.string.finish);

                Toast.makeText(this, mess, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**********************************************************************************************
     *
     *  アクティビティをBGも落とす
     *
     *********************************************************************************************/

    private void moveToBackground()
    {
        //String mess = getResources().getString(R.string.ui_bg);
        //Toast.makeText(this, mess, Toast.LENGTH_LONG).show();

        // UIをBGに落とす　→　Activity#pause
        moveTaskToBack(true);
    }

    /***********************************************************************************************
     *
     *  ACTIVITY#dispatchKeyEvent       BACKキーの処理
     *
     *  この処理をしとかないとBACKキーでACTIVITYが破棄まで移動する
     *
     **********************************************************************************************/

    @Override
    public boolean dispatchKeyEvent(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK)
        {
            int act = e.getAction();

            if (act == KeyEvent.ACTION_DOWN)
            {
                moveToBackground();
            }

            // 後段のBACKキー(UP/DOWN) 処理を握りつぶす
            //
            // -> 無駄な Activity#pause-> Activity#destroyを防ぐ

            return true;
        }

        return super.dispatchKeyEvent(e);
    }

    /**********************************************************************************************
     *
     * AtomService#onServiceConnected   サービスのバインド完了
     *
     **********************************************************************************************/

    @Override
    public void onServiceConnected(ComponentName className, IBinder service)
    {
        synchronized ( mBindLock )
        {
            Log.w(TAG, "BIND: onServiceConnected "+ className.getClassName());

            mService = ((AtomService.ServiceBinder) service).getService();

            //
            // サービスの設定
            //

            {
                // テザリング上限時間（ミリ秒）なし、5分、15分
                // 個数制限はなし
                // 制限時間不要の場合timeArray= null指定

                long[] timeArray = {0, 5 * 60 * 1000L, 15 * 60 * 1000L};
                mService.setTimeArray(timeArray);

                // PTTキーコード(286) とロングプレス時間を登録
                mService.setKeyCode(286);
                mService.setLongPressMsec(500);

                // 通知バーに表示するアイコン
                mService.setNotifyIconResource(R.mipmap.ic_launcher);

                // リスナー登録
                mService.setListener(this);

                // UI準備完了
                mService.UI_Ready();
            }

            if (mAdapter != null) mAdapter.setService(mService);

            //
            // FOREGROUNDサービスに登録
            //

            if (mPreference.getPref_ForegroundService())
            {
                mHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        synchronized (mBindLock)
                        {
                            Log.w(TAG, "startForegroundService ");

                            startForegroundService(mServiceIntent);
                        }
                    }
                });
            }
        }
    }

    /**********************************************************************************************
     *
     * AtomService#onServiceDisconnected    サービスのバインドが切れた（例外などでサービスが死んだ）
     *
     * STICKYモードなので死んだサービスは再度OSに起動されonServiceConnectedが呼ばれる（はず）
     *
     * サービスのアンバインドでは呼ばれない　→　よってクリティカルケースのみである
     *
     **********************************************************************************************/

    @Override
    public void onServiceDisconnected(ComponentName className)
    {
        synchronized (mBindLock)
        {
            Log.e(TAG, "BIND: onServiceDisconnected " + className.getClassName());

            mService = null;
        }
    }

    /**********************************************************************************************
     *
     * （内部）サービスをBINDする -> これでサービスエントリを取得（onServiceConnected）
     *
     **********************************************************************************************/

    private void doBindService()
    {
        Log.w(TAG, "bindService ");

        mServiceIntent = new Intent(MainActivity.this, AtomService.class);

        mServiceIntent.putExtra(AtomService.KEY_pkgName, this.getPackageName());
        mServiceIntent.putExtra(AtomService.KEY_clsName, getClassName(MainActivity.class));
        mServiceIntent.putExtra(AtomService.KEY_version, BuildConfig.VERSION_NAME);

        bindService(mServiceIntent,this, Context.BIND_AUTO_CREATE);

        mIsServiceBind = true;
    }

    /**********************************************************************************************
     *
     * （内部）サービスをBINDを解除する（サービス自体は常駐したままで残る）
     *
     **********************************************************************************************/

    private void doUnbindService()
    {
        if (mIsServiceBind)
        {
            Log.w(TAG, "unbindService ");

            // コネクションの解除
            unbindService(this);
            mIsServiceBind = false;
        }
    }

    /**********************************************************************************************
     *
     * AtomStatus#onNotify    ステータスオブザーバからの変化通知
     *
     **********************************************************************************************/

    @Override
    public void onNotify(AtomStatus.AtomInfo info)
    {
        Log.w(TAG, "observer#update -> update UI");

        mAdapter.notify(info);
    }

    /**********************************************************************************************
     *
     * AtomStatus#OnPttLongPress    PTTのロングプレス  from Service
     *
     * この通知はどう使っても良い
     *
     **********************************************************************************************/

    private final static boolean stopServiceDebug = false;

    @Override
    public void OnPttLongPress()
    {
        if (!stopServiceDebug)
        {
            moveToBackground();
        }
        else
        {
            // (機能デバック）
            //
            // サービス停止フラグを立てて、アクティビティを終了　→　onPause->onDestroy

            mServiceStop = true;
            finish();
        }
    }

    /********************************************************************************************
     *
     * 通知バーに表示する文字列 from Service
     *
     * @param id        接続種別（テザリングON/OFF)
     *
     * @return          表示する文字列
     *
     ********************************************************************************************/

    @Override
    public String OnNotifyString(AtomService.notifyMessage id)
    {
        // 初期文字列を表示する
        return id.toString();
    }

    /**********************************************************************************************
     *
     *  BackGround Image 選択画面の起動要求　from　ViewPager(SETTING-FRAGMENT)
     *
     *  PAUSE時にフラグメント側は破棄する仕様に変更したので、Activity側に本機能を移動する
     *
     *  破棄される側でINTENT発行すると結果（onActivityResult）が正しく取れないので、PAUSEでは
     *  破棄されないMainActivityにファイル選択画面への切り替え機能（INTENT)を持たせる
     *
     *********************************************************************************************/

    @Override
    public void onPickupImage()
    {
        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Filter to show only images, using the image MIME data type.
        // it would be "*/*".
        intent.setType("image/*");

        startActivityForResult(intent, RESULT_PICK_IMAGE);
    }

    /**********************************************************************************************
     *
     *  画面シングルタップ from ViewPager
     *
     *********************************************************************************************/

    @Override
    public void onSingleTapUp()
    {
        // UIをBGに落とす　→　Activity#pause
        moveToBackground();
    }

    /**********************************************************************************************
     *
     *  画面ダブルタップ from ViewPager
     *
     *********************************************************************************************/

    @Override
    public void onDoubleTap() {

    }

    /*********************************************************************************************
     *
     *  アクティティを再起動する
     *
     *********************************************************************************************/

    private void reload()
    {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                Log.w(TAG,"reload...");

                /*
                Intent intent = getIntent();
                overridePendingTransition(0, 0);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                overridePendingTransition(0, 0);
                startActivity(intent);
                */

                recreate();

            }
        }, 1);

    }

    /**********************************************************************************************
     *
     *  設定値が変更された  from AtomPreference
     *
     * @param key                   　変化したキー
     *
     **********************************************************************************************/

    @Override
    public void onPreferenceChanged(String key)
    {
        if (AtomPreference.KEY_autostart.equals(key))
        {
            boolean auto = mPreference.getPref_AutoStart();

            Log.w(TAG, "Change auto start[" + key + "] " + auto);

        }
        else if (AtomPreference.KEY_foregroundS.equals(key))
        {
            boolean fore = mPreference.getPref_ForegroundService();

            Log.w(TAG, "Change foreground service[" + key + "] " + fore);

            if (mServiceStop == fore)
            {
                mRestart     = true;
                mServiceStop = true;
                reload();
            }
        }
        else if (AtomPreference.KEY_background.equals(key))
        {
            Log.w(TAG, "Change background Image");

            mAdapter.backGroundImage();
        }
    }

    /**********************************************************************************************
     *
     *  INTENT発行からの戻り
     *
     *  ・BackGround Image 選択画面からの戻り　※元画像のアスペクト維持に変更
     *
     * @param requestCode       要求コード
     * @param resultCode        結果
     * @param resultData        結果データ
     *
     *********************************************************************************************/

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData)
    {
        Log.w(TAG," onActivityResult " + requestCode + " " + requestCode + " "+ resultData);

        if (requestCode == RESULT_PICK_IMAGE && resultCode == RESULT_OK)
        {
            //
            // 背景画像選択（ピッカー）からの戻り
            //

            int dst_height = mAdapter.mHeight;
            int dst_width = mAdapter.mWidth;

            //
            // 壁紙サイズが不明な場合は何もしない（安全対策）
            //

            if ((dst_width == 0) || (dst_height == 0)) return;

            if(resultData.getData() != null)
            {
                try
                {
                    //Log.w(TAG," Image Select Done");
                    Uri uri = resultData.getData();

                    ParcelFileDescriptor pfDescriptor = getContentResolver().openFileDescriptor(uri, "r");

                    if(pfDescriptor != null)
                    {
                        //
                        // bmpへ画像を読み込み
                        //

                        FileDescriptor fileDescriptor = pfDescriptor.getFileDescriptor();
                        Bitmap src_bmp = BitmapFactory.decodeFileDescriptor(fileDescriptor);

                        //
                        // ファイルクローズ
                        //

                        pfDescriptor.close();

                        //
                        // src_bmp: aspect Fit変換
                        //

                        if (src_bmp != null) {
                            int srcWidth = src_bmp.getWidth();
                            int srcHeight = src_bmp.getHeight();

                            int imgWidth, imgHeight;

                            // 縦と横の比率
                            float wRatio = ((float) srcWidth) / ((float) dst_width);
                            float hRatio = ((float) srcHeight) / ((float) dst_height);

                            if (wRatio > hRatio) {
                                // 縦比率に合わせて横を調整
                                imgHeight = srcHeight;
                                imgWidth = (int) (srcWidth * (hRatio / wRatio));
                            } else {
                                // 横比率に合わせて縦を調整
                                imgWidth = srcWidth;
                                imgHeight = (int) (srcHeight * (wRatio / hRatio));
                            }

                            //
                            // センターで切り出し
                            //

                            src_bmp = Bitmap.createBitmap(src_bmp, (srcWidth - imgWidth) / 2, (srcHeight - imgHeight) / 2, imgWidth, imgHeight);


                            //
                            // 壁紙サイズに変換（画面サイズ）
                            //

                            Bitmap wall = Bitmap.createScaledBitmap(src_bmp, dst_width, dst_height, false);

                            //
                            // ベース６４符号化（画面が大きい場合はファイル保存しファイル名保存に変更して）
                            //

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            wall.compress(Bitmap.CompressFormat.PNG, 100, baos);
                            String bitmapStr = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                            //
                            // プリファレンスへ保存（永続化）
                            //

                            mPreference.setPref_background(bitmapStr);
                        }
                        else
                        {
                            Log.e(TAG,"invalid image-data!");
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
