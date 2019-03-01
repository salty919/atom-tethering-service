package com.salty919.atomTethringService;

public class AtomDevice
{
    private     long            mId         = 0;
    private     ConnectionType  mType       = ConnectionType.OTHER;
    private     String          mIp         = "";
    private     int             mMask       = 0;
    private     String          mBroadcast  = "";
    private     String          mDeviceName = "";
    private     boolean         mFound      = false;

    public void setId(long id)
    {
        this.mId= id;
    }

    public long getId()
    {
        return mId;
    }

    public void setType(ConnectionType type)
    {
        this.mType = type;
    }

    public ConnectionType getType()
    {
        return mType;
    }

    public void setIp(String ip)
    {
        this.mIp = ip;
    }

    public String getIp()
    {
        return mIp;
    }

    public void setBcast(String bcast)
    {
        this.mBroadcast= bcast;
    }
    public String getBcast()
    {
        return mBroadcast;
    }

    public void setName(String name)
    {
        this.mDeviceName = name;
    }

    public String getName()
    {
        return mDeviceName;
    }

    public void setMask(int mask)
    {
        this.mMask = mask;
    }

    public int getMask()
    {
        return mMask;
    }

    public void found()
    {
        this.mFound = true;
    }

    public boolean isFound()
    {
        return mFound;
    }
}
