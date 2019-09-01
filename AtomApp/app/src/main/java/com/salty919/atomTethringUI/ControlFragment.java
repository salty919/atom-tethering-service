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

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     *********************************************************************************************/

    public ControlFragment()
    {
        mCnt++;

        TAG =   ControlFragment.class.getSimpleName() + "["+mCnt +"]";

        Log.w(TAG,"new ControlFragment ");
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

        mPtt.setClickable(false);
        mWifiAPBtn.setClickable(false);

        Log.w(TAG,"onCreateView ");

        mRunning = true;

        if (mListener != null)  mListener.onReady(this);

        // 画面タッチを有効にする
        enableTouch(mView);
        mEnable = true;

        return mView;
    }

    /**********************************************************************************************
     *
     *  (起動時)プリファレンス使えるようになった
     *
     *********************************************************************************************/

    void onPreferenceAvailable()
    {
        // 背景画像変更
        background_image();

        preferenceUpdate();
    }

    /**********************************************************************************************
     *
     *  設定内容の表示反映
     *
     ********************************************************************************************/

    private void preferenceUpdate()
    {
        if (mPreference !=null)
        {
            if (mPreference.getPref_PttWakeup()) {
                mPtt.setText(R.string.ptt);
                mPtt.setTextColor(Color.YELLOW);

            } else {
                mPtt.setText(R.string.pow);
                mPtt.setTextColor(Color.YELLOW);
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
        Log.w(TAG," UI_update "+ mRunning );

        //
        // テザリング経過時間プログレスバーの更新
        //

        preferenceUpdate();

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
            if (info.mMainOut == ConnectionType.MOBILE)
            {
                mCell.setBackgroundResource(R.drawable.conect_style);
                mCell.setTextColor(Color.BLACK);
            }
            else
            {
                mCell.setBackgroundResource(R.drawable.device_style);
                mCell.setTextColor(Color.YELLOW);
            }
        }
        else
        {
            mCell.setBackgroundResource(R.drawable.device_style);
            mCell.setTextColor(Color.GRAY);
        }

        if (info.mWifi)
        {
            if (info.mMainOut == ConnectionType.WIFI)
            {
                mWifi.setBackgroundResource(R.drawable.conect_style);
                mWifi.setTextColor(Color.BLACK);
            }
            else
            {
                mWifi.setBackgroundResource(R.drawable.device_style);
                mWifi.setTextColor(Color.YELLOW);
            }
        }
        else
        {
            mWifi.setBackgroundResource(R.drawable.device_style);
            mWifi.setTextColor(Color.GRAY);
        }

        if (info.mBluetooth)
        {
            if (info.mMainOut == ConnectionType.BTOOTH)
            {
                mBlue.setBackgroundResource(R.drawable.conect_style);
                mBlue.setTextColor(Color.BLACK);
            }
            else
            {
                mBlue.setBackgroundResource(R.drawable.device_style);
                mBlue.setTextColor(Color.YELLOW);
            }
        }
        else
        {
            mBlue.setBackgroundResource(R.drawable.device_style);
            mBlue.setTextColor(Color.GRAY);
        }

        if (info.mUsb)
        {
            mUsb.setBackgroundResource(R.drawable.device_style);
            mUsb.setTextColor(Color.YELLOW);
        }
        else
        {
            mUsb.setBackgroundResource(R.drawable.device_style);
            mUsb.setTextColor(Color.GRAY);
        }

        if (info.mVpn)
        {
            if (info.mMainOut == ConnectionType.VPN)
            {
                mVpn.setBackgroundResource(R.drawable.conect_style);
                mVpn.setTextColor(Color.BLACK);
            }
            else
            {
                mVpn.setBackgroundResource(R.drawable.device_style);
                mVpn.setTextColor(Color.YELLOW);
            }
        }
        else
        {
            mVpn.setBackgroundResource(R.drawable.device_style);
            mVpn.setTextColor(Color.GRAY);
        }

        if (info.mWap)
        {
            mWap.setBackgroundResource(R.drawable.device_style);
            mWap.setTextColor(Color.YELLOW);
        }
        else
        {
            mWap.setBackgroundResource(R.drawable.device_style);
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
