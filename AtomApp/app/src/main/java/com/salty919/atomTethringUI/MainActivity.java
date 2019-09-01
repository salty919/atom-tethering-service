package com.salty919.atomTethringUI;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.salty919.atomTethringService.AtomService;
import com.salty919.atomTethringService.AtomStatus;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.util.Locale;
import java.util.Objects;

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
    private static String TAG;

    @SuppressWarnings("SameParameterValue")
    private String getClassName(Class<?> cls) { return cls.getName(); }

    // バンドルキー
    public static final String    KEY_boot              = "bootUp";

    private final int           RESULT_PICK_IMAGE       = 1;
    private static final int    REQUEST_CAMERA          = 2;

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

    private boolean                             mCameraExec         = false;

    private Vibrator                            mVibrator           = null;

    private AlertDialog                         mDialog             = null;

    private Toast                               mToast              = null;

    private final Context                       mContext;

    private static final long[]                 mTimerArray         = {0,5*60*1000L,10*60*1000L,15*60*1000L, 30*60*1000L };
    private int                                 mTimerIndex         = 0;

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     *********************************************************************************************/

    public MainActivity()
    {
        mCnt++;

        TAG =  "Atom"+MainActivity.class.getSimpleName()+ "["+mCnt+"]";
        Log.w(TAG, " new MainActivity "+ this.hashCode());

        mContext = this;
    }

    /**********************************************************************************************
     *
     *  画面レイアウトをFULL-SCREEN切り替え
     *
     *********************************************************************************************/

    private void fullScreen()
    {
        View decor = this.getWindow().getDecorView();

        if (mPreference.getPref_StatusHidden())
        {
            Log.w(TAG,"STATUS BAR HIDDEN");


            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        else
        {
            Log.w(TAG,"STATUS BAR ENABLE");

            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

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

        if (mPreference == null)
        {
            // プリファレンス管理生成＆コールバック登録
            mPreference = new AtomPreference(getApplicationContext());
        }

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

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        //---------------------------------------------------------
        // FULL SCREEN
        //---------------------------------------------------------

        fullScreen();

        //---------------------------------------------------------
        // ロックスクリーンの全面に出る
        //---------------------------------------------------------

        setShowWhenLocked(true);
        setTurnScreenOn(false);

        //--------------------------------------------------------
        // LAYOUT-SETTING
        //--------------------------------------------------------

        setContentView(R.layout.activity_main);

        mPager      = findViewById(R.id.viewPager);
        mAdapter    = new ViewPagerAdapter(getSupportFragmentManager());

        mPager.setAdapter(mAdapter);

        mAdapterEnable = true;

        mCameraExec = false;

        //-------------------------------------------------------
        // bind ATOM-SERVICE
        //-------------------------------------------------------

        doBindService();
    }

    @Override
    protected void attachBaseContext(Context newBase)
    {
        Log.w(TAG, "attachBaseContext");

        super.attachBaseContext(newBase);

        if(mPreference == null)
        {
            mPreference = new AtomPreference(getApplicationContext());
        }

        float scale = mPreference.getPref_FontScale();
        Log.w(TAG, " FONT " + scale);

        final Configuration override = new Configuration(newBase.getResources().getConfiguration());
        override.fontScale = scale;
        applyOverrideConfiguration(override);


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

        mCameraExec = false;

        setTurnScreenOn(mPreference.getPref_PttWakeup());
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

        if (mAdapterEnable)
        {
            mPager.setAdapter(null);
            mAdapterEnable = false;
        }

        // UIがBGに入った
        mStatus.uiState(false);

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
        Log.w(TAG,"MOVE TO BackGround...");

        // UIをBGに落とす　→　Activity#pause
        moveTaskToBack(true);
    }

    /**********************************************************************************************
     *
     *  ロック画面上での動作かどうか確認
     *
     * @return  true ロック画面
     *
     ********************************************************************************************/

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isLockScreen()
    {
        KeyguardManager keyguardmanager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        return Objects.requireNonNull(keyguardmanager).isKeyguardLocked();
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
        if (KeyEvent(e)) return true;

        return super.dispatchKeyEvent(e);
    }

    private boolean KeyEvent(KeyEvent e)
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
        else if ((mService !=null)&&(mStatus !=null))
        {
            // ユーザー補助なしの場合はこちらで処理
            if (!mStatus.getInfo().mPtt)
            {
                Log.w(TAG,"PRESS KEY " + e.getKeyCode() + (e.getAction() == KeyEvent.ACTION_DOWN ? " DOWN ":" UP "));
                return mService.KeyCheck(e);
            }
        }

        return  false;
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

        mServiceIntent.putExtra(AtomService.KEY_pkgName,    this.getPackageName());
        mServiceIntent.putExtra(AtomService.KEY_clsName,    getClassName(MainActivity.class));
        mServiceIntent.putExtra(AtomService.KEY_version,    BuildConfig.VERSION_NAME);
        mServiceIntent.putExtra(AtomService.KEY_pttwakeup,  mPreference.getPref_PttWakeup());

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
        //Log.w(TAG, "observer#update -> update UI");

        mAdapter.notify(info);
    }

    @Override
    public Boolean OnVolUp(boolean exec)
    {
        int pos = mPager.getCurrentItem();

        if (mAdapter.getItem(pos) instanceof ControlFragment)
        {
            if (exec)
            {
                mTimerIndex++;
                if (mTimerIndex >= mTimerArray.length) mTimerIndex = 0;

                long duration = mTimerArray[mTimerIndex];

                mService.autoOffTimer(duration);
            }
            return true;
        }
        else if (mAdapter.getItem(pos) instanceof SettingFragment)
        {
            if (!isLockScreen())
            {
                if (exec)
                {
                    float scale = mPreference.getPref_FontScale();

                    if (scale == 1.3f) scale = 1.0f;
                    else scale = 1.3f;

                    mPreference.setPref_FontScale(scale);

                }
                else
                {
                    float scale = mPreference.getPref_FontScale();
                    String message = String.format(Locale.US, "FONT SCALE %1.1f", scale );
                    mToast = Toast.makeText(mContext, message, Toast.LENGTH_LONG);
                    mToast.setGravity(Gravity.CENTER, 0, 0);
                    {
                        View view = mToast.getView();
                        view.setBackgroundResource(R.color.yellow);
                    }
                    mToast.show();

                    reload();
                }

                return true;
            }
        }

        return false;
    }

    /**********************************************************************************************
     *
     * AtomStatus#OnPttPress    PTTのプレス  from Service
     *
     *********************************************************************************************/
    @Override
    public void OnPttPress()
    {
        mService.tetheringToggle();
    }

    /**********************************************************************************************
     *
     * AtomStatus#OnPttLongPress    PTTのロングプレス  from Service
     *
     **********************************************************************************************/

    @Override
    public void OnPttLongPress()
    {
        int pos = mPager.getCurrentItem();

        if (mAdapter.getItem(pos) instanceof ControlFragment)
        {
            // カメラ起動
            Func_CameraExecute();
        }
        else if (mAdapter.getItem(pos) instanceof SettingFragment)
        {
            if (!isLockScreen())
            {
                // Change SCREEN ON MODE
                Func_ScreenOnMode();
            }
            else
            {
                // ステータスバーのトグル制御
                Func_StatusBarToggle();
            }
        }
        else
        {
            // アプリ終了
            Func_ExitApp();
        }
    }

    private void Func_CameraExecute()
    {
        if (mPreference.getPref_CameraExec())
        {
            Log.w(TAG, "<camera Exec>");

            mCameraExec = true;

            mVibrator.vibrate(VibrationEffect.createOneShot(150, 10));

            // インテントのインスタンス生成
            Intent intent = new Intent();
            // インテントにアクションをセット
            intent.setAction("android.media.action.IMAGE_CAPTURE_SECURE");

            try
            {
                // カメラアプリ起動
                //startActivityForResult(intent, REQUEST_CAMERA);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, REQUEST_CAMERA, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                //pendingIntent.send();
                IntentSender intentSender = pendingIntent.getIntentSender();
                startIntentSenderForResult(intentSender, REQUEST_CAMERA, null, 0, 0, 0);
            }
            catch (Exception e)
            {
                Log.e(TAG, "camera Intent error");
            }
        }
    }

    private void Func_ScreenOnMode()
    {
        // PTT / POT のトグル制御

        mVibrator.vibrate(VibrationEffect.createOneShot(150, 10));


        if (mDialog != null) mDialog.dismiss();

        mDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.operation )
                .setMessage(R.string.operationMessage)
                .setPositiveButton(R.string.ptt, new  DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mPreference.setPref_PttWakeup(true); reload();
                        mToast = Toast.makeText(mContext, R.string.pttwakeup, Toast.LENGTH_LONG);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        {
                            View view = mToast.getView();
                            view.setBackgroundResource(R.color.yellow);
                        }
                        mToast.show();
                    }
                })
                .setNegativeButton(R.string.pow, new  DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mPreference.setPref_PttWakeup(false); reload();
                        mToast = Toast.makeText(mContext, R.string.powerwakeup, Toast.LENGTH_LONG);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        {
                            View view = mToast.getView();
                            view.setBackgroundResource(R.color.yellow);
                        }
                        mToast.show();
                    }
                })
                .setNeutralButton(R.string.cancel, new  DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        fullScreen();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        fullScreen();
                    }
                })
                .show();
    }
    private void Func_StatusBarToggle()
    {
        mVibrator.vibrate(VibrationEffect.createOneShot(150, 10));

        boolean hidden = mPreference.getPref_StatusHidden();

        hidden = !hidden;

        mPreference.setPref_StatusHidden(hidden);

        fullScreen();
    }

    private void Func_ExitApp()
    {
        mVibrator.vibrate(VibrationEffect.createOneShot(150, 10));

        if (mDialog != null) mDialog.dismiss();

        mDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.exitDialog )
                .setPositiveButton(R.string.yes, new  DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mServiceStop = true;
                        finish();
                    }
                })
                .setNegativeButton(R.string.no, new  DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        fullScreen();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        fullScreen();
                    }
                })
                .show();

    }

    @Override
    public void OnScreenOff()
    {
        if (mCameraExec)
        {
            Log.w(TAG,"<Camera force finish> ");
            mCameraExec = false;
            moveToBackground();
        }

        if (mDialog!= null)
        {
            mDialog.dismiss();
            mDialog = null;
        }

        if (mToast !=null)
        {
            mToast.cancel();
        }
    }

    @Override
    public void OnScreenOn()
    {

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

    /*********************************************************************************************
     *
     *  FragmentBaseリスナ； フラグメント側の準備完了
     *
     * @param fragment      フラグメント
     *
     *********************************************************************************************/

    @Override
    public void onReady(FragmentBase fragment)
    {
        Log.w(TAG,"onReady "+ fragment.TAG);

        fragment.setPreference(mPreference);
        fragment.setService(mService);
        fragment.UI_update(mStatus.getInfo());
    }

    /**********************************************************************************************
     *
     *  FragmentBaseリスナ: BackGround Image 選択画面の起動要求　
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
     *  FragmentBaseリスナ: 画面シングルタップ
     *
     *********************************************************************************************/

    @Override
    public void onSingleTapUp()
    {
        int pos = mPager.getCurrentItem();

        if (mAdapter.getItem(pos) instanceof ControlFragment)
        {
            moveToBackground();
        }
    }

    /**********************************************************************************************
     *
     *  FragmentBaseリスナ: 画面ダブルタップ
     *
     *********************************************************************************************/

    @Override
    public void onDoubleTap() {

    }

    /*********************************************************************************************
     *
     *  FragmentBaseリスナ:  DIALOG CANCEL
     *
     ********************************************************************************************/
    @Override
    public void onCancel()
    {
        fullScreen();
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
        if (AtomPreference.KEY_autoStart.equals(key))
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
        else if (AtomPreference.KEY_pttWakeup.equals(key))
        {
            if (mPreference.getPref_PttWakeup())
            {
                Log.w(TAG,"Change wakeup Type PTT");
            }
            else
            {
                Log.w(TAG,"Change wakeup Type POWER");
            }


            reload();
        }
        else if (AtomPreference.KEY_cameraExec.equals(key))
        {
            mAdapter.notify(mStatus.getInfo());
        }
        else if (AtomPreference.KEY_statusHidden.equals(key))
        {
            if (mPreference.getPref_StatusHidden())
            {
                Log.w(TAG, "Change Status bar OFF");
            }
            else
            {
                Log.w(TAG, "Change Status bar ON");
            }
        }
        else if (AtomPreference.KEY_fontScale.equals(key))
        {
            Log.w(TAG, "Change font scale "+ mPreference.getPref_FontScale());
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
        if (requestCode == REQUEST_CAMERA)
        {
            Log.w(TAG,"<CAMERA finish>");
            mCameraExec =false;

        }
        else if (requestCode == RESULT_PICK_IMAGE && resultCode == RESULT_OK)
        {
            //
            // 背景画像選択（ピッカー）からの戻り
            //
            Display display = getWindowManager().getDefaultDisplay();

            Point point = new Point();

            display.getRealSize(point);

            int dst_height  = point.y;
            int dst_width   = point.x;

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

