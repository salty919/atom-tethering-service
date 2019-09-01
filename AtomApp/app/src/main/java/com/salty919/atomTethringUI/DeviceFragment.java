package com.salty919.atomTethringUI;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.salty919.atomTethringService.AtomDevice;
import com.salty919.atomTethringService.AtomStatus;

import java.util.ArrayList;

public class DeviceFragment extends FragmentBase
{
    private static int              mCnt                    = 0;
    private ListView                mListView               = null;
    private DeviceAdapter           mAdapter;
    private ArrayList<AtomDevice>   mList;

    @SuppressWarnings("FieldCanBeLocal")
    private Button                  mBanner;
    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     *********************************************************************************************/

    public DeviceFragment()
    {
        mCnt ++;

        TAG  = DeviceFragment.class.getSimpleName() + "["+mCnt+"]";
        Log.w(TAG,"new DeviceFragment "+ this.hashCode());
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
        Log.w(TAG," onCreateView " + this.hashCode()) ;

        mView       = View.inflate(mContext, R.layout.device, null);
        mBanner     = mView.findViewById(R.id.terminate);
        mListView   = mView.findViewById(R.id.deviceList);

        mList = new ArrayList<>();
        mAdapter = new DeviceAdapter(mContext);

        if (mListener != null)  mListener.onReady(this);

        mBanner.setBackgroundColor(Color.BLACK);
        mBanner.setTextColor(Color.WHITE);

        mEnable = true;

        return mView;
    }

    /**********************************************************************************************
     *
     *  (起動時)プリファレンス使えるようになった
     *
     *********************************************************************************************/

    @Override
    void onPreferenceAvailable()
    {
        Log.w(TAG," onPreferenceAvailable " + this.hashCode());
    }

    /**********************************************************************************************
     *
     *  （内部）　ユーザインターフェースの情報を更新する
     *
     *********************************************************************************************/

    @Override
    void UI_update(AtomStatus.AtomInfo info)
    {
        Log.w(TAG," UI_update " + this.hashCode());

        if (mService != null)
        {
            mList = mService.getDeviceList();

            if (mList != null)
            {
                if (mAdapter != null)
                {
                    mAdapter.setDeviceList(mList);
                    mListView.setAdapter(mAdapter);
                }
            }
        }
    }
}
