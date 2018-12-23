package com.salty919.atomTethringUI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/*************************************************************************************************
 *
 *  自動起動レシーバー；  MAINアクティティを起動します（プレファレンスが自動ONの場合）
 *
 *  ※OREOでもACTION_BOOT_COMPLETEDは例外で暗黙INTENTとして受信できる
 *  ※その他のINTENTは明示的にレシーバ登録しないと受けれないので注意
 *
 *  @author     salty919@gmail.com
 *  @version    0.90
 *
 *************************************************************************************************/

public class BootReceiver extends BroadcastReceiver
{
    private static final String TAG = BootReceiver.class.getSimpleName();

    // BroadcastIntentを受信した場合の処理 //
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
        {
            AtomPreference preference = new AtomPreference(context);

            if (preference.getPref_AutoStart())
            {
                Intent newIntent = new Intent(context, MainActivity.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                newIntent.putExtra(MainActivity.KEY_boot, true);
                Log.w(TAG, "Auto Boot AtomService");
                context.startActivity(newIntent);
            }
        }
    }
}
