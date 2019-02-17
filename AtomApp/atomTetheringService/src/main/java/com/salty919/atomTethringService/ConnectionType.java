package com.salty919.atomTethringService;

@SuppressWarnings("SpellCheckingInspection")
public enum ConnectionType
{
    NONE    (0, "NO-CONNECTION" ),
    WIFI    (1, "WIFI"          ),
    MOBILE  (2, "MOBILE"        ),
    TETHER  (3, "TETHER"        ),
    BTOOTH  (4, "BLUETOOTH"     ),
    ETHER   (5, "ETHER"         ),
    VPN     (6, "VPN"           ),
    OTHER   (7, "OTHER"         );

    final String mStr;
    @SuppressWarnings("unused")
    final int    mId;

    ConnectionType(int id, String str)
    {
        mStr = str;
        mId  = id;
    }

    @SuppressWarnings("unused")
    public static ConnectionType valueOf(int id)
    {
        for (ConnectionType ctype : values())
        {
            if (ctype.mId == id) return ctype;
        }

        throw new IllegalArgumentException("no such enum object for the id: " + id);
    }
}
