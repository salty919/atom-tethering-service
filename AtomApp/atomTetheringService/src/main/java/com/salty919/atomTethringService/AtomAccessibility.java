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
    private final Handler           mPttHandler         = new Handler();
    private boolean                 mServiceEnable      = false;

    public static int               mPttCode            = 286;
    public static long              mLongPressMsec      = 500;

    private long                    mPttDownTIme        = 0;
    private boolean                 mPttPress           = false;
    private boolean                 mLongPressStart     = false;

    //------------------------------------------------------------------------------------
    // 静的呼び出し用
    //------------------------------------------------------------------------------------

    private static AtomAccessibility sInstance    = null;
    private static AtomPttInterface         sListener   = null;

    /**********************************************************************************************
     *
     * 静的呼び出し リスナー登録
     *
     * @param listener  コールバック先インスタンス
     *
     *********************************************************************************************/

    public static void setListener(AtomPttInterface listener)
    {
        sListener = listener;

        if (sInstance != null)
        {
            sInstance._setListener(sListener);
        }
    }

    private void _setListener(AtomPttInterface listener)
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
        final AtomPttInterface _listener = mListener;
        final boolean  _ServiceEnable = mServiceEnable;

        mPttHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    Log.w(TAG, "Ready " + _ServiceEnable);
                    if (_listener != null) _listener.onReady(_ServiceEnable);
                }
            }
        });
    }

    private AtomPttInterface mListener = null;

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
        synchronized (mLock)
        {
            if (mListener != null)
            {
                if (e.getKeyCode() == mPttCode)
                {
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
                else if ((e.getKeyCode() == KEYCODE_VOLUME_UP) && (e.getAction() == ACTION_DOWN))
                {
                    if (mListener.onVolPress()) return true;
                }
            }
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
        Log.w(TAG, "onAccessibilityEvent");
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

    /**********************************************************************************************
     *
     * PTTロングPRESS計測タイマー
     *
     *********************************************************************************************/

    private final Runnable  mForegroundCancel = new Runnable()
    {
        @Override
        public void run()
        {
            synchronized (mLock)
            {
                if (mLongPressStart)
                {
                    mLongPressStart = false;

                    //
                    // PTT-DOWN hold time  > mLongPressMsec
                    //

                    if (mListener != null) mListener.onPttLongPress();
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
        //Log.w(TAG," PTT "+ down);

        if (mListener == null) return;

        long current = SystemClock.elapsedRealtime();

        if (down)
        {
            //Log.w(TAG, "DOWN");

            //
            // PTT-DOWN
            //

            if ((!mPttPress) || (current - mPttDownTIme > mLongPressMsec))
            {
                //
                // PTT UP->DOWN & Previous Down > mLongPressMsec
                //

                mPttDownTIme    = current;

                mPttPress = mListener.onPttPress();

                if (mPttPress)
                {
                    mLongPressStart = true;
                    mPttHandler.postDelayed(mForegroundCancel, mLongPressMsec);
                }
            }
        }
        else
        {
            //Log.w(TAG, "UP");

            //
            // PTT-UP
            //

            if ((mPttPress) && (current - mPttDownTIme < mLongPressMsec))
            {
                if (mLongPressStart)
                {
                    mLongPressStart = false;
                    mPttHandler.removeCallbacks(mForegroundCancel);
                }

                //
                // PTT DOWN->UP & Duration < mLongPressMsec
                //

                mListener.onPttClick();
            }

            mPttPress = false;
        }

    }
}
