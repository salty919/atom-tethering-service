package com.salty919.atomTethringService;

public interface AtomPttInterface
{

    /** 機能が有効/無効に変化した通知         */
    void onReady(boolean enable);

    /** PTTが押された通知                   */
    boolean onPttPress();

    /** PTTが長時間押された通知              */
    void onPttLongPress();

    /** PTTがクリックされた                 */
    void onPttClick();

    /** VOL-UPが押された通知               */
    boolean onVolPress();
}
