package com.salty919.atomTethringUI;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.salty919.atomTethringService.AtomStatus;
import com.salty919.atomTethringService.ConnectionType;

import java.util.Locale;

/*************************************************************************************************
 *
 *  テザリング切替画面（フラグメント）
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public class ControlFragment extends FragmentBase
{
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static final String TAG = ControlFragment.class.getSimpleName();

    // UI部品
    private ImageButton mWifiAPBtn              = null;
    private TextView    mConnectText            = null;
    private TextView    mIpText                 = null;
    private TextView    mBattery                = null;
    private TextView    mPtt                    = null;
    private TextView    mTether                 = null;
    private TextView    mTimeSec                = null;

    //private ProgressBar mTetherBar              = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mView = View.inflate(mContext, R.layout.control, null );

        mWifiAPBtn      = mView.findViewById(R.id.wifiAp);
        mConnectText    = mView.findViewById(R.id.connect);
        mIpText         = mView.findViewById(R.id.ipaddress);
        mBattery        = mView.findViewById(R.id.battery);
        mPtt            = mView.findViewById(R.id.accessibility);
        mTether         = mView.findViewById(R.id.tethering);
        mTimeSec        = mView.findViewById(R.id.timer);
        //mTetherBar      = view.findViewById(R.id.tetheringBar);

        mPtt.setOnClickListener(mClickListener);
        mPtt.setClickable(true);

        //mTetherBar.setMax(100);
        //mTetherBar.setMin(0);
        //mTetherBar.setProgress(0,true);

        // 画面タッチを有効にする
        enableTouch(mView);

        background_change();

        return mView;
    }

    /**********************************************************************************************
     *
     *  PTTボタンクリック（Accessibilityの設定）
     *
     *********************************************************************************************/

    private final View.OnClickListener mClickListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            if ( mService != null) mService.startAccessibility();
        }
    };

    /**********************************************************************************************
     *
     *  (起動時)プリファレンス使えるようになった
     *
     *********************************************************************************************/

    void onPreferenceAvailable()
    {
        // 特に反映させる項目なし
    }

    /**********************************************************************************************
     *
     *  （内部）　ユーザインターフェースの情報を更新する
     *
     *********************************************************************************************/

    synchronized  void UI_update(AtomStatus.AtomInfo info)
    {
        //
        // テザリング経過時間プログレスバーの更新
        //

        //mTetherBar.setProgress((int) (info.mTimerRatio*100), true);

        //
        // テザリング残り秒数の更新
        //

        if (info.mTimerSec != 0)
        {
            long    stopTime = info.mTimerSec;

            String time_str = String.format(Locale.US,"AUTO OFF %3d:%02d",stopTime/60, stopTime % 60 );
            mTimeSec.setText(time_str);
        }
        else
        {
            mTimeSec.setText("");
        }

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

        //
        // バッテリ残量
        //

        if (info.mBatteryLevel != -1)
        {
            String levelStr = String.format(Locale.JAPANESE, "%d", info.mBatteryLevel);
            mBattery.setText(levelStr);
        }

        //
        // 回線状態
        //

        if (info.mType == ConnectionType.MOBILE)
        {
            // モバイル回線

            mWifiAPBtn.setBackgroundResource(R.drawable.mobile_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_signal_cellular_alt_24px);
            mConnectText.setText(R.string.moble);
            mIpText.setText(info.mIp);
        }
        else if (info.mType == ConnectionType.TETHER)
        {
            // モバイル回線でのテザリング中

            mWifiAPBtn.setBackgroundResource(R.drawable.tether_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_wifi_tethering_24px);
            mConnectText.setText(R.string.tether);
            mIpText.setText(info.mIp);
        }
        else if (info.mType == ConnectionType.WIFI)
        {
            // WIFI接続中

            mWifiAPBtn.setBackgroundResource(R.drawable.wifiap_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_wifi_24px);
            mConnectText.setText(R.string.wifi);
            mIpText.setText(info.mIp);
        }
        else
        {
            // 回線なし

            mWifiAPBtn.setBackgroundResource(R.drawable.discon_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_error_outline_24px);
            mConnectText.setText(R.string.none);
            mIpText.setText(R.string.noAddr);
        }
    }
}
