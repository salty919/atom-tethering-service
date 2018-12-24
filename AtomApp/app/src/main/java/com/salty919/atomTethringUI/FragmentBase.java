package com.salty919.atomTethringUI;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.salty919.atomTethringService.AtomService;

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

    /**********************************************************************************************
     *
     *  リスナー
     *
     *********************************************************************************************/

    public interface Listener
    {
        void onSingleTapUp();
        void onDoubleTap();
    }

    protected   Context         mContext            = null;
    protected   Listener        mListener           = null;
    protected   AtomService     mService            = null;
    protected   AtomPreference  mPreference         = null;
    private     GestureDetector mGestureDetector    = null;

    protected   boolean         mReady              = false;

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

        mContext = context;
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
        super.onCreate(savedInstanceState);
    }

    public void onDestroy()
    {
        super.onDestroy();

        mService = null;
        mPreference = null;
        mListener = null;

    }

    /**********************************************************************************************
     *
     * サービスと登録する
     *
     * @param service       AtomServiceオブジェクト
     *
     *********************************************************************************************/

    void setService(AtomService service)  {  mService = service; }

    /**********************************************************************************************
     *
     * プリファレンスを登録する
     *
     * @param preference    AtomPreferenceオブジェクト
     *
     *********************************************************************************************/

    void setPreference(AtomPreference preference)
    {
        mPreference = preference;

        onPreferenceAvailable();

        background_change();
    }

    // 派生クラスを呼び出す（抽象メソッド）
    abstract void onPreferenceAvailable();

    /**********************************************************************************************
     *
     * リスナー登録
     *
     * @param listener      通知先オブジェクト
     *
     *********************************************************************************************/

    void setListener(Listener listener)
    {
        mListener = listener;
    }

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
            if (mReady) mGestureDetector.onTouchEvent(event);
            return true;
        }
    };

    public void background_change()
    {
        if ((mView != null) && (mPreference != null))
        {
            String bmpStr = mPreference.getPref_background();

            try {
                if (!bmpStr.equals("")) {
                    //BitmapFactory.Options options = new BitmapFactory.Options();
                    byte[] byteArray = Base64.decode(bmpStr, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length).copy(Bitmap.Config.ARGB_8888, true);

                    mView.setBackground(new BitmapDrawable(mContext.getResources(), bitmap));
                } else {
                    mView.setBackground(null);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /*********************************************************************************************
     *
     *  各種ジェスチャイベント
     *
     *********************************************************************************************/

    @Override
    public boolean onDoubleTap(MotionEvent e)
    {
        if (mListener != null) mListener.onDoubleTap();
        return false;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e)
    {
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