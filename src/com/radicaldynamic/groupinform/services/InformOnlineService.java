/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.radicaldynamic.groupinform.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.util.Log;

import com.radicaldynamic.groupinform.R;
import com.radicaldynamic.groupinform.activities.AccountDeviceList;
import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.logic.AccountDevice;
import com.radicaldynamic.groupinform.logic.InformOnlineState;
import com.radicaldynamic.groupinform.utilities.FileUtils;
import com.radicaldynamic.groupinform.utilities.HttpUtils;

/**
 * 
 */
public class InformOnlineService extends Service {    
    private static final String t = "InformOnlineService: ";

    private String mHost;
    private int mPort;
    
    // Assume that we are connected when this service starts (we *should* be)
    @SuppressWarnings("unused")
    private boolean mConnected = true;
    
    // Whether or not we are currently attempting to connect to the service
    private boolean mConnecting = false;
    
    // This is the object that receives interactions from clients.  
    // See RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();
  
    private ConditionVariable mCondition;
    
    private Runnable mTask = new Runnable() {
        public void run() {            
            for (int i = 1; i > 0; ++i) {
                if (mConnecting == false)
                    connect();
                // Retry connection to Inform Online service every 10 minutes
                if (mCondition.block(600 * 1000))
                    break;
            }
        }
    };
      
    @Override
    public void onCreate() {        
        Thread persistentConnectionThread = new Thread(null, mTask, "InformOnlineService");        
        mCondition = new ConditionVariable(false);
        persistentConnectionThread.start();
    }
    
    @Override
    public void onDestroy() {
        mCondition.open();
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        public InformOnlineService getService() {
            return InformOnlineService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    private void connect()
    {
        mConnecting = true;
        
        mHost = getString(R.string.tf_default_couchdb_server);
        mPort = 5100;

        Log.d(Collect.LOGTAG, t + "pinging " + mHost + ":" + mPort);
        
        // Try to ping the service to see if it is "up" (and determine whether we are registered)
        String pingUrl = Collect.getInstance().getInformOnline().getServerUrl() + "/ping";
        String jsonResult = HttpUtils.getUrlData(pingUrl);
        JSONObject ping;
        
        try {
            Log.d(Collect.LOGTAG, t + "parsing jsonResult " + jsonResult);                
            ping = (JSONObject) new JSONTokener(jsonResult).nextValue();
            
            String result = ping.optString(InformOnlineState.RESULT, InformOnlineState.ERROR);
            
            if (result.equals(InformOnlineState.OK)) {
                // Online and registered (checked in)
                Log.i(Collect.LOGTAG, t + "ping successful (we are connected and checked in)");
                mConnected = true;
                
                // Update our list of account devices (aka members)
                AccountDeviceList.fetchDeviceList();
                loadDeviceHash();
                
                // Update our list of account groups (aka form groups)
                // TODO
            } else if (result.equals(InformOnlineState.FAILURE)) {
                Log.w(Collect.LOGTAG, t + "ping failed (will attempt checkin)");
                
                if (Collect.getInstance().getInformOnline().hasRegistration() && Collect.getInstance().getInformOnline().checkin()) {
                    Log.i(Collect.LOGTAG, t + "checkin successful (we are connected)");
                    mConnected = true;
                } else {
                    Log.w(Collect.LOGTAG, t + "checkin failed (registration invalid)");
                    /*
                     * We're no longer registered even though we thought we were at one point.
                     * 
                     * Now what?
                     */
                }
            } else {
                Log.w(Collect.LOGTAG, t + "ping failed (we are offline)");
                mConnected = false;
                // Fatal error (we're offline)
            }
        } catch (NullPointerException e) {
            // This usually indicates a communication error and will send us into an offline state
            Log.e(Collect.LOGTAG, t + "ping error while communicating with service (we are offline)");
            e.printStackTrace();
            mConnected = false;
        } catch (JSONException e) {
            // Parse errors (malformed result) send us into an offline state
            Log.e(Collect.LOGTAG, t + "ping error while parsing jsonResult " + jsonResult + " (we are offline)");
            e.printStackTrace();
            mConnected = false;
        } finally {
            
            
            // Unblock
            mConnecting = false;
        }
    }

    /*
     * Parse the cached device hash and load it into memory for lookup by other pieces of this application.
     * This allows us to have some fall back if we cannot connect to Inform Online immediately.
     */
    private void loadDeviceHash()
    {
        Log.d(Collect.LOGTAG , t + "loading device cache");
              
        try {
            FileInputStream fis = new FileInputStream(new File(FileUtils.DEVICE_CACHE_FILE_PATH));        
            InputStreamReader reader = new InputStreamReader(fis);
            BufferedReader buffer = new BufferedReader(reader, 8192);
            StringBuilder sb = new StringBuilder();
            
            String cur;
    
            while ((cur = buffer.readLine()) != null) {
                sb.append(cur + "\n");
            }
            
            buffer.close();
            reader.close();
            fis.close();
            
            try {
                JSONArray jsonDevices = (JSONArray) new JSONTokener(sb.toString()).nextValue();
                
                for (int i = 0; i < jsonDevices.length(); i++) {
                    JSONObject jsonDevice = jsonDevices.getJSONObject(i);
    
                    AccountDevice device = new AccountDevice(
                            jsonDevice.getString("id"),
                            jsonDevice.getString("alias"),
                            jsonDevice.getString("email"));
    
                    // Optional information that will only be present if the user is also an account owner
                    device.setLastCheckin(jsonDevice.getString("lastCheckin"));
                    device.setPin(jsonDevice.getString("pin"));
                    device.setStatus(jsonDevice.getString("status"));
                    device.setTransferStatus(jsonDevice.getString("transfer"));
    
                    Collect.getInstance().getAccountDevices().put(device.getId(), device);
                }
            } catch (JSONException e) {
                // Parse error (malformed result)
                Log.e(Collect.LOGTAG, t + "failed to parse jsonResult " + sb.toString());
                e.printStackTrace();
            }
        } catch (Exception e) {
            Log.e(Collect.LOGTAG, t + "unable to read device cache: " + e.toString());
            e.printStackTrace();
        }
    }
}