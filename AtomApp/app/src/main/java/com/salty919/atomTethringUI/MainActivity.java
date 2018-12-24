package com.salty919.atomTethringUI;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.salty919.atomTethringService.AtomService;
import com.salty919.atomTethringService.AtomStatus;

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
        AtomStatus.observer,AtomService.Listener,FragmentBase.Listener,
        AtomPreference.callBack
{
    private static final String TAG = MainActivity.class.getSimpleName();

    // バンドルキー
    public static final String    KEY_boot            = "bootUp";

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
        mStatus.mUiActive = true;
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

        FragmentManager manager = getSupportFragmentManager();
        mAdapter = new ViewPagerAdapter(manager);

        mPager    = findViewById(R.id.viewPager);
        mPager.setAdapter(mAdapter);

        //-------------------------------------------------------
        // bind ATOM-SERVICE
        //-------------------------------------------------------

        doBindService();
    }

    @SuppressWarnings("SameParameterValue")
    private String getClassName(Class<?> cls)
    {
        return cls.getName();
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

        // UIがFGになった
        mStatus.mUiForeground = true;

        // ステータスバーなどがあれば閉じる
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(intent);

        // 1ページ目
        mPager.setCurrentItem(ViewPagerAdapter.CONTROL_POS);

        if (mBoot)
        {
            mBoot = false;
            moveToBackground();
        }

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
        mStatus.mUiForeground = false;


        super.onPause();
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
        mStatus.mUiActive = false;

        // ステータス破棄
        mStatus.freeInstance();

        // サービスとの結合を外す
        doUnbindService();

        // ステータス監視を登録解除
        mStatus.unregisterObserver();

        mPreference.clearCallBack();

        try {

            ControlFragment cf = (ControlFragment) mAdapter.getItem(ViewPagerAdapter.CONTROL_POS);

            cf.setListener(null);

            SettingFragment sf = (SettingFragment) mAdapter.getItem(ViewPagerAdapter.SETTING_POS);

            sf.setListener(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        //  サービス停止するか？
        if (mServiceStop)
        {
            Log.e(TAG,"Stop AtomService");
            String mess = getResources().getString(R.string.finish);

            Toast.makeText(this, mess, Toast.LENGTH_LONG).show();

            stopService(mServiceIntent);

            if (mRestart) reload();
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

            try
            {
                ControlFragment cf = (ControlFragment) mAdapter.getItem(ViewPagerAdapter.CONTROL_POS);

                Log.e(TAG, "SET PREFERENCE to CONTROL");

                cf.setService(mService);
                cf.setPreference(mPreference);
                cf.setListener(this);

                SettingFragment sf = (SettingFragment) mAdapter.getItem(ViewPagerAdapter.SETTING_POS);

                Log.e(TAG, "SET PREFERENCE to SETTING");

                sf.setPreference(mPreference);
                sf.setService(mService);
                sf.setListener(this);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            //
            // FOREGROUNDサービスに登録
            //

            if (mPreference.getPref_ForegroundService())
            {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startForeground();
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
     * （内部）サービスをforegroundServiceにする
     *
     * 　これによりサービスはunbindしても消えない常駐サービスになる
     *
     **********************************************************************************************/

    private void startForeground()
    {
        synchronized (mBindLock)
        {
            // 既にフォアグランドサービスに登録済みなら何もしない

            //if ((mService != null) && (!mService.isForeground()))
            {

                Log.w(TAG, "startForegroundService ");

                //
                // AtomControlServiceをフォアグランドサービス登録する
                //

                startForegroundService(mServiceIntent);
            }
        }
    }

    /**********************************************************************************************
     *
     * （内部）サービスをBINDする -> これでサービスエントリを取得（onServiceConnected）
     *
     **********************************************************************************************/

    private void doBindService()
    {
        mServiceIntent = new Intent(MainActivity.this, AtomService.class);

        mServiceIntent.putExtra(AtomService.KEY_pkgName, this.getPackageName());
        mServiceIntent.putExtra(AtomService.KEY_clsName, getClassName(MainActivity.class));

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
        //Log.w(TAG, "observer#update -> update UI");

        ControlFragment cf = (ControlFragment) mAdapter.getItem(ViewPagerAdapter.CONTROL_POS);
        SettingFragment sf = (SettingFragment) mAdapter.getItem(ViewPagerAdapter.SETTING_POS);

        // UIの情報更新
        if (cf != null) { cf.UI_update(info);  }
        if (sf != null) { sf.UI_update(info);  }
    }

    /**********************************************************************************************
     *
     * AtomStatus#OnPttLongPress    PTTのロングプレス
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
     * 通知バーに表示する文字列
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

    @Override
    public void onSingleTapUp()
    {

        KeyguardManager km = (KeyguardManager) this.getSystemService(KEYGUARD_SERVICE);

        if ( ! km.isKeyguardLocked())
        {
            Log.w(TAG,"LockScreen OFF");
            // UIをBGに落とす　→　Activity#pause
            moveToBackground();
        }
        else
        {
            // TODO; ロック画面の自動解除（できるのか）
            Log.w(TAG,"LockScreen ON");
            // UIをBGに落とす　→　Activity#pause
            moveToBackground();
        }
    }

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
        Log.w(TAG,"reload...");
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    /**********************************************************************************************
     *
     *  設定値が変更された
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
                finish();
            }
        }
        else if (AtomPreference.KEY_background.equals(key))
        {
            ControlFragment cf = (ControlFragment) mAdapter.getItem(ViewPagerAdapter.CONTROL_POS);
            SettingFragment sf = (SettingFragment) mAdapter.getItem(ViewPagerAdapter.SETTING_POS);

            // UIの情報更新
            if (cf != null) { cf.background_change();  }
            if (sf != null) { sf.background_change();  }

        }
    }
}