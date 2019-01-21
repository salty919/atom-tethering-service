package com.salty919.atomTethringUI;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
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

public class ViewPagerAdapter extends FragmentPagerAdapter implements FragmentBase.Listener
{
    public interface Listener
    {
        void onPickupImage();
        void onSingleTapUp();
        void onDoubleTap();
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static final String TAG = ViewPagerAdapter.class.getSimpleName();

    private final FragmentManager           mFragmentManager;
    private final Listener                  mListener;
    private final ArrayList<FragmentBase>   mItemList;
    private int                             mItemCnt;
    private AtomPreference                  mPreference;
    private AtomService                     mService = null;

    int                                     mWidth  = 0;
    int                                     mHeight = 0;

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     * @param fragmentManager       フラグメントマネージャ
     * @param listener              リスナー
     *
     *********************************************************************************************/

    ViewPagerAdapter(FragmentManager fragmentManager, AtomPreference preference, Listener listener)
    {
        super(fragmentManager);

        Log.e(TAG, "new Adapter");

        mFragmentManager    = fragmentManager;
        mListener           = listener;
        mItemList           = new ArrayList<>();
        mPreference         = preference;

        //
        // Page0: ControlFragment
        //

        mItemList.add(new ControlFragment());

        //
        // Page1: SettingFragment
        //

        mItemList.add(new SettingFragment());

        mItemCnt    = 2;
    }

    /**********************************************************************************************
     *
     * ページャに登録された全フラグメントを開放する
     *
     * @param pager   ぺーじゃ
     *
     *********************************************************************************************/

    @SuppressWarnings("unused")
    void destroyAllItem(ViewPager pager)
    {
        for (int pos = 0; pos < getCount() ; pos++)
        {
            FragmentBase fragment=  (FragmentBase) this.instantiateItem(pager, pos);
            fragment.setListener(null);
            destroyItem(pager, pos, fragment);
        }
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
            fragment.UI_update(info);
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
            fragment.background_image();
        }
    }

    /**********************************************************************************************
     *
     * サービスのセット
     *
     *********************************************************************************************/

    void setService(AtomService service)
    {
        mService = service;

        for (FragmentBase fragment:mItemList )
        {
            fragment.setService(service);
        }
    }

    @Override
    public Fragment getItem(int position)
    {
        FragmentBase fragment = mItemList.get(position);

        Log.w(TAG," add   ["+position+"] " + fragment.TAG);

        fragment.setListener(this);

        return fragment;
    }

    @Override
    public int getCount()
    {
        return  mItemCnt;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object)
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

    @Override
    public void onReady(FragmentBase fragment)
    {
        Log.w(TAG,"onReady "+ fragment.TAG);

        fragment.setPreference(mPreference);
        fragment.setService(mService);
    }

    @Override
    public void onLayoutFix(int width, int height)
    {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onPickupImage()
    {
        mListener.onPickupImage();
    }

    @Override
    public void onSingleTapUp()
    {

        mListener.onSingleTapUp();
    }

    @Override
    public void onDoubleTap()
    {
        mListener.onDoubleTap();
    }
}
