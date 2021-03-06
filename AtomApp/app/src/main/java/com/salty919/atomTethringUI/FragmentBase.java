package com.salty919.atomTethringUI;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.salty919.atomTethringService.AtomService;
import com.salty919.atomTethringService.AtomStatus;

import java.util.Objects;

/*************************************************************************************************
 *
 *  フラグメント抽象クラス
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public abstract class FragmentBase extends Fragment implements GestureDetector.OnDoubleTapListener, GestureDetector.OnGestureListener
{

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public String TAG;

    /**********************************************************************************************
     *
     *  リスナー
     *
     *********************************************************************************************/

    public interface Listener
    {
        void onReady(FragmentBase fragment);
        void onPickupImage();
        void onSingleTapUp();
        void onDoubleTap();
        void onCancel();
    }

    public      boolean         mEnable             = false;
    protected   Context         mContext            = null;
    protected   Listener        mListener;
    protected   AtomService     mService            = null;
    protected   AtomPreference  mPreference         = null;
    private     GestureDetector mGestureDetector    = null;

    protected boolean           mRunning            = false;
    protected   View            mView               = null;

    /*********************************************************************************************
     *
     *  フラグメント：　Activityとの接続
     *
     * @param context   アプリケーションコンテキスト
     *
     ********************************************************************************************/

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        Log.w(TAG,"onAttach "+ this.hashCode()+ " "+ context.hashCode());

        mListener = (Listener) getActivity();
        mContext = context;
    }

    @Override
    public void onDetach()
    {
        Log.w(TAG,"onDetach "+ this.hashCode());
        super.onDetach();
        mListener = null;
        mContext = null;
        mEnable = false;
    }

    /*********************************************************************************************
     *
     *  フラグメント： 生成
     *
     * @param savedInstanceState    回復用データ（使わない）
     *
     ********************************************************************************************/

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.w(TAG,"onCreate "+ this.hashCode());

        super.onCreate(savedInstanceState);
    }

    public void onDestroy()
    {
        super.onDestroy();

        Log.w(TAG,"onDestroy "+ this.hashCode());

        mService        = null;
        mPreference     = null;
    }

    @Override
    public void onPause()
    {
        Log.w(TAG,"onPause "+ this.hashCode());
        super.onPause();
    }

    /**********************************************************************************************
     *
     * サービスと登録する
     *
     * @param service       AtomServiceオブジェクト
     *
     *********************************************************************************************/

    void setService(AtomService service)
    {
        Log.w(TAG,"setService "+ this.hashCode());
        mService = service;
    }

    /**********************************************************************************************
     *
     * プリファレンスを登録する
     *
     * @param preference    AtomPreferenceオブジェクト
     *
     *********************************************************************************************/

    void setPreference(AtomPreference preference)
    {
        Log.w(TAG,"setPreference "+ this.hashCode());

        mPreference = preference;

        onPreferenceAvailable();

        background_image();
    }

    // 派生クラスを呼び出す（抽象メソッド）
    abstract void onPreferenceAvailable();


    /*********************************************************************************************
     *
     *  （オプション）画面タッチ監視を有効にする
     *
     * @param view  監視するVIEW
     *
     ********************************************************************************************/

    protected  void enableTouch(View view)
    {
        // GestureDetectorインスタンス作成
        mGestureDetector = new GestureDetector(mContext, this);
        view.setOnTouchListener(mTouchListener);
    }

    /*********************************************************************************************
     *
     *  タッチリスナー　：　ジャスチャーに渡す
     *
     *********************************************************************************************/

    private final View.OnTouchListener  mTouchListener  = new View.OnTouchListener()
    {
        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
            if (event.getAction() == MotionEvent.ACTION_UP)
                v.performClick();

            mGestureDetector.onTouchEvent(event);
            return true;
        }
    };

    /*********************************************************************************************
     *
     *  背景イメージの変更
     *
     *********************************************************************************************/

    public void background_image()
    {
        if ((mView != null) && (mPreference != null))
        {
            String bmpStr = mPreference.getPref_background();

            try {
                if (!bmpStr.equals(""))
                {
                    //BitmapFactory.Options options = new BitmapFactory.Options();
                    byte[] byteArray = Base64.decode(bmpStr, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length).copy(Bitmap.Config.ARGB_8888, true);

                    mView.setBackground(new BitmapDrawable(mContext.getResources(), bitmap));
                }
                else
                {
                    mView.setBackground(null);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    protected boolean isLockScreen()
    {
        KeyguardManager keyguardmanager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);

        return Objects.requireNonNull(keyguardmanager).isKeyguardLocked();

    }

    abstract  void UI_update(AtomStatus.AtomInfo info);

    /*********************************************************************************************
     *
     *  各種ジェスチャイベント
     *
     *********************************************************************************************/

    @Override
    public boolean onDoubleTap(MotionEvent e)
    {
        Log.w(TAG,"onDoubleTap "+ (mListener!= null) +" "+ this.hashCode());

        if (mListener != null) mListener.onDoubleTap();
        return false;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e)
    {
        Log.w(TAG,"onSingleTapUp " + (mListener!= null) +" "+ this.hashCode());

        if (mListener != null) mListener.onSingleTapUp();
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) { return false;  }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) { return false; }

    @Override
    public boolean onDown(MotionEvent e) { return false; }

    @Override
    public void onShowPress(MotionEvent e) { }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) { return false; }

    @Override
    public void onLongPress(MotionEvent e) { }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) { return false; }
}
