package com.salty919.atomTethringUI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.salty919.atomTethringService.AtomDevice;

import java.util.ArrayList;

public class DeviceAdapter extends BaseAdapter
{
    private final String TAG  = DeviceAdapter.class.getSimpleName();

    private final LayoutInflater          layoutInflater;
    private     ArrayList<AtomDevice>   deviceArrayList;

    DeviceAdapter(Context context)
    {
        this.layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    void setDeviceList(ArrayList<AtomDevice> deviceArrayList)
    {
        this.deviceArrayList = deviceArrayList;
    }

    @Override
    public int getCount()
    {
        try
        {
            Log.w(TAG, "getCount " + deviceArrayList.size());
            return deviceArrayList.size();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return  0;
        }
    }

    @Override
    public Object getItem(int position)
    {
        return deviceArrayList.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return deviceArrayList.get(position).getId();
    }

    @SuppressLint("ViewHolder")
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = layoutInflater.inflate(R.layout.device_item,parent,false);

        TextView    type    = convertView.findViewById(R.id.device_type);
        TextView    ip      = convertView.findViewById(R.id.device_ip);
        TextView    name    = convertView.findViewById(R.id.device_name);
        TextView    bcast   = convertView.findViewById(R.id.device_bcast);

        AtomDevice device = deviceArrayList.get(position);

        type.setText(device.getType().toString());

        ip.setText(device.getIp());

        name.setText(device.getName());

        if (device.getBcast().equals(""))
        {
            bcast.setText(R.string.nobcast);
            bcast.setTextColor(Color.GRAY);
        }
        else
        {
            bcast.setText(device.getBcast());
        }

        Log.w(TAG, "getView "+ device.getType().toString() + " " + device.getIp() +
                " " + device.getName() + " " + device.getBcast());

        return convertView;
    }
}
