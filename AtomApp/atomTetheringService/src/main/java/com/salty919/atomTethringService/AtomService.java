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
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

/**************************************************************************************************
 *
 *  ATOMサービス（フォアグランドサービス）
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

@SuppressWarnings("FieldCanBeLocal")
public class AtomService extends Service
{
    private static final String TAG = AtomService.class.getSimpleName();

    public static final String      KEY_pkgName     = "packageName";
    public static final String      KEY_clsName     = "className";
    public static final String      KEY_version     = "uiVersion";

    @SuppressWarnings("unused")
    private static final String     ACTION_PTT_UP   = "android.intent.action.PTT.up";
    private static final String     ACTION_PTT_DOWN = "android.intent.action.PTT.down";

    private final String title          = "AtomTethering ";
    private final String serviceVersion = BuildConfig.VERSION_NAME;
    private String uiVersion            = null;

    @SuppressWarnings("FieldCanBeLocal")
    private final String channelId = "Atom-service-channel";

    private Intent  mIntent             = null;

    private int     mPressKeyCode       = 286;
    private long    mPressLongPress     = 500;

    private notifyMessage   mNotifyId   = notifyMessage.NOTIFY_NOTING;

    private class Nif_device
    {
        String      mIp;
        int         mMask;
        String      mBroadcast;
        String      mDeviceName;
        boolean     mFound;
    }

    private List<Nif_device>    mIpv4List  = null;

    private final String    mCell       = "ccmni";
    private String          mCell_tmp   = null;
    private String          mCellIp     = "";

    private final String    mWifi       = "wlan";
    private String          mWifi_tmp   = null;
    private String          mWifiIp     = "";

    private final String    mBlue       = "bt-pan";
    private String          mBlue_tmp   = null;
    private String          mBlueIp     = "";

    private final String    mUsb        = "rndis";
    private String          mUsbIp      = "";

    private final String    mVpn        = "ppp";
    private final String    mVpn2       = "tun";
    private String          mVpn_tmp    = null;

    private String          mVpnIp      = "";

    private final String    mWap        = "ap";
    private String          mWapIp      = "";

    private String          mDeviceName = "";

    private boolean isBackGroundUI()  {return (!mStatus.mUiUsed) || (!mStatus.mUiForeground); }
    /**********************************************************************************************
     *
     * PTT KEYCODEのセット
     *
     * @param code      PTTキーコード
     *
     *********************************************************************************************/

    public  void setKeyCode(int code ) { mPressKeyCode = code;}

    /**********************************************************************************************
     *
     *  PTT LONGプレス検知時間
     *
     * @param msec  長押し時間（ミリ秒）
     *
     *********************************************************************************************/

    public  void setLongPressMsec(long msec) { mPressLongPress = msec; }

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

        /**  PTTのロングプレス通知（ユーザ側で自由に使ってよい）  */

        void   OnPttLongPress();
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
    private static  long[]      mTimerArray         = {0,5*60*1000L,10*60*1000L };
    private int                 mTimerIndex         = 0;

    private boolean             mRegisterReceiver   = false;

    private Vibrator            mVibrator           = null;

    private NotificationChannel mChannel = null;

    /*********************************************************************************************
     *
     *  テザリング自動切断タイマー配列の設定｛オプション｝
     *
     *  起動初期値は配列の第一要素から開始し、末尾まで行ったら先頭に戻る。
     *  自動切断時間はボリュームUPキーで切り替える
     *
     * @param timeArray     タイマー配列、NULLの場合は自動切断なし
     *
     *********************************************************************************************/

    public void setTimeArray( long[] timeArray)
    {
        if ((timeArray == null) || ((timeArray.length ==1)&&(timeArray[0]==0)))
        {
            mTimerArray = null;
        }
        else
        {
            mTimerArray = timeArray;
        }
    }

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

                Log.w(TAG, "onStartCommand[" + startId + "]" + intent.getStringExtra(KEY_pkgName) + " " + intent.getStringExtra(KEY_clsName));

                uiVersion = intent.getStringExtra(KEY_version);

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
        Log.w(TAG, "onBind " + intent.getStringExtra(KEY_pkgName) + " " + intent.getStringExtra(KEY_clsName));

        synchronized (mServiceLock)
        {
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

            Log.w(TAG,"connect Accessibility");

            AtomAccessibility.mLongPressMsec = mPressLongPress;
            AtomAccessibility.mPttCode       = mPressKeyCode;

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
            //Log.w(TAG," PTT INTENT ["+ mPttIntentEnable + "]" + intent);

            String action = intent.getAction();

            if (action == null) return;

            if (action.equals(ACTION_PTT_DOWN))
            {
                synchronized (mServiceLock)
                {
                    if (isBackGroundUI())
                    {
                        appForeground(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                }
            }
        }
    };

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


    //----------------------------------------------------------------------
    // ユーザ補助コールバック
    //----------------------------------------------------------------------

    private final AtomPttInterface mPressListener = new AtomPttInterface()
    {
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

        @Override
        public boolean onPttPress()
        {
            //Log.w(TAG,"onPttPress");

            synchronized (mServiceLock)
            {
                // UIがFGの場合はTRUEを戻す
                //
                // FALSEを戻した場合はonPttClickやonPttLongPressは発動しない
                // (BGだとテザリング切り替えは動かさない）

                return !isBackGroundUI();

                // INTENTを使う前は
                // ここでBG->FGを処理していたが
                // UI起動機能をINTENT側にすべて任せる
            }
        }

        @Override
        public void onPttLongPress()
        {
            if ((mStatus.mUiUsed) && (mStatus.mUiForeground))
            {
                //Log.w(TAG, "onPttLongPress");
                if (mListener != null) mListener.OnPttLongPress();
            }
        }

        @Override
        public void onPttClick()
        {
            if ((mStatus.mUiUsed) && (mStatus.mUiForeground))
            {
                //Log.w(TAG, "onPttClick");
                // テザリング切り替え
                tetheringToggle();
            }
        }

        @Override
        public  boolean onVolPress()
        {
            if ((mStatus.mUiUsed) && (mStatus.mUiForeground) && (mTimerArray != null))
            {
                //Log.w(TAG, "onVolPress");

                mTimerIndex++;
                if (mTimerIndex >= mTimerArray.length) mTimerIndex = 0;

                mTetherDuration = mTimerArray[mTimerIndex];

                Log.w(TAG, " TIMER INDEX:" + mTimerIndex + " Duration " + mTetherDuration);

                synchronized (mServiceLock)
                {
                    _stopTetherTimer();
                    _changeTetherTime();
                }

                return true;
            }

            return false;
        }
    };

    /*********************************************************************************************
     *
     *  テザリング切り替え
     *
     ********************************************************************************************/

    private void tetheringToggle()
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
    private void appForeground(int flag)
    {
        Log.w(TAG, "appForeground ");

        try
        {
            mVibrator.vibrate(VibrationEffect.createOneShot(150,10));

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

                    mCellIp         = _getLocalIpV4Address(mIpv4List, mCell);
                    mWifiIp         = _getLocalIpV4Address(mIpv4List, mWifi);
                    mBlueIp         = _getLocalIpV4Address(mIpv4List, mBlue);
                    mUsbIp          = _getLocalIpV4Address(mIpv4List, mUsb);
                    mWapIp          = _getLocalIpV4Address(mIpv4List, mWap);
                    mVpnIp          = _getLocalIpV4Address(mIpv4List, mVpn);

                    if (mVpnIp.equals(""))      mVpnIp  = _getLocalIpV4Address(mIpv4List, mVpn2);
                    if (mCellIp.equals(""))     mCellIp = _getLocalIpV4Address(mIpv4List, mCell_tmp);
                    if (mWifiIp.equals(""))     mWifiIp = _getLocalIpV4Address(mIpv4List, mWifi_tmp);
                    if (mBlueIp.equals(""))     mBlueIp = _getLocalIpV4Address(mIpv4List, mBlue_tmp);
                    if (mVpnIp.equals(""))      mVpnIp  = _getLocalIpV4Address(mIpv4List, mVpn_tmp);

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
                                info.mIp = _getLocalIpV4Address(mIpv4List, linkProperties.getInterfaceName());
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

    private String _getLocalIpV4Address(List<Nif_device> deviceList, String deviceName)
    {
        if ((deviceName == null) || (deviceList == null)) return "";

        for(Nif_device device: deviceList)
        {
            if (deviceName.equals("")&&(device.mFound)) continue;

            if (deviceName.equals("") || device.mDeviceName.contains(deviceName))
            {
                device.mFound = true;

                return device.mIp;
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

    private List<Nif_device> _enmLocalIpV4Address()
    {
        List<Nif_device> deviceList = new ArrayList<>();

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
                        Nif_device device       = new Nif_device();

                        device.mDeviceName      = nif.getName();
                        device.mIp              = inetAddress.getHostAddress();
                        device.mMask            = nif.getInterfaceAddresses().get(pos).getNetworkPrefixLength();

                        InetAddress broadcast = nif.getInterfaceAddresses().get(pos).getBroadcast();

                        if (broadcast != null)  device.mBroadcast = broadcast.getHostAddress();
                        else                    device.mBroadcast = null;

                        if (device.mIp == null) device.mIp = "";
                        if (device.mDeviceName == null) device.mDeviceName = "";

                        device.mFound           = false;

                        deviceList.add(device);

                        Log.e(TAG,"["+index+"] IP:" + device.mIp  + "/"+device.mMask +" "+device.mBroadcast + " " + _deviceName(nif.getName()) + " "+ nif.getName());

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

            // 初回は遅延は半分
            mHandle.postDelayed(mConnectionProcess, mHandleCycleMsec/2);
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
}
