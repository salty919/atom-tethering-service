package com.salty919.atomTethringService;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

/**************************************************************************************************
 *
 *  ATOMユーザ補助サービス　（PTTキー監視）
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public class AtomAccessibility extends AccessibilityService
{
    private final String TAG = AtomAccessibility.class.getSimpleName();

    private final Object            mLock               = new Object();

    public interface KeyInterface
    {
        /** 機能が有効/無効に変化した通知         */
        void onReady(boolean enable);

        /** HW KEYが押された通知                 */
        boolean onKeyPress(KeyEvent e);
    }

    //------------------------------------------------------------------------------------
    //　内部変数
    //------------------------------------------------------------------------------------

    private static AtomAccessibility    sInstance       = null;
    private static KeyInterface         sListener       = null;
    private KeyInterface                mListener       = null;
    private boolean                     mServiceEnable  = false;
    private final Handler               mHandler        = new Handler();

    /**********************************************************************************************
     *
     * 静的呼び出し リスナー登録
     *
     * @param listener  コールバック先インスタンス
     *
     *********************************************************************************************/

    public static void setListener(KeyInterface listener)
    {
        sListener = listener;

        if (sInstance != null)
        {
            sInstance._setListener(sListener);
        }
    }

    private void _setListener(KeyInterface listener)
    {
        synchronized (mLock)
        {
            if (listener != null)
            {
                Log.w(TAG, "SET Listener ");
            }
            else
            {
                Log.w(TAG, "CLR Listener ");
            }
            mListener = listener;

            //
            // KEY監視有効/無効通知
            //

            postServiceReady();
        }
    }

    private void postServiceReady()
    {
        final KeyInterface _listener = mListener;
        final boolean  _ServiceEnable = mServiceEnable;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    Log.w(TAG, "Ready " + _ServiceEnable);
                    if (_listener != null) _listener.onReady(_ServiceEnable);
                }
            }
        });
    }

    /**********************************************************************************************
     *
     * AtomAccessibility#onCreate
     *
     * ユーザ補助がONにされた場合に呼ばれる
     *
     *********************************************************************************************/

    @Override
    public void onCreate()
    {
        Log.w(TAG,"onCreate");
        super.onCreate();

        sInstance = this;

        if (sListener!=null) mListener = sListener;
    }

    /**********************************************************************************************
     *
     * AtomAccessibility#onDestroy
     *
     * ユーザ補助がOFFにされた場合に呼ばれる
     *
     *********************************************************************************************/

    @Override
    public void onDestroy()
    {
        Log.w(TAG,"onDestroy");
        super.onDestroy();

        synchronized (mLock)
        {
            mServiceEnable = false;
            postServiceReady();
        }
    }

    /*********************************************************************************************
     *
     * ハードウェアKEYを監視する（アプリに渡る前に握りつぶす場合あり）
     *
     * @param e 発生イベント
     *
     * @return  true:アプリに渡さない　false;アプリに渡す
     *
     *********************************************************************************************/

    @Override
    public boolean onKeyEvent(KeyEvent e)
    {
        if (mListener != null)
        {
            if (mListener.onKeyPress(e))
                return true;
        }
        return super.onKeyEvent(e);
    }

    /**********************************************************************************************
     *
     * サービス接続　　AccessibilityServiceが有効になったら呼ばれる
     *
     *********************************************************************************************/

    @Override
    public void onServiceConnected()
    {
        synchronized (mLock)
        {
            Log.w(TAG, "onServiceConnected");

            mServiceEnable = true;

            postServiceReady();
        }
    }

    /**********************************************************************************************
     *
     * ユーザ補助イベント（未使用）
     *
     *********************************************************************************************/

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event)
    {
        Log.w(TAG, "onAccessibilityEvent "+  event.getEventType());
    }

    /**********************************************************************************************
     *
     * 中断（未使用）
     *
     *********************************************************************************************/

    @Override
    public void onInterrupt()
    {
        Log.w(TAG, "onInterrupt");
    }


}
