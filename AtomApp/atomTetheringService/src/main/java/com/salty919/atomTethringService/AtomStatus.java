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
    boolean                         mUiForeground       = false;

    // TRUE: 生成済み
    @SuppressWarnings({"WeakerAccess", "unused"})
    boolean                         mUiActive           = false;

    // UI必須動作（ここをFALSEにするとUI起動なしでサービス単体になるが、操作がわかりずらい
    // falseでの動作は未検証です

    final   boolean                 mUiUsed             = true;

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
        public ConnectionType       mMainOut        = ConnectionType.NONE;

        // 接続付加情報
        @SuppressWarnings("WeakerAccess")
        public String               mExtraInfo      = "";

        @SuppressWarnings("WeakerAccess")
        public  String              mSubType        = "";

        // 接続IPアドレス（V4）
        public String               mIp             = "";

        public String               mDevice         = "";

        // バッテリーレベル(0..100)
        public int                  mBatteryLevel   = -1;

        // 出力IF     true:
        public boolean              mWap            = false;
        public boolean              mCell           = false;
        public boolean              mWifi           = false;
        public boolean              mVpn            = false;
        public boolean              mBluetooth      = false;
        public boolean              mUsb            = false;

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
                mMainOut        = info.mMainOut;
                mExtraInfo      = info.mExtraInfo;
                mSubType        = info.mSubType;
                mDevice         = info.mDevice;
                mIp             = info.mIp;
                mBatteryLevel   = info.mBatteryLevel;

                mWap            = info.mWap;
                mCell           = info.mCell;
                mWifi           = info.mWifi;
                mBluetooth      = info.mBluetooth;
                mUsb            = info.mUsb;
                mVpn            = info.mVpn;

                mPtt            = info.mPtt;
                mTimerRatio     = info.mTimerRatio;
                mTimerSec       = info.mTimerSec;

                Log.w(TAG, "---------------------------------------------");
                Log.w(TAG, "TETHER [" + mName + "] " + mTether);
                Log.w(TAG, "CTYPE  [" + mName + "] " + mType);
                Log.w(TAG, "MOUT   [" + mName + "] " + mMainOut );
                Log.w(TAG, "EXTINF [" + mName + "] " + mExtraInfo);
                Log.w(TAG, "STYPE  [" + mName + "] " + mSubType);
                Log.w(TAG, "DEVICE [" + mName + "] " + mDevice);
                Log.w(TAG, "IPADDR [" + mName + "] " + mIp);
                Log.w(TAG, "BATLEV [" + mName + "] " + mBatteryLevel);
                Log.w(TAG, "WIFIWP [" + mName + "] " + mWap);
                Log.w(TAG, "CELLUR [" + mName + "] " + mCell);
                Log.w(TAG, "WIFI   [" + mName + "] " + mWifi);
                Log.w(TAG, "BLUETH [" + mName + "] " + mBluetooth);
                Log.w(TAG, "USBTH  [" + mName + "] " + mUsb);
                Log.w(TAG, "VPN    [" + mName + "] " + mVpn);
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

                    if (info.mMainOut != mMainOut)
                    {
                        valid = false;
                        Log.e(TAG, "MOUT   [" + mName + "] " + mMainOut + " <= " + info.mMainOut);
                    }

                    if (!info.mExtraInfo.equals(mExtraInfo))
                    {
                        valid = false;
                        Log.e(TAG, "EXINF  [" + mName + "] " + mExtraInfo + " <= " + info.mExtraInfo);
                    }

                    if (!info.mSubType.equals(mSubType))
                    {
                        valid = false;

                        Log.e(TAG, "STYPE  [" + mName + "] " + mSubType + " <= " + info.mSubType);
                    }

                    if (!info.mIp.equals(mIp))
                    {
                        valid = false;
                        Log.e(TAG, "IPADDR [" + mName + "] " + mIp + " <= " + info.mIp);
                    }

                    if (!info.mDevice.equals(mDevice))
                    {
                        valid = false;
                        Log.e(TAG, "DEVICE [" + mName + "] " + mDevice + " <= " + info.mDevice);
                    }

                    if (info.mBatteryLevel != mBatteryLevel)
                    {
                        valid = false;
                        Log.e(TAG, "BATLEV [" + mName + "] " + mBatteryLevel + " <= " + info.mBatteryLevel);
                    }

                    if (info.mWap != mWap)
                    {
                        valid = false;
                        Log.e(TAG, "WIFIAP [" + mName + "] " + mWap + " <= " + info.mWap);
                    }

                    if (info.mCell != mCell)
                    {
                        valid = false;
                        Log.e(TAG, "CELLUR [" + mName + "] " + mCell+ " <= " + info.mCell);
                    }

                    if (info.mWifi != mWifi)
                    {
                        valid = false;
                        Log.e(TAG, "WIFI   [" + mName + "] " + mWifi + " <= " + info.mWifi);
                    }

                    if (info.mBluetooth != mBluetooth)
                    {
                        valid = false;
                        Log.e(TAG, "BLUETH [" + mName + "] " + mBluetooth + " <= " + info.mBluetooth);
                    }

                    if (info.mUsb != mUsb)
                    {
                        valid = false;
                        Log.e(TAG, "USBTH  [" + mName + "] " + mUsb + " <= " + info.mUsb);
                    }

                    if (info.mVpn != mVpn)
                    {
                        valid = false;
                        Log.e(TAG, "VPN    [" + mName + "] " + mVpn + " <= " + info.mVpn);
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

    private  ComponentName      mComponentName  = null;

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

        if (instance.mComponentName == null)
        {
            instance.mComponentName = new ComponentName(pkgName, className);
        }

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

                Log.w(sInstance.TAG, "new AtomStatus ref = 1");

                sInstance.reference = 1;
            }
            else
            {
                sInstance.ref();
            }
        }
        return sInstance;
    }


    public static AtomStatus setRecovery(String pkgName, String className)
    {
        if (sInstance.mComponentName == null)
        {
            sInstance.mComponentName = new ComponentName(pkgName, className);
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

    /*********************************************************************************************
     *
     *  UI生成/破棄     ※現状本フラグを参照する機能はない
     *
     * @param active    true:生成、　false:破棄
     *
     *********************************************************************************************/

    public void uiActive(boolean active)
    {
        mUiActive = active;
    }

    /**********************************************************************************************
     *
     * UIがFG/BGを記録する
     *
     * @param foreground        true;FG false:BG
     *
     *********************************************************************************************/

    public void uiState(boolean foreground)
    {
        mUiForeground = foreground;

        if (mUiForeground) update(true);
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
                Log.w(TAG,"free AtomStatus ref = 0");
                sInstance = null;
            }
            else
            {
                Log.w(TAG,"ref "+ reference);
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

    public AtomInfo getInfo()
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
