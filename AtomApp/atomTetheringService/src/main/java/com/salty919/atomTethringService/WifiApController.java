package com.salty919.atomTethringService;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.android.dx.Code;
import com.android.dx.DexMaker;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/*************************************************************************************************
 *
 *  WIFIアクセスポイント制御クラス(OREO)
 *
 *  非公開API（Hidden属性のメソッド）を切替や監視に使うので、永続的な動作保証はできない
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

@SuppressWarnings("unchecked")
@RequiresApi(api = Build.VERSION_CODES.O)
class WifiApController
{
    private static final int TETHERING_WIFI      = 0;

    private static final String TAG = WifiApController.class.getSimpleName();

    private final Handler               mHandler            = new Handler();
    private       Object                mTetherCallback     = null;

    @SuppressWarnings("FieldCanBeLocal")
    private TetherCallbackMaker         mTetherCallBackMaker = null;

    private final ConnectivityManager   mConnectivityManager;
    private final WifiManager           mWifiManager;

    private Method                      mTetherStart;
    private Method                      mTetherStop;
    private Method                      mWifiApCheck;

    private boolean                     mEnable;

    /*********************************************************************************************
     *
     *  コンストラクタ 基本的に破棄はしない
     *
     * @param context       アプリコンテキスト
     *
     *********************************************************************************************/

    @SuppressLint("PrivateApi")
    WifiApController(Context context)
    {
        // 接続管理
        mConnectivityManager            = context.getSystemService(ConnectivityManager.class );

        // WIFI管理
        mWifiManager                    = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        try
        {
            //
            // テザリングコールバック(OnStartTetheringCallback継承)クラスを作る
            //

            mTetherCallBackMaker               = new TetherCallbackMaker(context);
            Class<?> tetheringCallbackClazz    = mTetherCallBackMaker.getCallBackClass();

            //
            // コンストラクタを呼び出しコールバックインスタンスを生成
            //

            Constructor constructor = tetheringCallbackClazz.getDeclaredConstructor(int.class);
            mTetherCallback = constructor.newInstance(0);

            //
            // ConnectivityManager#startTethering(int , boolean, OnStartTetheringCallback, handler)　メソッド
            //

            Class callbackClass = Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
            mTetherStart = Objects.requireNonNull(mConnectivityManager).getClass().getDeclaredMethod("startTethering", int.class, boolean.class, callbackClass, Handler.class);

            //
            // ConnectivityManager#stopTethering(int)　メソッド
            //

            mTetherStop  = Objects.requireNonNull(mConnectivityManager).getClass().getDeclaredMethod("stopTethering",int.class);

            //
            // WifiManager#isWifiApEnable() メソッド
            //

            mWifiApCheck = Objects.requireNonNull(mWifiManager).getClass().getDeclaredMethod("isWifiApEnabled");
            mWifiApCheck.setAccessible(true);

            mEnable      = true;

        }
        catch (Exception e)
        {
            Log.e(TAG,"framework Tethering Method Exception");
            e.printStackTrace();
        }
    }

    /**********************************************************************************************
     *
     *  WIFI-AccessPoint(テザリング）が有効かどうかの判定
     *
     * @return  true:有効　false;無効
     *
     *********************************************************************************************/

    boolean isWifiAccessPoint()
    {
        boolean wifi_ap = false;

        try
        {
            //
            // (非公開）WIFIマネージャにWIFI-AP(テザリング）かどうか確認する
            //

            wifi_ap = (Boolean) mWifiApCheck.invoke(mWifiManager);
        }
        catch (Exception e)
        {
            Log.e(TAG,"ERROR isWifiApEnabled Exception");
        }

        return wifi_ap;
    }

    /**********************************************************************************************
     *
     *  テザリングON
     *
     *********************************************************************************************/

    void startTethering()
    {
        if (mEnable)
        {
            try
            {
                //
                // （非公開）コネクティビティマネージャにテザリング開始要求
                //
                // mTetherCallback　コールバックNULLはNGなので絶対に入れる事

                mTetherStart.invoke(mConnectivityManager, TETHERING_WIFI, false, mTetherCallback, mHandler);
            }
            catch (Exception e)
            {
                Log.e(TAG, "ERROR startTetheringMethod Exception");
                e.printStackTrace();
            }
        }
    }

    /**********************************************************************************************
     *
     *  テザリングOFF
     *
     *********************************************************************************************/

    void stopTethering()
    {
        if (mEnable)
        {
            try
            {
                //
                // （非公開）コネクティビティマネージャにテザリング停止要求
                //

                mTetherStop.invoke(mConnectivityManager, TETHERING_WIFI);
            }
            catch (Exception e)
            {
                Log.e(TAG, "ERROR stopTetheringMethod Exception");
            }
        }
    }

    /*********************************************************************************************
     *
     *  テザリング通知（機能しない）→　使わない
     *
     ********************************************************************************************/

    @SuppressWarnings({"UnnecessaryInterfaceModifier", "unused"})
    private interface listener
    {
        public abstract void onTetheringStarted();
        public abstract void onTetheringFailed();
    }

    /********************************************************************************************
     *
     *  tethering コールバッククラスの生成
     *
     *********************************************************************************************/

    final class TetherCallbackMaker
    {
        private  final String TAG = TetherCallbackMaker.class.getSimpleName();

        private Class <?>               mTetheringCallbackClazz;
        private final DexMaker dexMaker = new DexMaker();

        /******************************************************************************************
         *
         *  コンストラクタ     TetheringCallbackクラスを生成
         *
         *  本クラスはstartTetheringMethodの必須引数であるOnStartTetheringCallbackをクラス定義を
         *  生成する
         *
         * @param context   アプリケーションコンテキスト
         *
         *****************************************************************************************/

        @SuppressLint("PrivateApi")
        TetherCallbackMaker(Context context)
        {
            Class callbackClass;

            // ConnectivityManager$OnStartTetheringCallbackクラスを取得
            try
            {
                callbackClass = Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
            }
            catch (ClassNotFoundException e)
            {
                Log.e(TAG,"OnStartTetheringCallback（not found) Exception ");
                return;
            }

            // TetheringCallbackクラス（ConnectivityManager$OnStartTetheringCallback継承）

            TypeId<?> startTetheringCallbackId  = TypeId.get(callbackClass);
            TypeId<?> tetheringCallbackId       = TypeId.get("LTetheringCallback;");
            dexMaker.declare(tetheringCallbackId, "TetheringCallback.generated", Modifier.PUBLIC, startTetheringCallbackId);

            // クラスを生成すう
            generateConstructorWorking(tetheringCallbackId,startTetheringCallbackId);

            try
            {
                ClassLoader loader = dexMaker.generateAndLoad(TetherCallbackMaker.class.getClassLoader(),context.getCodeCacheDir());

                mTetheringCallbackClazz = loader.loadClass("TetheringCallback");

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        /*******************************************************************************************
         *
         * ここの動作はよくわからんが、ConnectivityManager$OnStartTetheringCallbackを継承した
         * テザリングコールバックのコンストラクタの動作を記述しているはずである
         *
         * @param tetheringCallbackId           生成クラスID
         * @param startTetheringCallbackId      継承クラスID
         *
         *******************************************************************************************/

        @SuppressWarnings("unchecked")
        private void generateConstructorWorking(TypeId<?> tetheringCallbackId, TypeId<?> startTetheringCallbackId)
        {
            final MethodId<?, ?> superConstructor = startTetheringCallbackId.getConstructor();

            MethodId<?, ?> constructor = tetheringCallbackId.getConstructor(TypeId.INT);

            Code constructorCode = dexMaker.declare(constructor, Modifier.PUBLIC);

            final Local thisRef = constructorCode.getThis(tetheringCallbackId);

            // 継承元を呼び出す
            constructorCode.invokeDirect(superConstructor, null, thisRef);

            // 戻り値なし
            constructorCode.returnVoid();
        }

        /******************************************************************************************
         *　
         * 　生成したクラス
         *
         * @return      コールバッククラス
         *
         ******************************************************************************************/

        Class<?> getCallBackClass()
        {
            return mTetheringCallbackClazz;
        }
    }
}
