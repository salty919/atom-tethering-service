package com.salty919.atomTethringService;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

/*************************************************************************************************
 *
 *  ATOM状態管理クラス     シングルトン実装
 *
 *  端末の回線情報を含む機器情報を格納するクラス（ここは永続層ではない）
 *
 *  オブザーバ：　状態の変化点での通知機能をもつ（ただし登録者は１インスタンス）
 *  インテント：  アクティビティ（UI）の生成インテント（情報バンドル）を生成できる
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

@SuppressWarnings("SpellCheckingInspection")
public class AtomStatus
{
    @SuppressWarnings("unused")
    private final String TAG = AtomStatus.class.getSimpleName();

    //
    // 排他制御
    //

    private final static Object     mLock               = new Object();

    //
    // UIの状態
    //

    // TRUE：表示中
    public boolean                  mUiForeground       = false;
    // TRUE: 生成済み
    public boolean                  mUiActive           = false;

    public final boolean            mUiUsed             = true;

    //
    // bundle key
    //

    /*
    private static final String     KEY_bundle      = "bundle";
    private static final String     KEY_sequence    = "sequence";
    private static final String     KEY_tether      = "tether";
    private static final String     KEY_type        = "type";
    private static final String     KEY_ip          = "ip";
    private static final String     KEY_battery     = "battery";
    private static final String     KEY_ptt         = "ptt";
    private static final String     KEY_wifiap      = "wifiap";
    private static final String     KEY_timeratio   = "timerratio";
    private static final String     KEY_timesec     = "timersec";
    */

    //
    // 生成初回用判別
    //

    private boolean                 mInitialized    = false;

    /**********************************************************************************************
     *
     *  内部クラス（公開）   端末情報構造を格納
     *
     *********************************************************************************************/

    public class AtomInfo
    {
        private final String TAG = AtomInfo.class.getSimpleName();

        private final Object   mLock    = new Object();

        AtomInfo(String name) { mName = name; }

        //-------------------------------------------------------------------------------------
        // ステート名称（デバック用）
        //-------------------------------------------------------------------------------------

        final String                mName;

        //-------------------------------------------------------------------------------------
        // 状態（起動初期値）
        //-------------------------------------------------------------------------------------

        // テザリング要求の有無（外部から実行もある）
        public boolean              mTether         = false;

        // 接続タイプ
        public ConnectionType       mType           = ConnectionType.NONE;

        // 接続IPアドレス（V4）
        public String               mIp             = "";

        // バッテリーレベル(0..100)
        public int                  mBatteryLevel   = -1;

        // テザリング状態 true:テザリング中
        public boolean              mWifiApFromWM   = false;

        public boolean              mPtt            = false;

        public float                mTimerRatio     = 0.0f;
        public long                 mTimerSec       = 0;

        //-------------------------------------------------------------------------------------
        // 状態の更新
        //-------------------------------------------------------------------------------------

        void  update(AtomInfo info)
        {
            if (info == null) return;

            synchronized (mLock)
            {
                mTether         = info.mTether;
                mType           = info.mType;
                mIp             = info.mIp;
                mBatteryLevel   = info.mBatteryLevel;
                mWifiApFromWM   = info.mWifiApFromWM;
                mPtt            = info.mPtt;
                mTimerRatio     = info.mTimerRatio;
                mTimerSec       = info.mTimerSec;

                Log.w(TAG, "---------------------------------------------");
                Log.w(TAG, "TETHER [" + mName + "] " + mTether);
                Log.w(TAG, "CTYPE  [" + mName + "] " + mType);
                Log.w(TAG, "IPADDR [" + mName + "] " + mIp);
                Log.w(TAG, "BATLEV [" + mName + "] " + mBatteryLevel);
                Log.w(TAG, "WIFIWP [" + mName + "] " + mWifiApFromWM);
                Log.w(TAG, "PTTACC [" + mName + "] " + mPtt);
                Log.w(TAG, "TIMERT [" + mName + "] " + mTimerRatio);
                Log.w(TAG, "TIMESC [" + mName + "] " + mTimerSec);
            }
        }

        //-------------------------------------------------------------------------------------
        // 状態の比較（変化点検知）
        //-------------------------------------------------------------------------------------

        boolean     equal(AtomInfo info)
        {
            if (info == null) return false;

            boolean valid = true;

            synchronized (mLock)
            {
                if (mInitialized)
                {
                    if (info.mTether != mTether)
                    {
                        valid = false;
                        Log.e(TAG, "TETHER [" + mName + "] " + mTether + " <= " + info.mTether);
                    }

                    if (info.mType != mType)
                    {
                        valid = false;
                        Log.e(TAG, "CTYPE  [" + mName + "] " + mType.mStr + " <= " + info.mType.mStr);
                    }

                    if (!info.mIp.equals(mIp))
                    {
                        valid = false;
                        Log.e(TAG, "IPADDR [" + mName + "] " + mIp + " <= " + info.mIp);
                    }

                    if (info.mBatteryLevel != mBatteryLevel)
                    {
                        valid = false;
                        Log.e(TAG, "BATLEV [" + mName + "] " + mBatteryLevel + " <= " + info.mBatteryLevel);
                    }

                    if (info.mWifiApFromWM != mWifiApFromWM)
                    {
                        valid = false;
                        Log.e(TAG, "WIFIAP [" + mName + "] " + mWifiApFromWM + " <= " + info.mWifiApFromWM);
                    }

                    if (info.mPtt != mPtt)
                    {
                        valid = false;
                        Log.e(TAG, "PTTACC [" + mName + "] " + mPtt + " <= " + info.mPtt);
                    }

                    if (info.mTimerRatio != mTimerRatio)
                    {
                        valid = false;
                        Log.e(TAG, "TIMERT [" + mName + "] " + mTimerRatio + " <= " + info.mTimerRatio);
                    }

                    if (info.mTimerSec != mTimerSec)
                    {
                        valid = false;
                        Log.e(TAG, "TIMESC [" + mName + "] " + mTimerSec + " <= " + info.mTimerSec);
                    }
                }
                else
                {
                    // 生成初回
                    valid = false;
                    mInitialized = true;
                }
            }

            return valid;
        }
    }

    private final Handler       mHandler        = new Handler();

    //
    // 端末情報（SET)
    //

    private final AtomInfo      mInfo             = new AtomInfo("Master");

    //
    // 端末情報(GET)
    //
    private final AtomInfo      mInfoObserver       = new AtomInfo("Display");

    //
    // 監視オブザーバ（状態変化通知 for UI）
    //

    public interface observer
    {
        void onNotify(AtomInfo info);
    }

    private observer            mObserver       = null;

    //
    // インテント発行先コンポーネント名（UI）
    //

    private  ComponentName      mComponentName;

    //
    // シングルトンインスタンス
    //

    private   static AtomStatus   sInstance     = null;

    //
    // 参照カウンタ
    //

    private int                 reference       = 0;

    /**********************************************************************************************
     *
     * （UI)　ステータス管理を取得する
     *
     * @param pkgName       パッケージ名
     * @param className     クラス名
     *
     * @return      ATOM状態管理オブジェクト
     *
     *********************************************************************************************/

    public static AtomStatus shardInstance(String pkgName, String className)
    {
        AtomStatus instance = AtomStatus.shardInstance();

        instance.mComponentName = new ComponentName(pkgName,className);

        return instance;
    }

    static AtomStatus shardInstance()
    {
        synchronized (mLock)
        {
            // 生成されていあければ生成する

            if (sInstance == null)
            {
                sInstance = new AtomStatus();

                Log.w(sInstance.TAG, "new AtomStatus");

                sInstance.reference = 1;
            }
            else
            {
                sInstance.ref();
            }
        }
        return sInstance;
    }

    /*********************************************************************************************
     *
     * （UI）　ステータス管理を破棄する
     *
     *********************************************************************************************/

    public void freeInstance()
    {
        unref();
    }

    /**********************************************************************************************
     *
     *  ステータスの参照アップ
     *
     *********************************************************************************************/

    private void ref()
    {
        synchronized (mLock)
        {
            reference++;
            Log.w(TAG, "ref "+  reference);
        }
    }

    /**********************************************************************************************
     *
     *  ステータスの参照ダウン
     *
     *********************************************************************************************/

    private void unref()
    {
        synchronized (mLock)
        {
            reference--;

            if (reference == 0)
            {
                Log.w(TAG,"free AtomStatus");
                sInstance = null;
            }
            else
            {
                Log.w(TAG,"unref "+ reference);
            }
        }
    }

    /**********************************************************************************************
     *
     * (UI) オブザーバー登録
     *
     * @param observer      通知先インスタンス
     *
     *********************************************************************************************/

    public void resisterObserver(observer observer)
    {
        mObserver = observer;
    }

    /**********************************************************************************************
     *
     * (UI) オブザーバー解除
     *
     *********************************************************************************************/

    public void unregisterObserver()
    {
        mObserver = null;
    }

    /**********************************************************************************************
     *
     * ステート更新
     *
     * 更新毎にオブザーバを呼び出すと通知が多発するので変化点のみ実施
     *
     *********************************************************************************************/

    void update()
    {
        // 変化点のみ
        update(false);
    }

    /**********************************************************************************************
     *
     * ステート更新
     *
     * 更新毎にオブザーバを呼び出すと通知が多発するので変化点のみ実施
     *
     * @param force      強制通知（）
     *
     *********************************************************************************************/

    void update(boolean force)
    {
        // 状態変化したか？
        if (!mInfo.equal(mInfoObserver))
        {
            // データをラッチ（次比較用）
            mInfoObserver.update(mInfo);

            notifyUI();
        }
        else if (force)
        {
            notifyUI();
        }
    }

    /**********************************************************************************************
     *
     *  UIに通知する
     *
     **********************************************************************************************/

    private void notifyUI()
    {
        //if (!mForeground) return;

        final observer _observer = mObserver;

        if (_observer != null)
        {
            mHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    // 監視リスナーを呼び出す
                    _observer.onNotify(mInfoObserver);
                }
            });
        }
    }
    /*********************************************************************************************
     *
     * 情報クラスを戻す
     *
     * @return 情報クラス
     *
     *********************************************************************************************/

    AtomInfo getInfo()
    {
        return mInfo;
    }

    /**********************************************************************************************
     *
     * アクティビティを起動するIntentの生成
     *
     * @param flag          INTENTフラグ
     *
     * @return  起動用インテント
     *
     *********************************************************************************************/

    Intent getNewIntent(int flag )
    {
        Intent intent;

        synchronized (mLock)
        {
            intent= new Intent();

            intent.setClassName(mComponentName.getPackageName(), mComponentName.getClassName());

            intent.setFlags(flag);

        }

        return intent;
    }
}
