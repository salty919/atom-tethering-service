package com.salty919.atomTethringUI;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.salty919.atomTethringService.AtomStatus;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;

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
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static final String TAG = SettingFragment.class.getSimpleName();

    private final int   RESULT_PICK_IMAGE      = 1;

    // ラジオグループ
    private RadioGroup      mAutoStart              = null;
    private RadioGroup      mForegroundService      = null;

    private TextView        mPtt                    = null;
    private TextView        mTether                 = null;

    private Button          mWallPaper              = null;
    private Button          mClear                   = null;
    // 選択値
    private int             mAutoStartId            = -1;
    private int             mForegroundServiceId    = -1;

    private int             mWidth                  = 0;
    private int             mHeight                 = 0;

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

        mWallPaper.setOnClickListener(mClickListener);
        mWallPaper.setClickable(true);

        mClear.setOnClickListener(mClickListener);
        mClear.setClickable(true);

        updateUI();

        background_change();

        mView.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);

        return mView;
    }

    /**********************************************************************************************
     *
     *  レイアウト後の画面サイズを取得
     *
     *********************************************************************************************/

    ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener()
    {
        @Override
        public void onGlobalLayout()
        {
            // VIEWの画面サイズを記録する
            mWidth  = mView.getWidth();
            mHeight = mView.getHeight();

            Log.w(TAG," VIEW "+ mWidth + " " + mHeight);

            mView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
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
    }

    /**********************************************************************************************
     *
     *  UIの更新（起動時のプリファレンス反映）
     *
     *********************************************************************************************/

    private void updateUI()
    {

        if (mPreference == null) return;

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
    public void onCheckedChanged(RadioGroup group, int checkedId) {

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

        //
        // PTT 接続状態
        //

        if (info.mPtt)
        {
            mReady  = true;

            mPtt.setTextColor(Color.YELLOW);
            mPtt.setClickable(false);
        }
        else
        {
            mReady  = false;

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

                    case R.id.wallpaper: {

                        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                        // Filter to only show results that can be "opened", such as a
                        // file (as opposed to a list of contacts or timezones)
                        intent.addCategory(Intent.CATEGORY_OPENABLE);

                        // Filter to show only images, using the image MIME data type.
                        // it would be "*/*".
                        intent.setType("*/*");

                        startActivityForResult(intent, RESULT_PICK_IMAGE);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData)
    {

        if (requestCode == RESULT_PICK_IMAGE && resultCode == RESULT_OK)
        {
            if(resultData.getData() != null)
            {

                ParcelFileDescriptor pfDescriptor = null;

                try
                {
                    Uri uri = resultData.getData();

                    pfDescriptor = mContext.getContentResolver().openFileDescriptor(uri, "r");

                    if(pfDescriptor != null)
                    {
                        FileDescriptor fileDescriptor = pfDescriptor.getFileDescriptor();
                        Bitmap bmp = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                        pfDescriptor.close();

                        Bitmap wall = Bitmap.createScaledBitmap(bmp, mWidth, mHeight, false);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        wall.compress(Bitmap.CompressFormat.PNG, 100, baos);

                        String bitmapStr = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                        mPreference.setPref_background(bitmapStr);

                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    try
                    {
                        if(pfDescriptor != null)
                        {
                            pfDescriptor.close();
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

 }
