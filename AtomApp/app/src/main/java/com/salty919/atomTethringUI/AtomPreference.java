package com.salty919.atomTethringUI;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

/*************************************************************************************************
 *
 *  プリファレンス管理(永続層）
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

class AtomPreference
{
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static final String TAG = AtomPreference.class.getSimpleName();

    /*** 保存パラメータ　*/

    @SuppressWarnings("WeakerAccess")
    static final String KEY_autostart = "auto start";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_foregroundS = "foreground service";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_background  = "background ";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_version = "preference version";



    /*** PREFバージョン（アップグレード時の下位互換用） */

    @SuppressWarnings("WeakerAccess")
    final String PREF_VER = "1.0.0";

    private SharedPreferences mPref;

    private boolean mAutostart;
    private boolean mForeground;

    /**********************************************************************************************
     *
     * プリファレンス変更通知
     *
     *********************************************************************************************/

    interface callBack
    {
        void onPreferenceChanged(String key);
    }

    private callBack mListener;

    /**********************************************************************************************
     *
     *  コンストラクタ
     *
     * @param context       アプリケーションコンテキスト
     *
     *********************************************************************************************/

    AtomPreference(Context context)
    {
        mPref = PreferenceManager.getDefaultSharedPreferences(context);

        if (!mPref.contains(KEY_foregroundS))
        {
            SharedPreferences.Editor edit = mPref.edit();

            edit.putBoolean(KEY_foregroundS, true);
            edit.putBoolean(KEY_autostart, true);
            edit.putString(KEY_version, PREF_VER);
            edit.apply();
        }

        mAutostart = getPref_AutoStart();
        mForeground = getPref_ForegroundService();

        //ログ出力
        {
            String ver = mPref.getString(KEY_version, "");

            Log.w(TAG, "PREF [AUTO START] " + mAutostart + " [FOREGROUND] " + mForeground
                    + " [VER] " + ver);
        }
    }

    /**********************************************************************************************
     *
     *  プリファレンス変更通知に登録する
     *
     * @param listener  通知先オブジェクト
     *
     *********************************************************************************************/

    void setCallBack(callBack listener) {

        mListener = listener;

        mPref.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    /**********************************************************************************************
     *
     *  プリファレンス変更通知に登録解除する
     *
     *********************************************************************************************/

    void clearCallBack()
    {
        mListener = null;

        mPref.unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    /**********************************************************************************************
     *
     * プリファレンス（フレームワーク）からのコールバック
     *
     **********************************************************************************************/

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener()
            {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

                    if (mListener == null) return;

                    if (KEY_autostart.equals(key))
                    {
                        mAutostart = getPref_AutoStart();

                        Log.w(TAG,"[auto start] CHG "+ mAutostart);

                        mListener.onPreferenceChanged(key);
                    }
                    else if (KEY_foregroundS.equals(key))
                    {
                        mForeground= getPref_ForegroundService();

                        Log.w(TAG,"[foreground service] CHG "+ mForeground);

                        mListener.onPreferenceChanged(key);
                    }
                    else if(KEY_background.equals(key))
                    {
                        Log.w(TAG,"[background] CHG ");

                        mListener.onPreferenceChanged(key);
                    }
                }
            };

    /**********************************************************************************************
     *
     *  自動スタート読み出し
     *
     *********************************************************************************************/

    boolean getPref_AutoStart()
    {
        return mPref.getBoolean(KEY_autostart, true);
    }

    /**********************************************************************************************
     *
     *  フォアグランドサービス読み出し
     *
     *********************************************************************************************/

    boolean getPref_ForegroundService()
    {
        return mPref.getBoolean(KEY_foregroundS, true);
    }

    /**********************************************************************************************
     *
     *  自動スタート書き込み
     *
     *********************************************************************************************/

    void setPref_AutoStart(boolean enable)
    {
        Log.e(TAG,"[auto start] SET "+ enable);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_autostart, enable);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  フォアグランドサービス書き込み
     *
     *********************************************************************************************/

    void setPref_ForegroundService(boolean enable)
    {
        Log.e(TAG,"[foreground service] SET "+ enable);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_foregroundS, enable);
        edit.apply();
    }

    void setPref_background(String bmtStr)
    {
        SharedPreferences.Editor    edit = mPref.edit();

        edit.putString(KEY_background, bmtStr);
        edit.apply();
    }

    String getPref_background()
    {
        return mPref.getString(KEY_background, "");
    }
}
