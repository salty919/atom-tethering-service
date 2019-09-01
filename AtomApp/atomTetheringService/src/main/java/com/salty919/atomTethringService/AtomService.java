package com.salty919.atomTethringService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

/**************************************************************************************************
 *
 *  ATOMサービス（フォアグランドサービス）
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

@SuppressWarnings("ALL")
public class AtomService extends Service
{
    private static final String TAG = AtomService.class.getSimpleName();

    public static final String      KEY_pkgName     = "packageName";
    public static final String      KEY_clsName     = "className";
    public static final String      KEY_version     = "uiVersion";
    @SuppressWarnings("SpellCheckingInspection")
    public static final String      KEY_pttwakeup   = "pttwakeup";

    @SuppressWarnings("unused")
    private static final String     ACTION_PTT_UP   = "android.intent.action.PTT.up";
    private static final String     ACTION_PTT_DOWN = "android.intent.action.PTT.down";

    private final String title          = "AtomTethering ";
    private final String serviceVersion = BuildConfig.VERSION_NAME;
    private String uiVersion            = null;

    @SuppressWarnings("FieldCanBeLocal")
    private final String channelId = "Atom-service-channel";

    private Intent  mIntent             = null;

    @SuppressWarnings("FieldCanBeLocal")
    private int         mPressKeyCode   = 0;

    @SuppressWarnings("FieldCanBeLocal")
    private final long    mPressLongPress       = 500;

    private notifyMessage   mNotifyId   = notifyMessage.NOTIFY_NOTING;

    public  ArrayList<AtomDevice> getDeviceList() { return mIpv4List; }

    private ArrayList<AtomDevice> mIpv4List  = null;

    private final String    mCell       = "ccmni";
    private String          mCell_tmp   = null;
    private String          mCellIp     = "";

    @SuppressWarnings("SpellCheckingInspection")
    private final String    mWifi       = "wlan";
    private String          mWifi_tmp   = null;
    private String          mWifiIp     = "";

    private final String    mBlue       = "bt-pan";
    private String          mBlue_tmp   = null;
    private String          mBlueIp     = "";

    @SuppressWarnings("SpellCheckingInspection")
    private final String    mUsb        = "rndis";
    private String          mUsbIp      = "";

    private final String    mVpn        = "ppp";
    private final String    mVpn2       = "tun";
    private String          mVpn_tmp    = null;

    private String          mVpnIp      = "";

    private final String    mWap        = "ap";
    private String          mWapIp      = "";

    private String          mDeviceName = "";

    private boolean         mPttWakeup  = true;

    private boolean isBackGroundUI()  {return (!mStatus.mUiUsed) || (!mStatus.mUiForeground); }

    /**********************************************************************************************
     *
     *  通知メッセージ種別（テザリングON/OFF) OnNotifyStringの引数
     *
     *********************************************************************************************/

    public enum notifyMessage
    {
        /**     未決定N通知               */
        NOTIFY_NOTING(0,"unknown"),

        /**     テザリングON通知               */
        NOTIFY_TETHER_ON(1, "tethering ON"),

        /**     テザリングOFF通知              */
        NOTIFY_TETHER_OFF(2, "tethering OFF");

        @SuppressWarnings("unused")
        private  final int        mId;
        private  final String     mStr;

        notifyMessage(int id, String name)
        {
            mId     = id;
            mStr    = name;
        }

        /** 初期文字列取得　*/

        @SuppressWarnings("NullableProblems")
        public String toString() { return mStr; }
    }

    /**********************************************************************************************
     *
     * アクティビティへの通知
     *
     **********************************************************************************************/

    public interface Listener
    {
        /**
         *
         * 通知領域表示文字要求
         *
         * @param   mid     通知メッセージ種別
         *
         * @return  通知バーに表示する文字列
         *
         */

        String OnNotifyString(notifyMessage mid);

        void    OnPttPress();
        /**  PTTのロングプレス通知（ユーザ側で自由に使ってよい）  */

        void   OnPttLongPress();

        Boolean OnVolUp(boolean exec);

        void   OnScreenOn();

        void   OnScreenOff();
    }

    private Listener    mListener;

    /**********************************************************************************************
     *
     * サービスリスナーの登録（複数登録は不可）
     *
     * @param listener  通知先オブジェクト
     *
     *********************************************************************************************/

    public void setListener(Listener listener) { mListener = listener; }

    private int     mNotifyIconResource;

    /*********************************************************************************************
     *
     * 通知猟奇アイコンのリソースID
     *
     * @param resourceId    アイコンリソースID
     *
     ********************************************************************************************/

    public void  setNotifyIconResource(int resourceId) { mNotifyIconResource = resourceId; }

    //--------------------------------------------------------------------------------------
    // サービス排他
    //--------------------------------------------------------------------------------------
    private final Object        mServiceLock    = new Object();

    //--------------------------------------------------------------------------------------
    // パラメータ
    //--------------------------------------------------------------------------------------

    private AtomStatus          mStatus         = null;

    //--------------------------------------------------------------------------------------
    // テザリング制御
    //--------------------------------------------------------------------------------------

    private WifiApController        mWAp;

    //--------------------------------------------------------------------------------------
    // MOBILE（Tethering） -> MOBILE変化ではリスナーがこないので監視用ハンドラ
    //-------------------------------------------------------------------------------------

    private final Handler       mHandle             = new Handler();

    private final Object        mHandleLock         = new Object();

    private final int           mHandleCycleMsec    = 1000;

    @SuppressWarnings("FieldCanBeLocal")
    private final int           mHandleCntMax       = 2;
    private int                 mHandleCnt          = 0;

    private HandlerThread       mTetherStopThread;
    private Handler             mTetherStopHandler;
    private long                mTetherStartTime    = 0;
    private long                mTetherStartNsec    = 0;
    private long                mTetherStopCnt      = 0;

    private long                mTetherTmpTime      = 0;
    private boolean             mTetherTimerFlag    = false;

    private long                mTetherDuration     = 0;

    private boolean             mRegisterReceiver   = false;

    private Vibrator            mVibrator           = null;

    private NotificationChannel mChannel            = null;

    //--------------------------------------------------------------------------------------
    // フォアグランドサービス登録済みかどうか
    //--------------------------------------------------------------------------------------

    private boolean             mServiceForeground = false;

    //--------------------------------------------------------------------------------------
    //サービスに接続するためのBinder
    //--------------------------------------------------------------------------------------

    public class ServiceBinder extends Binder
    {
        //サービスインスタンスを外部が取得するメソッドを実装
        public AtomService getService()
        {
            return AtomService.this;
        }
    }

    //--------------------------------------------------------------------------------------
    //Binderの生成
    //--------------------------------------------------------------------------------------

    private final IBinder mBinder = new ServiceBinder();

    //--------------------------------------------------------------------------------------
    // コネクション検知処理　変化点から１秒周期で数回実施
    //--------------------------------------------------------------------------------------

    private final Runnable mConnectionProcess = new Runnable()
    {
        @Override
        public void run()
        {
            _checkConnection(mHandleCnt);

            synchronized (mHandleLock)
            {
                if( mHandleCnt > 0)
                {
                    mHandle.removeCallbacks(mConnectionProcess);
                    mHandle.postDelayed(mConnectionProcess, mHandleCycleMsec);
                    mHandleCnt--;
                }
            }
        }
    };

    /**********************************************************************************************
     *
     *  Service#onCreate
     *
     *********************************************************************************************/

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.w(TAG, "onCreate");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1)
        {
            mPressKeyCode = 287;
        }
        else
        {
            mPressKeyCode = 286;
        }
        //-------------------------------------------------------
        // SYSTEM-SETTING PERMISSION (only one time)
        //-------------------------------------------------------

        if ( !Settings.System.canWrite(getApplicationContext()))
        {
            Log.w(TAG,"get SYSTEM-SETTING-PERMISSION");

            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:"+getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        mWAp = new WifiApController(this);

        mTetherStopThread = new HandlerThread("Tethering Handler");
        mTetherStopThread.start();
        mTetherStopHandler = new Handler(mTetherStopThread.getLooper());

        mStatus = AtomStatus.shardInstance();

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

    }

    /**********************************************************************************************
     *
     *  Service#onStartCommand
     *
     *********************************************************************************************/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        synchronized (mServiceLock)
        {
            if (intent != null)
            {
                // バンドルキーでUIのINTENT情報をステータスに記録
                //
                // -> サービスが異常終了した場合、OSによるサービス再起動が起こるので
                //   　本バンドル情報を元にUI層へのインテント情報を格納する

                Log.w(TAG, "onStartCommand[" + startId + "]" + intent.getStringExtra(KEY_pkgName) + " " + intent.getStringExtra(KEY_clsName)
                        + " PTT " + intent.getBooleanExtra(KEY_pttwakeup, true));

                uiVersion   = intent.getStringExtra(KEY_version);
                mPttWakeup  = intent.getBooleanExtra(KEY_pttwakeup,true);

                // ステータスを取得（UI側情報をセット）
                // 参照カウンタは上げない
                mStatus = AtomStatus.setRecovery(intent.getStringExtra(KEY_pkgName), intent.getStringExtra(KEY_clsName));
            }

            mServiceForeground = true;

            if (mStatus.getInfo().mTether)
            {
                startForeground(notifyMessage.NOTIFY_TETHER_ON);
            }
            else
            {
                startForeground(notifyMessage.NOTIFY_TETHER_OFF);
            }


            registerReceivers();
        }

        return START_REDELIVER_INTENT;
    }

    /**********************************************************************************************
     *
     *  フォアグランドサービスにする為の通知の発行  （通知情報の更新も実施）
     *
     *********************************************************************************************/

    @SuppressWarnings("SameParameterValue")
    private void startForeground( notifyMessage mid)
    {
        //if ((!mServiceForeground)||(mNotifyId == mid)) return;
        if (!mServiceForeground) return;
        Log.w(TAG,"startForeground");

        mNotifyId = mid;

        if (mChannel == null)
        {
            mChannel = new NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_DEFAULT);

            mChannel.setSound(null,null);
            mChannel.setShowBadge(false);
        }

        NotificationManager notificationManager = (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        String contentText;

        if (mListener != null)
        {
            contentText = mListener.OnNotifyString(mid);
        }
        else
        {
            contentText = mid.toString();
        }

        //マネージャに登録する
        Objects.requireNonNull(notificationManager).createNotificationChannel(mChannel);

        if (mIntent == null)
        {
            mIntent = mStatus.getNewIntent(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mIntent, PendingIntent.FLAG_CANCEL_CURRENT );

        Notification notification = new Notification.Builder(getApplicationContext(), channelId)
                .setContentTitle(title+" v"+uiVersion+"."+serviceVersion)
                .setSmallIcon(mNotifyIconResource)
                .setContentIntent(pendingIntent)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),mNotifyIconResource))
                .setContentText(contentText)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        // フォアグラウンドで実行
        startForeground(1, notification);
    }

    /**********************************************************************************************
     *
     *  Service#onBind  サービスのActivityからのバインド
     *
     *********************************************************************************************/

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.w(TAG, "onBind " + intent.getStringExtra(KEY_pkgName) + " " + intent.getStringExtra(KEY_clsName)
                    + " PTT " + intent.getBooleanExtra(KEY_pttwakeup, true));

        synchronized (mServiceLock)
        {
            mPttWakeup = intent.getBooleanExtra(KEY_pttwakeup,true);

            registerReceivers();
        }

        return mBinder;
    }

    /**********************************************************************************************
     *
     *  Service#onDestroy
     *
     *********************************************************************************************/

    @Override
    public void onDestroy()
    {
        Log.w(TAG,"onDestroy");

        super.onDestroy();

        synchronized (mHandleLock)
        {
            mHandle.removeCallbacks(mConnectionProcess);
        }

        synchronized (mServiceLock)
        {
            unregisterReceivers();

            mTetherStopHandler.removeCallbacks(mTimerProcess);
            mTetherStopThread.quitSafely();
        }

        mStatus.freeInstance();

        mStatus = null;

        stopForeground(true);

        // Service終了
        stopSelf();

    }

    /**********************************************************************************************
     *
     *  OS各種レシーバ登録
     *
     **********************************************************************************************/

    private void registerReceivers()
    {
        if (!mRegisterReceiver)
        {
            mRegisterReceiver = true;

            //------------------------------------------------------
            // ユーザ補助と接続
            //------------------------------------------------------

            Log.w(TAG,"connect Accessibility ");

            AtomAccessibility.setListener(mPressListener);

            //------------------------------------------------------
            // TETHERING OBSERVER
            //------------------------------------------------------

            registerReceiver(mTetherReceiver, new IntentFilter("android.net.conn.TETHER_STATE_CHANGED"));

            //------------------------------------------------------
            // BATTERY OBSERVER
            //------------------------------------------------------

            registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            //------------------------------------------------------
            // NETWORK CONNECTION OBSERVER
            //------------------------------------------------------

            registerReceiver(mConnectReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            registerReceiver(mConnectReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

            //-------------------------------------------------------
            // INTENT監視
            //-------------------------------------------------------

            //registerReceiver(mPttIntentReceiver, new IntentFilter(ACTION_PTT_UP));
            registerReceiver(mPttIntentReceiver, new IntentFilter(ACTION_PTT_DOWN));

            registerReceiver(mDozeReceiver, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));

            registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
            registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
            registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        }
    }

    /**********************************************************************************************
     *
     *  OS各種レシーバ登録解除
     *
     **********************************************************************************************/

    private void unregisterReceivers()
    {
        if (mRegisterReceiver)
        {
            mRegisterReceiver = false;

            unregisterReceiver(mConnectReceiver);
            unregisterReceiver(mBatteryReceiver);
            unregisterReceiver(mTetherReceiver);
            unregisterReceiver(mPttIntentReceiver);
            unregisterReceiver(mDozeReceiver);
            unregisterReceiver(mScreenReceiver);

            AtomAccessibility.setListener(null);
        }
    }

    //------------------------------------------------------------------------------
    // PTT監視レシーバー
    //------------------------------------------------------------------------------

    private final BroadcastReceiver mPttIntentReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action == null) return;

            if (action.equals(ACTION_PTT_DOWN))
            {
                synchronized (mServiceLock)
                {
                    if ((isPttIntentRunning()) && (isBackGroundUI()))
                    {
                        appForeground(Intent.FLAG_ACTIVITY_NEW_TASK, true);
                    }
                }
            }
        }
    };

    private boolean isPttIntentRunning()
    {
        if (!mPttWakeup)
        {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

            return pm.isInteractive();
        }

        return true;
    }

    //------------------------------------------------------------------------------
    // コネクション監視レシーバー
    //------------------------------------------------------------------------------

    private final BroadcastReceiver mConnectReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            synchronized (mServiceLock)
            {
                // 端末のコネクションが変化した（こない場合がある）
                startConnectionCheckCycle();
            }
        }
    };

    //------------------------------------------------------------------------------
    // テザリング監視レシーバー
    //------------------------------------------------------------------------------

    private final BroadcastReceiver mTetherReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            try
            {
                synchronized (mServiceLock)
                {
                    if (mStatus != null)
                    {
                        startConnectionCheckCycle();
                    }
                }
            }
            catch (final Throwable ignored)
            {
                Log.e(TAG, "isWifiApEnabled Exception");
            }
        }
    };

    //------------------------------------------------------------------------------
    // バッテリー監視レシーバー
    //------------------------------------------------------------------------------

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            try
            {
                //Log.w(TAG, "mBatteryReceiver START");

                synchronized (mServiceLock)
                {
                    int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    AtomStatus.AtomInfo info = mStatus.getInfo();

                    if (rawLevel != -1 && scale != -1)
                    {
                        // ↑で渡しているデフォルト値の-1でないことを確認
                        float batteryLevelFloat = rawLevel / (float) scale;
                        info.mBatteryLevel = (int) (batteryLevelFloat * 100); // 0-100 の batteryLevel 値を設定
                    }

                    mStatus.update();
                }
                //Log.w(TAG, "mBatteryReceiver END");
            }
            catch (Exception e)
            {
                Log.e(TAG," mBatteryReceiver EXCEPTION");
                e.printStackTrace();
            }
        }
    };

    //------------------------------------------------------------------------------
    // DOZE監視レシーバー
    //------------------------------------------------------------------------------

    private  int mDozeCnt = 0;
    private final BroadcastReceiver mDozeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            PowerManager powerManager = context.getSystemService(PowerManager.class);
            boolean isDoze = powerManager.isDeviceIdleMode();
            mDozeCnt++;
            Log.e(TAG, "isDoze: " + isDoze + " "+mDozeCnt);
        }
    };

    //------------------------------------------------------------------------------
    // SCREEN 監視レシーバー
    //------------------------------------------------------------------------------

    private final BroadcastReceiver  mScreenReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            synchronized (mServiceLock)
            {
                switch (action)
                {
                    case Intent.ACTION_SCREEN_ON:

                        Log.w(TAG, "SCREEN ON");

                        if (mListener != null) mListener.OnScreenOn();

                        break;

                    case Intent.ACTION_SCREEN_OFF:

                        Log.w(TAG, "SCREEN OFF");
                        if (mListener != null) mListener.OnScreenOff();

                        // PTTでのスクリーンONが無効の場合は、画面オフ時にTOPアクティティにする

                        if (!mPttWakeup)
                        {
                            if (!mStatus.mUiForeground)
                            {
                                appForeground(Intent.FLAG_ACTIVITY_NEW_TASK, false);
                            }
                        }

                        break;

                    case Intent.ACTION_USER_PRESENT:

                        Log.w(TAG, "LOCK OFF");
                        break;
                }
            }
        }
    };

    //----------------------------------------------------------------------
    // ユーザ補助コールバック
    //----------------------------------------------------------------------

    private final AtomAccessibility.KeyInterface mPressListener = new AtomAccessibility.KeyInterface()
    {
        /******************************************************************************************
         *
         *  ユーザー補助の準備完了
         *
         * @param enable
         *
         *****************************************************************************************/

        @Override
        public void onReady(boolean enable)
        {
            synchronized (mServiceLock)
            {
                Log.w(TAG,"onReady " + enable);

                if (mStatus != null)
                {
                    AtomStatus.AtomInfo info = mStatus.getInfo();
                    info.mPtt = enable;
                    mStatus.update();
                }
            }
        }

        /*****************************************************************************************
         *
         * 　ユーザー補助でのKEY検知
         *
         * @param e     KEYイベント
         * @return      TRUE：下流に渡さない　FALSE：　下流に渡す
         *
         ****************************************************************************************/

        @Override
        public boolean onKeyPress(KeyEvent e)
        {
            return KeyCheck(e);
        }
    };

    /*********************************************************************************************
     *
     *  テザリング切り替え
     *
     ********************************************************************************************/

    public void tetheringToggle()
    {
        try
        {
            synchronized (mServiceLock)
            {
                AtomStatus.AtomInfo info = mStatus.getInfo();

                mVibrator.vibrate(VibrationEffect.createOneShot(150,10));

                if (!info.mTether)
                {
                    Log.w(TAG, "request Tethering ON");

                    tetherOn();

                    info.mTether = true;

                    startForeground(notifyMessage.NOTIFY_TETHER_ON);

                }
                else
                {
                    Log.w(TAG, "request Tethering OFF");

                    tetherOff();

                    info.mTether = false;

                    startForeground(notifyMessage.NOTIFY_TETHER_OFF);
                }

                mStatus.update();

                _changeTetherTime();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**********************************************************************************************
     *
     *  フォアグランドに出す
     *
     *********************************************************************************************/

    @SuppressWarnings("SameParameterValue")
    private void appForeground(int flag, boolean vib_on)
    {
        //Log.w(TAG, "appForeground ");

        try
        {
            if (vib_on)
            {
                mVibrator.vibrate(VibrationEffect.createOneShot(150, 10));
            }

            mIntent =  mStatus.getNewIntent(flag);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            pendingIntent.send();

            startForeground(mNotifyId);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**********************************************************************************************
     *
     * デバイス名の末尾の番号を除いた部分を戻す（番号は１桁前提）
     *
     * @param name  デバイス名　xxxx0
     * @return xxxxx
     *
     *********************************************************************************************/

    @SuppressWarnings("SpellCheckingInspection")
    private String _deviceName(String name)
    {
        try
        {
            return name.substring(0, name.length() - 1);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /*********************************************************************************************
     *
     * @param linkProperties    リンク情報
     * @return true デフォルトルート
     *
     ********************************************************************************************/

    private static boolean hasDefaultRoute(LinkProperties linkProperties) {
        for(RouteInfo route: linkProperties.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }
    /***********************************************************************************************
     *
     *  コネクション取得
     *
     * @param phase     呼び出しフェーズ 変化点から2->1->0の３回呼び出してタイミング検知ミスを防ぐ
     *
     **********************************************************************************************/

    private void _checkConnection(int phase)
    {
        try
        {
            synchronized (mServiceLock)
            {
                AtomStatus.AtomInfo info = mStatus.getInfo();

                ConnectivityManager conn        = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo         networkInfo = Objects.requireNonNull(conn).getActiveNetworkInfo();
                Network             gateway     = Objects.requireNonNull(conn).getActiveNetwork();
                NetworkCapabilities gatewayCap  = conn.getNetworkCapabilities(gateway);

                info.mType    = ConnectionType.NONE;
                info.mMainOut = ConnectionType.NONE;

                if (networkInfo != null)
                {
                    //
                    // 回線付加情報
                    //

                    String              extraInf        = networkInfo.getExtraInfo();
                    String              subtype         = networkInfo.getSubtypeName();

                    //
                    // 空文字はNULLにする
                    //

                    if ((extraInf != null) && (extraInf.equals(""))) extraInf = null;
                    if ((subtype  != null) && (subtype.equals("")))  subtype  = null;


                    //
                    // IPV4をもつNIFを列挙する
                    //

                    mIpv4List = _enmLocalIpV4Address();

                    //
                    // 回線毎のIPアドレスを取得
                    //

                    mCellIp         = _getLocalIpV4Address(mIpv4List, mCell, ConnectionType.MOBILE);
                    mWifiIp         = _getLocalIpV4Address(mIpv4List, mWifi, ConnectionType.WIFI);
                    mBlueIp         = _getLocalIpV4Address(mIpv4List, mBlue, ConnectionType.BTOOTH);
                    mUsbIp          = _getLocalIpV4Address(mIpv4List, mUsb,  ConnectionType.USB);
                    mWapIp          = _getLocalIpV4Address(mIpv4List, mWap,  ConnectionType.TETHER);
                    mVpnIp          = _getLocalIpV4Address(mIpv4List, mVpn,  ConnectionType.VPN);

                    if (mVpnIp.equals(""))      mVpnIp  = _getLocalIpV4Address(mIpv4List, mVpn2,        ConnectionType.VPN);
                    if (mCellIp.equals(""))     mCellIp = _getLocalIpV4Address(mIpv4List, mCell_tmp ,   ConnectionType.MOBILE);
                    if (mWifiIp.equals(""))     mWifiIp = _getLocalIpV4Address(mIpv4List, mWifi_tmp,    ConnectionType.WIFI);
                    if (mBlueIp.equals(""))     mBlueIp = _getLocalIpV4Address(mIpv4List, mBlue_tmp,    ConnectionType.BTOOTH);
                    if (mVpnIp.equals(""))      mVpnIp  = _getLocalIpV4Address(mIpv4List, mVpn_tmp,     ConnectionType.VPN);

                    info.mCell      = !Objects.requireNonNull(mCellIp).equals("");
                    info.mWifi      = !Objects.requireNonNull(mWifiIp).equals("");
                    info.mBluetooth = !Objects.requireNonNull(mBlueIp).equals("");
                    info.mUsb       = !Objects.requireNonNull(mUsbIp).equals("");
                    info.mVpn       = !Objects.requireNonNull(mVpnIp).equals("");
                    info.mWap       = !Objects.requireNonNull(mWapIp).equals("");

                    //
                    // ゲートウェイアドレス
                    //

                    if (gateway != null)
                    {
                        LinkProperties linkProperties = conn.getLinkProperties(gateway);

                        if (linkProperties != null)
                        {
                            if (hasDefaultRoute(linkProperties))
                            {
                                mDeviceName = linkProperties.getInterfaceName();
                                linkProperties.getLinkAddresses();
                                info.mIp = _getLocalIpV4Address(mIpv4List, linkProperties.getInterfaceName(), ConnectionType.OTHER);
                            }
                        }
                    }

                    //
                    // 接続タイプ別文字列設定（現状は１つのトランスポートのみ）
                    //

                    if (gatewayCap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    {
                        Log.w(TAG,"TRANSPORT_CELLULAR");

                        if (mCellIp.equals(""))
                        {
                            mCellIp     = info.mIp;
                            mCell_tmp   = _deviceName(mDeviceName);
                        }

                        info.mCell      = true;
                        info.mType      = ConnectionType.MOBILE;
                        info.mMainOut   = ConnectionType.MOBILE;
                        info.mExtraInfo = (extraInf != null) ? extraInf : "";
                        info.mSubType   = (subtype != null) ? subtype : "";
                    }
                    else if (gatewayCap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    {
                        Log.w(TAG,"TRANSPORT_WIFI");

                        if (mWifiIp.equals(""))
                        {
                            mWifiIp     = info.mIp;
                            mWifi_tmp   = _deviceName(mDeviceName);
                        }

                        if(extraInf == null)
                        {
                            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            extraInf = wifiInfo.getSSID();
                        }

                        info.mWifi      = true;
                        info.mType      = ConnectionType.WIFI;
                        info.mMainOut   = ConnectionType.WIFI;
                        info.mExtraInfo = (extraInf != null) ? extraInf : "";
                        info.mSubType   = (subtype  != null) ? subtype  : "SSID";

                    }
                    else if (gatewayCap.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
                    {
                        Log.w(TAG,"TRANSPORT_BLUETOOTH");

                        if (mBlueIp.equals(""))
                        {
                            mBlueIp     = info.mIp;
                            mBlue_tmp   = _deviceName(mDeviceName);
                        }

                        info.mBluetooth = true;
                        info.mType      = ConnectionType.BTOOTH;
                        info.mMainOut   = ConnectionType.BTOOTH;
                        info.mExtraInfo = (extraInf != null) ? extraInf : "BLT-Line sharing";
                        info.mSubType   = (subtype  != null) ? subtype  : "BLUE";
                    }
                    else if (gatewayCap.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    {
                        Log.w(TAG,"TRANSPORT_VPN");

                        if (mVpnIp.equals(""))
                        {
                            mVpnIp     = info.mIp;
                            mVpn_tmp   = _deviceName(mDeviceName);
                        }

                        info.mVpn       = true;
                        info.mType      = ConnectionType.VPN;
                        info.mMainOut   = ConnectionType.VPN;
                        info.mExtraInfo = (extraInf != null) ? extraInf : "";
                        info.mSubType   = (subtype  != null) ? subtype  :"VPN";
                    }
                }
                else
                {
                    Log.w(TAG,"TRANSPORT_NONE");

                    // 回線なし

                    info.mCell      = false;
                    info.mWifi      = false;
                    info.mBluetooth = false;
                    info.mUsb       = false;
                    info.mVpn       = false;
                    info.mWap       = false;

                    mCellIp         = "";
                    mWifiIp         = "";
                    mBlueIp         = "";
                    mUsbIp          = "";
                    mVpnIp          = "";
                    mWapIp          = "";

                    info.mIp        = "";
                    info.mExtraInfo = "";
                    info.mSubType   = "";
                    info.mDevice    = "";
                }

                //
                // 通知バーの更新
                //

                if (!info.mWap)
                {
                    if (phase <= 1)
                    {
                        if (info.mTether)
                        {
                            startForeground(notifyMessage.NOTIFY_TETHER_OFF);
                            tetherOff();
                            info.mTether = false;
                        }
                    }
                }
                else
                {
                    info.mType          = ConnectionType.TETHER;

                    if (!info.mTether)
                    {
                        startForeground(notifyMessage.NOTIFY_TETHER_ON);
                        info.mTether        = true;
                    }
                }

                mStatus.update();

                _changeTetherTime();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /*********************************************************************************************
     *
     *  指定したデバイス名を持つIPアドレスを取得する
     *
     * @param deviceList    IPV4デバイス列挙リスト
     * @param deviceName    インターフェース名
     *
     * @return  IPアドレス
     *
     ********************************************************************************************/

    private String _getLocalIpV4Address(List<AtomDevice> deviceList, String deviceName, ConnectionType type)
    {
        if ((deviceName == null) || (deviceList == null)) return "";

        for(AtomDevice device: deviceList)
        {
            if (deviceName.equals("")&&(device.isFound())) continue;

            if (deviceName.equals("") || device.getName().contains(deviceName))
            {
                device.found();

                if (type != ConnectionType.OTHER)
                {
                    device.setType(type);
                }

                return device.getIp();
            }
        }

        return "";

    }

    /**********************************************************************************************
     *
     *  IPアドレスを持つデバイスを列挙する
     *
     * @return  正常：デバイスリスト
     *
     *********************************************************************************************/

    private ArrayList<AtomDevice> _enmLocalIpV4Address()
    {
        ArrayList<AtomDevice> deviceList = new ArrayList<>();

        try
        {
            int index = 0;

            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {

                NetworkInterface nif = en.nextElement();

                int pos = 0;
                for (Enumeration<InetAddress> enumIpAdder = nif.getInetAddresses(); enumIpAdder.hasMoreElements();) {

                    InetAddress inetAddress = enumIpAdder.nextElement();

                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
                    {
                        AtomDevice device       = new AtomDevice();

                        device.setId(index);
                        device.setName(nif.getName());
                        device.setIp(inetAddress.getHostAddress());
                        device.setMask(nif.getInterfaceAddresses().get(pos).getNetworkPrefixLength());

                        InetAddress broadcast = nif.getInterfaceAddresses().get(pos).getBroadcast();

                        if (broadcast != null)  device.setBcast(broadcast.getHostAddress());
                        else                    device.setBcast("");

                        device.found();

                        deviceList.add(device);

                        Log.e(TAG,"["+device.getId()+"] IP:" + device.getIp()  + "/"+device.getMask() +" "+device.getBcast() + " " + _deviceName(nif.getName()) + " "+ device.getName());

                        index++;
                    }

                    pos++;
                }
            }
        }
        catch (Exception ex)
        {
            Log.e(TAG, ex.toString());
        }

        return deviceList;
    }

    /**********************************************************************************************
     *
     *  テザリング開始
     *
     *********************************************************************************************/

    private void tetherOn()
    {
        mWAp.startTethering();

        //
        // コネクション通知が来ない場合があるので周期監視をしばらく実施
        //

        startConnectionCheckCycle();
    }

    /**********************************************************************************************
     *
     *  テザリング停止
     *
     *********************************************************************************************/

    private void tetherOff()
    {
        mWAp.stopTethering();

        //
        // コネクション通知が来ない場合があるので周期監視をしばらく実施
        //

        startConnectionCheckCycle();
    }

    /**********************************************************************************************
     *
     * UIの準備完了
     *
     ********************************************************************************************/

    public void UI_Ready()
    {
        synchronized (mServiceLock)
        {
            mStatus.update(true);
        }
    }

    /*******************************************************************************************
     *
     *  変化点から１秒数期で３回だけ設足を確認する
     *
     ******************************************************************************************/

    private  void startConnectionCheckCycle()
    {
        synchronized (mHandleLock)
        {
            // 全３回確認（0.5Sec->check->1Sec->check->1Sec->check)

            mHandleCnt = mHandleCntMax;

            // 動作中カウンタを破棄
            mHandle.removeCallbacks(mConnectionProcess);

            // 監視開始
            mHandle.postDelayed(mConnectionProcess, mHandleCycleMsec);
        }
    }

    /*******************************************************************************************
     *
     *  ユーザ補助設定画面を立ち上げる
     *
     ******************************************************************************************/

    public void startAccessibility()
    {
        if ((mStatus != null) &&(!mStatus.getInfo().mPtt))
        {
            Log.w(TAG,"start Accessibility service ");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**********************************************************************************************
     *
     * テザリング時間監視タイマーの更新
     *
     *********************************************************************************************/

    private void _changeTetherTime()
    {
        if (mStatus.getInfo().mTether)
        {
            _startTetherTimer();
        }
        else
        {
            _stopTetherTimer();
        }
    }

    /**********************************************************************************************
     *
     * テザリング時間監視タイマー開始
     *
     *********************************************************************************************/

    private void _startTetherTimer()
    {
        if ((mTetherDuration > 0) && (!mTetherTimerFlag))
        {
            // 開始時間
            mTetherStartTime = SystemClock.elapsedRealtime();

            mTetherStartNsec    = System.nanoTime();

            mTetherStopCnt      = 0;

            Log.w(TAG, "start Tethering Time "+mTetherStartTime);

            // 開始中
            mTetherTimerFlag = true;

            // 一秒後
            mTetherTmpTime  = mTetherStartTime+1000;

            // １秒タイマー開始
            mTetherStopHandler.postDelayed(mTimerProcess, 1000);
        }
    }

    /**********************************************************************************************
     *
     * テザリング時間監視タイマー停止
     *
     *********************************************************************************************/

    private void _stopTetherTimer()
    {
        // 停止通知
        AtomStatus.AtomInfo info = mStatus.getInfo();
        info.mTimerRatio    = 0.0f;
        info.mTimerSec      = mTetherDuration/1000;

        Log.w(TAG, "stop Tethering Time "+info.mTimerRatio + " " + info.mTimerSec);
        mStatus.update();

        if (mTetherTimerFlag)
        {
            // タイマー停止
            mTetherStopHandler.removeCallbacks(mTimerProcess);
            mTetherTimerFlag = false;
        }
    }

    /**********************************************************************************************
     *
     * テザリング停止ハンドラー（１秒周期）で起動する
     *
     *********************************************************************************************/

    private final Runnable mTimerProcess = new Runnable()
    {
        @Override
        public void run()
        {
            synchronized (mServiceLock)
            {
                if (!mTetherTimerFlag) return;

                // 現在時刻
                //long current = System.nanoTime();
                long current = SystemClock.elapsedRealtime();

                long nsec = System.nanoTime();

                mTetherStopCnt++;

                Log.w(TAG, "Tethering Time["+mTetherStopCnt+"] "+(current-mTetherStartTime)/1000 + " sec [" + (nsec -mTetherStartNsec)/1000000000L + " sec]");

                // 経過時間
                long delta = current - mTetherStartTime;

                // デルタが負値はエラーなのでタイマーを解除する

                if ((delta > 0) && (mTetherDuration > delta))
                {
                    // 停止時価に満たない場合

                    AtomStatus.AtomInfo info = mStatus.getInfo();
                    info.mTimerRatio = (float) delta / (float) mTetherDuration;
                    delta = mTetherDuration - delta;
                    info.mTimerSec = (delta + 500) / 1000;

                    mStatus.update();

                    int loop = 30;

                    do
                    {
                        // 次１秒周期
                        mTetherTmpTime += 1000;

                        if (mTetherTmpTime - mTetherStartTime > mTetherDuration)
                        {
                            Log.e(TAG, "next time over");
                            mTetherTmpTime = mTetherStartTime+ mTetherDuration;
                        }

                        delta = (mTetherTmpTime - current);

                        if (loop <=0)
                        {
                            Log.e(TAG," timer err / delta " + delta + " / " + mTetherTmpTime + " / " + current );
                            delta =1000;
                            mTetherTmpTime = current+1000;
                            break;
                        }
                        loop--;
                    }
                    while (delta < 0);

                    if (delta > 0)
                    {
                        if (delta > 1000) delta = 1000;

                        // １秒タイマー
                        mTetherStopHandler.postDelayed(mTimerProcess, delta);
                        return;
                    }
                }

                // 停止時刻を過ぎた

                AtomStatus.AtomInfo info = mStatus.getInfo();
                info.mTimerRatio = 0.0f;
                info.mTimerSec = mTetherDuration/1000;

                mStatus.update();

                // テザリング停止
                tetherOff();

                // タイマー停止
                mTetherTimerFlag = false;
            }
        }
    };


    /*********************************************************************************************
     *
     *  H/Wキーの処理（利用したキーは下流に渡さず、ここで握りつぶす）
     *
     * @param e     KeyEvent
     * @return      TRUE　下流に渡さない、　FALES　下流に渡す
     *
     ********************************************************************************************/

    public boolean KeyCheck(KeyEvent e)
    {
        int code = e.getKeyCode();
        int acttion = e.getAction();

        if (code == mPressKeyCode)
        {
            Log.w(TAG,"PRESS PTT " + (e.getAction() == ACTION_DOWN ? "down": "up"));

            if (e.getAction() == ACTION_DOWN)
            {
                pressPtt(true);
            }
            else
            {
                pressPtt(false);
            }
            return true;

        }
        else if (e.getKeyCode() == KEYCODE_VOLUME_UP)
        {
            if (e.getAction() == ACTION_DOWN)
            {
                Log.w(TAG, "VOLUP DWON");
                return pressVolUp(true);
            }
            else
            {
                Log.w(TAG, "VOLUP UP");
                return pressVolUp(false);
            }
        }

        return false;
    }
    private final Handler           mPttHandler         = new Handler();

    private long                    mPttDownTIme        = 0;
    private boolean                 mPttPress           = false;
    private boolean                 mVolDownPress       = false;
    private boolean                 mVolDownOp          = false;
    private boolean                 mLongPressStart     = false;

    /**********************************************************************************************
     *
     * PTTロングPRESS計測タイマー
     *
     *********************************************************************************************/

    private final Runnable  mPttLongPressTimer = new Runnable()
    {
        @Override
        public void run()
        {
            synchronized (mServiceLock)
            {
                if (mLongPressStart)
                {
                    mLongPressStart = false;

                    //
                    // PTT-DOWN hold time  > mLongPressMsec
                    //

                    if ((mStatus.mUiUsed) && (mStatus.mUiForeground))
                    {
                        //Log.w(TAG, "onPttLongPress");
                        if (mListener != null) mListener.OnPttLongPress();
                    }
                }
            }
        }
    };

    /**********************************************************************************************
     *
     * PTTボタンのDOWN/UP
     *
     *********************************************************************************************/

    private void pressPtt(boolean down)
    {
        if (mListener == null) return;

        long current = SystemClock.elapsedRealtime();

        if (down)
        {
            //
            // PTT-DOWN
            //

            if (!mPttPress)
            {
                if (current - mPttDownTIme > mPressLongPress)
                {
                    Log.w(TAG, "PRESS PTT DOWN EXECUTE");
                    //
                    // PTT UP->DOWN & Previous Down > mLongPressMsec
                    //

                    mPttDownTIme = current;

                    synchronized (mServiceLock)
                    {
                        // UIがBGの場合は無視する

                        if (isBackGroundUI()) return;
                    }

                    mLongPressStart = true;

                    mPttHandler.postDelayed(mPttLongPressTimer, mPressLongPress);
                }

                mPttPress = true;
            }
        }
        else
        {
            //
            // PTT-UP
            //

            if (mPttPress)
            {
                if (current - mPttDownTIme < mPressLongPress)
                {
                    Log.w(TAG, "PRESS PTT UP EXECUTE");

                    if (mLongPressStart)
                    {
                        mLongPressStart = false;
                        mPttHandler.removeCallbacks(mPttLongPressTimer);
                    }

                    //
                    // PTT DOWN->UP & Duration < mLongPressMsec
                    //
                    if ((mStatus.mUiUsed) && (mStatus.mUiForeground))
                    {
                        if (mListener != null) mListener.OnPttPress();
                    }
                }
            }

            mPttPress = false;
        }
    }

    /**********************************************************************************************
     *
     * VOL-UPキーのダウン（押し）
     *
     *********************************************************************************************/

    private boolean  pressVolUp(boolean down)
    {
        boolean prevPress = mVolDownPress;

        if (down)
        {
            mVolDownPress   = true;

            if ((!prevPress)&& (mListener != null) && (mStatus.mUiUsed) && (mStatus.mUiForeground))
            {
                Log.w(TAG, "onVolPress Down");

                mVolDownOp = mListener.OnVolUp(true);
            }

            return mVolDownOp;
        }
        else
        {
            if ((prevPress)&& (mListener != null) && (mStatus.mUiUsed) && (mStatus.mUiForeground))
            {
                Log.w(TAG, "onVolPress Uo");

                mVolDownOp = mListener.OnVolUp(false);
            }
            mVolDownPress   = false;
            mVolDownOp      = false;
        }

        return false;
    }

    /*********************************************************************************************
     *
     *  テザリング自動オフタイマー
     *
     * @param timerDuration         自動オフ時間（ミリ秒）
     *
     *********************************************************************************************/

    public void autoOffTimer(long timerDuration)
    {
        synchronized (mServiceLock)
        {
            mTetherDuration = timerDuration;

            _stopTetherTimer();
            _changeTetherTime();
        }
    }
}
