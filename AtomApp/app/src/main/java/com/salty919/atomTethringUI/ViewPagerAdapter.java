package com.salty919.atomTethringUI;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/*************************************************************************************************
 *
 *  フラグメントページャー
 *
 *  ・テザリング切替画面（ControlFragment)
 *  ・設定画面（SettingFragment)
 *
 *  の２画面のページ切替を実施するページャー
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public class ViewPagerAdapter extends FragmentPagerAdapter
{
    ViewPagerAdapter(FragmentManager fragmentManager)
    {
        super(fragmentManager);
    }

    final static int       CONTROL_POS         = 0;
    final static int       SETTING_POS         = 1;

    private ControlFragment     mControl       = null;
    private SettingFragment     mSetting       = null;

    @Override
    public Fragment getItem(int position)
    {
        Fragment fragment = null;

        switch (position)
        {
            case CONTROL_POS:

                if (mControl == null) mControl = new ControlFragment();
                fragment = mControl;
                break;

            case SETTING_POS:

                if (mSetting == null) mSetting = new SettingFragment();
                fragment = mSetting;
                break;
        }
        return fragment;
    }

    @Override
    public int getCount()
    {
        return 2;
    }

}
