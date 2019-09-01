package com.salty919.atomTethringUI;

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ViewGroup;

import com.salty919.atomTethringService.AtomService;
import com.salty919.atomTethringService.AtomStatus;

import java.util.ArrayList;

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

    private final String TAG = ViewPagerAdapter.class.getSimpleName();

    private final FragmentManager           mFragmentManager;
    private final ArrayList<FragmentBase>   mItemList;
    private final int                       mItemCnt;

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     * @param fragmentManager       フラグメントマネージャ
     *
     *********************************************************************************************/

    ViewPagerAdapter(FragmentManager fragmentManager)
    {
        super(fragmentManager);

        Log.e(TAG, "new Adapter "+ this.hashCode());

        mFragmentManager    = fragmentManager;
        mItemList           = new ArrayList<>();

        //
        // Page0: ControlFragment
        //

        {
            FragmentBase fragment = new ControlFragment();

            mItemList.add(fragment);
        }
        //
        // Page1: SettingFragment
        //
        {
            FragmentBase fragment = new SettingFragment();

            mItemList.add(fragment);
        }

        //
        // Page2: DeviceFragment
        //

        {
            FragmentBase fragment = new DeviceFragment();

            mItemList.add(fragment);
        }

        mItemCnt    = 3;
    }
    /**********************************************************************************************
     *
     * UI情報更新通知
     *
     * @param info  各種情報構造
     *
     *********************************************************************************************/

    void notify(AtomStatus.AtomInfo info)
    {
        for (FragmentBase fragment:mItemList )
        {
            if (fragment.mEnable)  fragment.UI_update(info);
        }
    }

    /**********************************************************************************************
     *
     * 背景画像の変更通知
     *
     *********************************************************************************************/

    void backGroundImage()
    {
        for (FragmentBase fragment:mItemList )
        {
            if (fragment.mEnable)  fragment.background_image();
        }
    }

    /**********************************************************************************************
     *
     * サービスのセット
     *
     *********************************************************************************************/

    void setService(AtomService service)
    {
        for (FragmentBase fragment:mItemList )
        {
            if (fragment.mEnable)  fragment.setService(service);
        }
    }

    @Override
    public Fragment getItem(int position)
    {
        return mItemList.get(position);
    }

    @Override
    public int getCount()
    {
        return  mItemCnt;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object)
    {
        super.destroyItem(container, position, object);

        if (position <getCount())
        {
            Log.w(TAG," remove[ "+position +"] "+ ((FragmentBase) object).TAG );

            FragmentTransaction trans = mFragmentManager.beginTransaction();
            trans.remove((Fragment) object);
            trans.commit();
        }
    }
}
