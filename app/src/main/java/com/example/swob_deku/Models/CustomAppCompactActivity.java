package com.example.swob_deku.Models;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Telephony;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;

import com.example.swob_deku.BroadcastReceivers.IncomingDataSMSBroadcastReceiver;
import com.example.swob_deku.DefaultCheckActivity;
import com.example.swob_deku.Models.Archive.ArchiveHandler;
import com.example.swob_deku.Models.SMS.SMSHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class CustomAppCompactActivity extends AppCompatActivity {

    BroadcastReceiver incomingDataBroadcastReceiver;
    BroadcastReceiver incomingBroadcastReceiver;
    BroadcastReceiver messageStateChangedBroadcast;

    ArchiveHandler archiveHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!_checkIsDefaultApp()) {
            startActivity(new Intent(this, DefaultCheckActivity.class));
            finish();
        }

        _instantiateGlobals();
    }

    private void _instantiateGlobals(){
        archiveHandler = new ArchiveHandler(getApplicationContext());
    }

    private boolean _checkIsDefaultApp() {
        final String myPackageName = getPackageName();
        final String defaultPackage = Telephony.Sms.getDefaultSmsPackage(this);

        return myPackageName.equals(defaultPackage);
    }

    public void configureBroadcastListeners(Runnable runnable) {
        incomingBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(runnable != null) {
//                    cancelNotifications(threadId);
//                    defaultViewModel.informNewItemChanges();
                    runnable.run();
                }
            }
        };

        incomingDataBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(runnable != null) {
//                try {
//                    checkEncryptedMessaging();
//                } catch (GeneralSecurityException | IOException e) {
//                    e.printStackTrace();
//                }
                }
            }
        };

        messageStateChangedBroadcast = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, @NonNull Intent intent) {
                if(runnable != null) {
//                    defaultViewModel.informNewItemChanges();
                }
            }
        };

        registerReceiver(messageStateChangedBroadcast,
                new IntentFilter(SMSHandler.MESSAGE_STATE_CHANGED_BROADCAST_INTENT));

        // SMS_RECEIVED = global broadcast informing all apps listening a message has arrived
        registerReceiver(incomingBroadcastReceiver,
                new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));

        registerReceiver(incomingDataBroadcastReceiver,
                new IntentFilter(Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION));

        registerReceiver(incomingDataBroadcastReceiver,
                new IntentFilter(IncomingDataSMSBroadcastReceiver.DATA_BROADCAST_INTENT));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (incomingBroadcastReceiver != null)
            unregisterReceiver(incomingBroadcastReceiver);

        if (incomingDataBroadcastReceiver != null)
            unregisterReceiver(incomingDataBroadcastReceiver);

        if (messageStateChangedBroadcast != null)
            unregisterReceiver(messageStateChangedBroadcast);

        if (archiveHandler != null)
            archiveHandler.close();
    }

}
