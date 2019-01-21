package com.salty919.atomTethringUI;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.salty919.atomTethringService.AtomStatus;

import java.util.Objects;

/*************************************************************************************************
 *
 *  設定画面：　画面が小さいのでフレームワークの設定部品は使わず、通常のフラグメントで構成する
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public class SettingFragment extends FragmentBase implements RadioGroup.OnCheckedChangeListener
{
    // ラジオグループ
    private RadioGroup      mAutoStart              = null;
    private RadioGroup      mForegroundService      = null;

    private TextView        mPtt                    = null;
    private TextView        mTether                 = null;

    @SuppressWarnings("FieldCanBeLocal")
    private Button          mWallPaper              = null;
    @SuppressWarnings("FieldCanBeLocal")
    private Button          mClear                   = null;
    // 選択値
    private int             mAutoStartId            = -1;
    private int             mForegroundServiceId    = -1;

    private static int      mCnt                    = 0;

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     *********************************************************************************************/

    public SettingFragment()
    {
        mCnt ++;

        TAG  = SettingFragment.class.getSimpleName() + "["+mCnt+"]";
        Log.w(TAG,"new SettingFragment ");
    }

    /**********************************************************************************************
     *
     * フラグメントのVIEW生成
     *
     * @param inflater              未使用
     * @param container             未使用
     * @param savedInstanceState    未使用
     *
     * @return      VIEW
     *
     *********************************************************************************************/

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mView   = View.inflate(mContext, R.layout.setting, null);

        mAutoStart          = mView.findViewById(R.id.boot);
        mForegroundService  = mView.findViewById(R.id.activate);
        mPtt                = mView.findViewById(R.id.accessibility);
        mTether             = mView.findViewById(R.id.tethering);
        mWallPaper          = mView.findViewById(R.id.wallpaper);
        mClear              = mView.findViewById(R.id.clear);

        mAutoStart.setOnCheckedChangeListener(this);
        mForegroundService.setOnCheckedChangeListener(this);

        mAutoStartId = mAutoStart.getCheckedRadioButtonId();
        mForegroundServiceId = mForegroundService.getCheckedRadioButtonId();

        KeyguardManager keyguardmanager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);

        if (!Objects.requireNonNull(keyguardmanager).isKeyguardLocked())
        {
            mWallPaper.setOnClickListener(mClickListener);
            mWallPaper.setClickable(true);

            mClear.setOnClickListener(mClickListener);
            mClear.setClickable(true);
        }
        else
        {
            mWallPaper.setClickable(false);
            mClear.setClickable(false);

            mWallPaper.setTextColor(Color.GRAY);
            mClear.setTextColor(Color.GRAY);
            //mWallPaper.setVisibility( View.INVISIBLE);
            //mClear.setVisibility(View.INVISIBLE);
        }

        mView.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);

        Log.w(TAG,"onCreateView");

        mRunning = true;

        if (mListener != null)  mListener.onReady(this);

        return mView;
    }

    /**********************************************************************************************
     *
     *  レイアウト後の画面サイズを取得
     *
     *********************************************************************************************/

    private final ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener()
    {
        @Override
        public void onGlobalLayout()
        {
            // VIEWの画面サイズを記録する
            int width  = mView.getWidth();
            int height = mView.getHeight();

            Log.w(TAG," VIEW "+ width + " " + height);

            mView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

            if (mListener != null) mListener.onLayoutFix(width, height);
        }
    };

    /**********************************************************************************************
     *
     *  (起動時)プリファレンス使えるようになった
     *
     *********************************************************************************************/

    @Override
    void onPreferenceAvailable()
    {
        Log.w(TAG, "UI update by Preference");

        // プリファレンスの値を表示
        updateUI();

        //
        background_image();
    }

    /**********************************************************************************************
     *
     *  UIの更新（起動時のプリファレンス反映）
     *
     *********************************************************************************************/

    private void updateUI()
    {
        if (!mRunning) return;

        boolean fore = mPreference.getPref_ForegroundService();

        boolean auto = mPreference.getPref_AutoStart();

        Log.w(TAG ,"[foreground] UI " + fore);
        Log.w(TAG, "[auto start] UI " + auto);

        if (mForegroundService != null)
        {
            if (fore)
            {
                mForegroundService.check(R.id.enable);
            }
            else
            {
                mForegroundService.check(R.id.disable);
            }
        }

        if (mAutoStart != null)
        {
            if (auto)
            {
                mAutoStart.check(R.id.auto);
            }
            else
            {
                mAutoStart.check(R.id.manual);
            }
        }
    }

    /**********************************************************************************************
     *
     *  (ユーザ操作）ラジオボタンの変更  ※リスナー名がへんな名称やが標準やから
     *
     * @param group         変更されたラジオグループ
     * @param checkedId     選択されたボタン（ID)
     *
     ********************************************************************************************/

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId)
    {
        //Log.w(TAG, "onCheckedChanged G:" + group +" ID: " + checkedId);

        if (mPreference == null) return;

        if (group == mAutoStart)
        {
            mAutoStartId = checkedId;

            if (mAutoStartId == R.id.auto)
            {
                mPreference.setPref_AutoStart(true);
            }
            else if (mAutoStartId == R.id.manual)
            {
                mPreference.setPref_AutoStart(false);
            }
        }
        else if (group == mForegroundService)
        {
            mForegroundServiceId = checkedId;

            if (mForegroundServiceId == R.id.enable)
            {
                mPreference.setPref_ForegroundService(true);
            }
            else if (mForegroundServiceId == R.id.disable)
            {
                mPreference.setPref_ForegroundService(false);
            }
        }
    }

    /**********************************************************************************************
     *
     *  （内部）　ユーザインターフェースの情報を更新する
     *
     *********************************************************************************************/

    synchronized  void UI_update(AtomStatus.AtomInfo info)
    {
        //Log.w(TAG," UI_update " + mRunning );

        if (!mRunning) return;

        //
        // PTT 接続状態
        //

        if (info.mPtt)
        {
            mPttReady  = true;
            mPtt.setTextColor(Color.YELLOW);
            mPtt.setClickable(false);
        }
        else
        {
            mPttReady  = false;
            mPtt.setTextColor(Color.GRAY);
            mPtt.setClickable(true);
        }

        //
        // テザリング要求
        //

        if (info.mTether)
        {
            mTether.setTextColor(Color.YELLOW);
        }
        else
        {
            mTether.setTextColor(Color.GRAY);
        }
    }

    /**********************************************************************************************
     *
     *  背景選択／解除
     *
     *********************************************************************************************/

    private final View.OnClickListener mClickListener  = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (v != null) {
                switch (v.getId()) {

                    case R.id.wallpaper:
                    {
                        if (mListener != null) mListener.onPickupImage();
                    }
                    break;

                    case R.id.clear:
                    {
                        mPreference.setPref_background("");
                    }
                    break;

                }
            }
        }
    };
 }
