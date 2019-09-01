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
    @SuppressWarnings({"unused"})
    private static final String TAG = AtomPreference.class.getSimpleName();

    private final String KEY_version        = "preference version";

    /*** 保存パラメータ　*/
    static final String KEY_autoStart       = "auto start";
    static final String KEY_foregroundS     = "foreground service";
    static final String KEY_background      = "background ";
    static final String KEY_pttWakeup       = "ptt wakeup";
    static final String KEY_statusHidden    = "status hidden";
    static final String KEY_cameraExec      = "camera execute";
    static final String KEY_fontScale       = "font scale";

    /*** PREFバージョン（アップグレード時の下位互換用） */
    final String PREF_VER                   = "1.1.5";

    private final SharedPreferences mPref;

    private boolean mAutoStart;
    private boolean mForeground;

    private boolean mPttWakeup;
    private boolean mStatusHidden;
    private boolean mCameraExec;
    private Float   mFontScale;

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
            edit.putBoolean(KEY_autoStart, true);

            edit.putString(KEY_version, PREF_VER);

            edit.putBoolean(KEY_pttWakeup, true);
            edit.putBoolean(KEY_statusHidden,false);
            edit.putBoolean(KEY_cameraExec, true);
            edit.putFloat(KEY_fontScale, 1.3f);
            edit.apply();
        }
        else if (!getPref_VER().equals(PREF_VER))
        {
            Log.w(TAG, "version up "+ getPref_VER() + " -> " + PREF_VER);

            SharedPreferences.Editor edit = mPref.edit();

            edit.putString(KEY_version, PREF_VER);

            edit.putBoolean(KEY_pttWakeup, true);
            edit.putBoolean(KEY_statusHidden,false);
            edit.putBoolean(KEY_cameraExec, true);
            edit.putBoolean(KEY_cameraExec, true);
            edit.putFloat(KEY_fontScale, 1.3f);
            edit.apply();
        }

        mAutoStart = getPref_AutoStart();
        mForeground     = getPref_ForegroundService();

        mPttWakeup      = getPref_PttWakeup();
        mStatusHidden   = getPref_StatusHidden();
        mCameraExec     = getPref_CameraExec();
        mFontScale      = getPref_FontScale();
        //ログ出力
        {
            String ver = mPref.getString(KEY_version, "");

            Log.w(TAG, "PREF [AUTO START] " + mAutoStart + " [FOREGROUND] " + mForeground
                    +" [PTT] " + mPttWakeup + " [STATUS HIDDEN] " + mStatusHidden
                    +" [CAM] "+ mCameraExec + " [FONT] "+ mFontScale + " [VER] " + ver);
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

                    if (KEY_autoStart.equals(key))
                    {
                        mAutoStart = getPref_AutoStart();

                        Log.w(TAG,"[auto start] CHG "+ mAutoStart);

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
                    else if (KEY_pttWakeup.equals(key))
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
                    else if(KEY_fontScale.equals(key))
                    {
                        mFontScale = getPref_FontScale();

                        Log.w(TAG,"[font] CHG: "+ mFontScale);

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
     *  自動スタート
     *
     *********************************************************************************************/

    boolean getPref_AutoStart()
    {
        return mPref.getBoolean(KEY_autoStart, true);
    }

    void setPref_AutoStart(boolean enable)
    {
        Log.e(TAG,"[auto start] SET "+ enable);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_autoStart, enable);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  フォアグランドサービス
     *
     *********************************************************************************************/

    boolean getPref_ForegroundService()
    {
        return mPref.getBoolean(KEY_foregroundS, true);
    }

    void setPref_ForegroundService(boolean enable)
    {
        Log.e(TAG,"[foreground service] SET "+ enable);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_foregroundS, enable);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  PTTパワーオン＆アクティビティ起動
     *
     *********************************************************************************************/

    boolean getPref_PttWakeup()
    {
        return mPref.getBoolean(KEY_pttWakeup, true);
    }

    void setPref_PttWakeup(boolean on)
    {
        Log.e(TAG,"[ptt wakeup]  SET "+ on);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_pttWakeup, on);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  ステータスバーを隠す
     *
     *********************************************************************************************/

    boolean getPref_StatusHidden()
    {
        return mPref.getBoolean(KEY_statusHidden, true);
    }

    void setPref_StatusHidden(boolean off)
    {
        Log.e(TAG,"[status hidden]  SET "+ off);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_statusHidden, off);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  カメラ連動
     *
     *********************************************************************************************/

    boolean getPref_CameraExec()
    {
        return mPref.getBoolean(KEY_cameraExec, true);
    }

    void setPref_CameraExec(boolean on)
    {
        Log.e(TAG,"[camera] SET "+ on);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putBoolean(KEY_cameraExec, on);
        edit.apply();
    }

    /**********************************************************************************************
     *
     *  壁紙の切替
     *
     *********************************************************************************************/

    String getPref_background() {  return mPref.getString(KEY_background, ""); }

    void setPref_background(String bmtStr)
    {
        Log.e(TAG,"[background] SET ");

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putString(KEY_background, bmtStr);
        edit.apply();
    }

    /*********************************************************************************************
     *
     *  FONT SCALE
     *
     *********************************************************************************************/

    float getPref_FontScale()
    {
        return mPref.getFloat(KEY_fontScale,1.0f);
    }
    void setPref_FontScale(float scale)
    {
        Log.e(TAG,"[font] SET "+ scale);

        SharedPreferences.Editor    edit = mPref.edit();

        edit.putFloat(KEY_fontScale,scale);
        edit.apply();
    }
}
