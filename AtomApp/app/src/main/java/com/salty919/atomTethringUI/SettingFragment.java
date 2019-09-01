package com.salty919.atomTethringUI;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
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
    private Button          mAutoStartBlock         = null;
    private RadioGroup      mForegroundService      = null;
    private Button          mForegroundServiceBlock = null;

    private TextView        mCam                    = null;
    private TextView        mTether                 = null;
    private TextView        mVersion                = null;
    private Button          mBanner                 = null;

    // 選択値
    private int             mAutoStartId            = -1;
    private int             mForegroundServiceId    = -1;

    private static int      mCnt                    = 0;

    private final Handler   mHandle                 = new Handler();

    private AlertDialog     mDialog                 = null;

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
        Log.w(TAG,"onCreateView " + this.hashCode());

        mView   = View.inflate(mContext, R.layout.setting, null);

        mAutoStart              = mView.findViewById(R.id.boot);
        mForegroundService      = mView.findViewById(R.id.activate);
        mAutoStartBlock         = mView.findViewById(R.id.bootblock);
        mForegroundServiceBlock = mView.findViewById(R.id.activateblock);
        mCam                    = mView.findViewById(R.id.camera);
        mTether                 = mView.findViewById(R.id.tethering);
        mVersion                = mView.findViewById(R.id.pversion);
        mBanner                 = mView.findViewById(R.id.lock);

        mBanner.setOnClickListener(mClickListener);
        mBanner.setBackgroundColor(Color.BLACK);
        mBanner.setTextColor(Color.WHITE);
        mBanner.setClickable(true);

        mCam.setOnClickListener(mClickListener);
        mCam.setClickable(true);

        mAutoStartBlock.setVisibility(View.INVISIBLE);
        mAutoStartBlock.setOnClickListener(mClickListener);
        mAutoStartBlock.setClickable(true);

        mForegroundServiceBlock.setVisibility(View.INVISIBLE);
        mForegroundServiceBlock.setOnClickListener(mClickListener);
        mForegroundServiceBlock.setClickable(true);

        mAutoStart.setOnCheckedChangeListener(this);
        mForegroundService.setOnCheckedChangeListener(this);

        mAutoStartId = mAutoStart.getCheckedRadioButtonId();
        mForegroundServiceId = mForegroundService.getCheckedRadioButtonId();

        mRunning = true;

        if (mListener != null)  mListener.onReady(this);

        mEnable = true;

        return mView;
    }

    /**********************************************************************************************
     *
     * フラグメントのポーズ
     *
     **********************************************************************************************/

    @Override
    public void onPause()
    {
        super.onPause();
        if (mDialog != null) mDialog.dismiss();
    }

    /**********************************************************************************************
     *
     *  (起動時)プリファレンス使えるようになった
     *
     *********************************************************************************************/

    @Override
    void onPreferenceAvailable()
    {
        Log.w(TAG, "UI update by Preference "+ this.hashCode());

        // プリファレンスの値を表示
        updatePreferenceValue();

        //
        background_image();
    }

    /**********************************************************************************************
     *
     *  UIの更新（起動時のプリファレンス反映）
     *
     *********************************************************************************************/

    private void updatePreferenceValue()
    {
        if (!mRunning) return;

        boolean fore = mPreference.getPref_ForegroundService();

        boolean auto = mPreference.getPref_AutoStart();

        Log.w(TAG ,"[foreground] UI " + fore);
        Log.w(TAG, "[auto start] UI " + auto);

        String ver = " preference-v."+ mPreference.PREF_VER;
        mVersion.setText(ver);

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

        if (isLockScreen())
        {
            mBanner.setTextColor(Color.WHITE);
            mBanner.setVisibility(View.VISIBLE);
            mBanner.setText(R.string.lock);

            mForegroundServiceBlock.setVisibility(View.VISIBLE);
            mAutoStartBlock.setVisibility(View.VISIBLE);
        }
        else
        {
            mBanner.setTextColor(Color.WHITE);
            mBanner.setVisibility(View.VISIBLE);
            mBanner.setText(R.string.unLock);

            mForegroundServiceBlock.setVisibility(View.INVISIBLE);
            mAutoStartBlock.setVisibility(View.INVISIBLE);

        }

        //
        // Camera
        //

        if ((mPreference!=null) && (mPreference.getPref_CameraExec()))
        {
            mCam.setTextColor(Color.YELLOW);
        }
        else
        {
            mCam.setTextColor(Color.GRAY);
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

    /*********************************************************************************************
     *
     *  バーナーハンドラー
     *
     ********************************************************************************************/

    private final Runnable  mBannerHandler = new Runnable()
    {
        @Override
        public void run()
        {
            mBanner.setText(R.string.lock);
            mBanner.setTextColor(Color.WHITE);
        }
    };

    /*********************************************************************************************
     *
     *  操作キャンセル
     *
     ********************************************************************************************/

    private void operationBlock()
    {
        mBanner.setText(R.string.opblock);
        mBanner.setTextColor(Color.YELLOW);
        mHandle.removeCallbacks(mBannerHandler);
        mHandle.postDelayed(mBannerHandler, 1000);
    }

    /*********************************************************************************************
     *
     *  壁紙クリア
     *
     ********************************************************************************************/

    private void Func_clearWallPaper()
    {
        mPreference.setPref_background("");
    }

    /*********************************************************************************************
     *
     *  壁紙選択
     *
     ********************************************************************************************/

    private void Func_selectWallPaper()
    {
        if (mListener != null) mListener.onPickupImage();
    }

    /*********************************************************************************************
     *
     *  カメラ呼び出し機能、有効／無効トグル
     *
     ********************************************************************************************/

    private void Func_cameraToggle()
    {
        boolean camera = mPreference.getPref_CameraExec();

        mPreference.setPref_CameraExec(!camera);

        if (!camera) {
            Log.w(TAG, "change camera enable");
        } else {
            Log.w(TAG, "change camera disable");
        }
    }

    /**********************************************************************************************
     *
     *  背景選択／解除
     *
     *********************************************************************************************/

    private final View.OnClickListener mClickListener  = new View.OnClickListener()
    {
        @Override
        public void onClick(View v) {

            if (v != null)
            {
                switch (v.getId())
                {
                    case R.id.activateblock:
                    case R.id.bootblock:
                    {
                        operationBlock();
                    }
                    break;

                    case R.id.camera:
                    {
                        if (isLockScreen())
                        {
                            operationBlock();
                        }
                        else
                        {
                            Func_cameraToggle();
                        }
                    }
                    break;

                    case R.id.lock:
                    {
                        if (!isLockScreen())
                        {
                            wallPaperDialog();
                        }
                        else
                        {
                            operationBlock();
                        }
                    }
                    break;
                }
            }
        }
    };

    /********************************************************************************************
     *
     *  壁紙変更ダイアログ
     *
     ********************************************************************************************/
    void wallPaperDialog()
    {
        mDialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.wallPaperTitle )
                .setMessage(R.string.wallPaperMessage)
                .setPositiveButton(R.string.selectw, new  DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Func_selectWallPaper();
                    }
                })
                .setNegativeButton(R.string.clearw, new  DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Func_clearWallPaper();
                    }
                })
                .setNeutralButton(R.string.cancel, new  DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mListener.onCancel();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mListener.onCancel();
                    }
                })
                .show();
    }
 }
