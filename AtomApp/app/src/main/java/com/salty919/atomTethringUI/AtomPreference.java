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

@SuppressWarnings("ALL")
class AtomPreference
{
    @SuppressWarnings({"unused"})
    private static final String TAG = AtomPreference.class.getSimpleName();

    /*** 保存パラメータ　*/

    @SuppressWarnings({"WeakerAccess", "SpellCheckingInspection"})
    static final String KEY_autostart       = "auto start";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_foregroundS     = "foreground service";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_background      = "background ";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_version         = "preference version";
    /** ver 1.0.1 **/
    @SuppressWarnings({"WeakerAccess", "SpellCheckingInspection"})
    static final String KEY_pttwakeup       = "ptt wakeup";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_statusHidden    = "status hidden";
    @SuppressWarnings("WeakerAccess")
    static final String KEY_cameraExec      = "camera execute";


    /*** PREFバージョン（アップグレード時の下位互換用） */

    @SuppressWarnings("WeakerAccess")
    final String PREF_VER                   = "1.1.4";

    private final SharedPreferences mPref;

    private boolean mAutostart;
    private boolean mForeground;

    private boolean mPttWakeup;
    private boolean mStatusHidden;
    private boolean mCameraExec;

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

            edit.putBoolean(KEY_pttwakeup, true);
            edit.putBoolean(KEY_statusHidden,false);
            edit.putBoolean(KEY_cameraExec, true);

            edit.apply();
        }
        else if (!getPref_VER().equals(PREF_VER))
        {
            Log.w(TAG, "version up "+ getPref_VER() + " -> " + PREF_VER);

            SharedPreferences.Editor edit = mPref.edit();

            edit.putString(KEY_version, PREF_VER);

            edit.putBoolean(KEY_pttwakeup, true);
            edit.putBoolean(KEY_statusHidden,false);
            edit.putBoolean(KEY_cameraExec, true);
            edit.apply();
        }

        mAutostart      = getPref_AutoStart();
        mForeground     = getPref_ForegroundService();

        mPttWakeup      = getPref_PttWakeup();
        mStatusHidden   = getPref_StatusHidden();
        mCameraExec     = getPref_CameraExec();

        //ログ出力
        {
            String ver = mPref.getString(KEY_version, "");

            Log.w(TAG, "PREF [AUTO START] " + mAutostart + " [FOREGROUND] " + mForeground
                    +" [PTT] " + mPttWakeup + " [STATUS HIDDEN] " + mStatusHidden
                    +" [CAM] "+ mCameraExec + " [VER] " + ver);
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
                    else if (KEY_pttwakeup.equals(key))
                    {
                        mPttWakeup = getPref_PttWakeup();

                        Log.w(TAG, "[ptt wakeup] CHG; "+ mPttWakeup );

                        mListener.onPreferenceChanged(key);
                    }
                    else if (KEY_statusHidden.equals(key))
                    {
                        mStatusHidden = getPref_StatusHidden();

                        Log.w(TAG, "[status hidden] CHG; "+ mStatusHidden );

                        mListener.onPreferenceChanged(key);
                    }
                    else if(KEY_cameraExec.equals(key))
                    {
                        mCameraExec = getPref_CameraExec();

                        Log.w(TAG,"[camera] CHG: "+ mCameraExec);

                        mListener.onPreferenceChanged(key);
                    }
                }
            };

    /**********************************************************************************************
     *
     *  プリファレンスバージョン
     *
     *********************************************************************************************/

    private String getPref_VER()
    {
        return mPref.getString(KEY_version, "");
    }

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
     *  PTTパワーオン＆アクティビティ起動
     *
     *********************************************************************************************/

    @SuppressWarnings("WeakerAccess")
    boolean getPref_PttWakeup()
    {
        return mPref.getBoolean(KEY_pttwakeup, true);
    }


    /**********************************************************************************************
     *
     *  ステータスバーを隠す
     *
     *********************************************************************************************/

    @SuppressWarnings("WeakerAccess")
    boolean getPref_StatusHidden()
    {
        return mPref.getBoolean(KEY_statusHidden, true);
    }

    /**********************************************************************************************
     *
     *  かめら連動
     *
     *********************************************************************************************/

    @SuppressWarnings("WeakerAccess")
    boolean getPref_CameraExec()
    {
        return mPref.getBoolean(KEY_cameraExec, true);
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

    /**********************************************************************************************
     *
     *  Camera 呼び出し有効／無効
     *
     *********************************************************************************************/

    void setPref_CameraExec(boolean on)
    {
        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_cameraExec, on);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  PTTパワーオン＆アクティビティ起動
     *
     *********************************************************************************************/

    void setPref_PttWakeup(boolean on)
    {
        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_pttwakeup, on);
        edit.apply();
    }
}
