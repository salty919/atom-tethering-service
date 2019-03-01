package com.salty919.atomTethringUI;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     *********************************************************************************************/

    public DeviceFragment()
    {
        mCnt ++;

        TAG  = DeviceFragment.class.getSimpleName() + "["+mCnt+"]";
        Log.w(TAG,"new DeviceFragment ");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mView = View.inflate(mContext, R.layout.device, null);

        mListView = mView.findViewById(R.id.deviceList);

        mList = new ArrayList<>();
        mAdapter = new DeviceAdapter(mContext);

        if (mListener != null)  mListener.onReady(this);

        return mView;
    }

    @Override
    void onPreferenceAvailable() {

    }

    @Override
    void UI_update(AtomStatus.AtomInfo info)
    {
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
