package com.salty919.atomTethringUI;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
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
    // UI部品
    private ImageButton mWifiAPBtn              = null;
    private TextView    mConnectText            = null;
    private TextView    mIpText                 = null;
    private TextView    mBattery                = null;
    private TextView    mPtt                    = null;
    private TextView    mTether                 = null;
    private TextView    mTimeSec                = null;
    private TextView    mExtraInfo              = null;
    private TextView    mCell                   = null;
    private TextView    mWifi                   = null;
    private TextView    mBlue                   = null;
    private TextView    mUsb                    = null;
    private TextView    mVpn                    = null;
    private TextView    mWap                    = null;

    private static int  mCnt                    = 0;

    //private ProgressBar mTetherBar              = null;

    public ControlFragment()
    {
        mCnt++;

        TAG =   ControlFragment.class.getSimpleName() + "["+mCnt +"]";

        Log.w(TAG,"new ControlFragment ");
    }

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
        mExtraInfo      = mView.findViewById(R.id.extraInfo);

        mCell           = mView.findViewById(R.id.bcell);
        mWifi           = mView.findViewById(R.id.bwifi);
        mBlue           = mView.findViewById(R.id.bblue);
        mUsb            = mView.findViewById(R.id.busb);
        mWap            = mView.findViewById(R.id.bap);
        mVpn            = mView.findViewById(R.id.bvpn);

        mPtt.setOnClickListener(mClickListener);
        mPtt.setClickable(true);

        mWifiAPBtn.setClickable(false);
        Log.w(TAG,"onCreateView ");

        mRunning = true;

        if (mListener != null)  mListener.onReady(this);

        // 画面タッチを有効にする
        enableTouch(mView);

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
        // 背景画像変更
        background_image();
    }

    /**********************************************************************************************
     *
     *  （内部）　ユーザインターフェースの情報を更新する
     *
     *********************************************************************************************/

    synchronized  void UI_update(AtomStatus.AtomInfo info)
    {
        Log.w(TAG," UI_update "+ mRunning );
        //
        // テザリング経過時間プログレスバーの更新
        //

        //mTetherBar.setProgress((int) (info.mTimerRatio*100), true);

        //
        // テザリング残り秒数の更新
        //

        if (!mRunning) return;

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

        if (info.mCell)
        {
            mCell.setTextColor(Color.YELLOW);
        }
        else
        {
            mCell.setTextColor(Color.GRAY);
        }

        if (info.mWifi)
        {
            mWifi.setTextColor(Color.YELLOW);
        }
        else
        {
            mWifi.setTextColor(Color.GRAY);
        }

        if (info.mBluetooth)
        {
            mBlue.setTextColor(Color.YELLOW);
        }
        else
        {
            mBlue.setTextColor(Color.GRAY);
        }

        if (info.mUsb)
        {
            mUsb.setTextColor(Color.YELLOW);
        }
        else
        {
            mUsb.setTextColor(Color.GRAY);
        }

        if (info.mVpn)
        {
            mVpn.setTextColor(Color.YELLOW);
        }
        else
        {
            mVpn.setTextColor(Color.GRAY);
        }

        if (info.mWap)
        {
            mWap.setTextColor(Color.YELLOW);
        }
        else
        {
            mWap.setTextColor(Color.GRAY);
        }

        //
        // バッテリ残量
        //

        if (info.mBatteryLevel != -1)
        {
            String levelStr = String.format(Locale.JAPANESE, "%d", info.mBatteryLevel);
            mBattery.setText(levelStr);
        }

        mIpText.setText(info.mIp);

        //
        // 回線状態
        //

        if (info.mType == ConnectionType.MOBILE)
        {
            // モバイル回線

            mWifiAPBtn.setBackgroundResource(R.drawable.mobile_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_signal_cellular_alt_24px);
            mConnectText.setText(R.string.moble);

        }
        else if (info.mType == ConnectionType.TETHER)
        {
            // モバイル回線でのテザリング中

            mWifiAPBtn.setBackgroundResource(R.drawable.tether_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_wifi_tethering_24px);
            mConnectText.setText(R.string.tether);
        }
        else if (info.mType == ConnectionType.WIFI)
        {
            // WIFI接続中

            mWifiAPBtn.setBackgroundResource(R.drawable.wifiap_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_wifi_24px);
            mConnectText.setText(R.string.wifi);
        }
        else if (info.mType == ConnectionType.BTOOTH)
        {
            // BT接続中

            mWifiAPBtn.setBackgroundResource(R.drawable.bt_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_baseline_bluetooth_connected_24px);
            mConnectText.setText(R.string.bluetooth);
        }
        else if (info.mType == ConnectionType.VPN)
        {
            // VPN接続中

            mWifiAPBtn.setBackgroundResource(R.drawable.vpn_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_baseline_vpn_lock_24px);
            mConnectText.setText(R.string.vpn);
        }
        else
        {
            // 回線なし

            mWifiAPBtn.setBackgroundResource(R.drawable.discon_style);
            mWifiAPBtn.setImageResource(R.drawable.ic_sharp_error_outline_24px);
            mConnectText.setText(R.string.none);
            mIpText.setText(R.string.noAddr);
        }

        if (info.mSubType.equals(""))
        {
            mExtraInfo.setText(info.mExtraInfo);
        }
        else
        {
            String extraText = info.mSubType + " : " + info.mExtraInfo;

            mExtraInfo.setText(extraText);
        }
    }
}
