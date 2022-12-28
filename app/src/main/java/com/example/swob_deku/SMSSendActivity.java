package com.example.swob_deku;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.swob_deku.Commons.Contacts;
import com.example.swob_deku.Commons.Helpers;
import com.example.swob_deku.Models.Messages.MessagesThreadViewModel;
import com.example.swob_deku.Models.Messages.SingleMessageViewModel;
import com.example.swob_deku.Models.Messages.SingleMessagesThreadRecyclerAdapter;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.google.android.material.textfield.TextInputEditText;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SMSSendActivity extends AppCompatActivity {

    // TODO: incoming message MessagesThread
    // TODO: incoming message from notification
    // TODO: incoming message from shared intent

    SingleMessageViewModel singleMessageViewModel;

    public static final String ADDRESS = "address";
    public static final String THREAD_ID = "thread_id";
    public static final String ID = "_id";
    public static final String SEARCH_STRING = "search_string";

    public static final String SMS_SENT_INTENT = "SMS_SENT";
    public static final String SMS_DELIVERED_INTENT = "SMS_DELIVERED";

    public static final int SEND_SMS_PERMISSION_REQUEST_CODE = 1;

    String threadId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_smsactivity);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.send_smsactivity_toolbar);
        setSupportActionBar(myToolbar);

        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);

        getMessagesThreadId();

        // TODO: should be used when message is about to be sent
//        if(!checkPermissionToSendSMSMessages())
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_PERMISSION_REQUEST_CODE);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(false);
        linearLayoutManager.setReverseLayout(true);

        RecyclerView singleMessagesThreadRecyclerView = findViewById(R.id.single_messages_thread_recycler_view);

        SingleMessagesThreadRecyclerAdapter singleMessagesThreadRecyclerAdapter = new SingleMessagesThreadRecyclerAdapter(
                this,
                R.layout.messages_thread_received_layout,
                R.layout.messages_thread_sent_layout,
                R.layout.messages_thread_timestamp_layout,
                null,
                null);
        singleMessagesThreadRecyclerView.setLayoutManager(linearLayoutManager);
        singleMessagesThreadRecyclerView.setAdapter(singleMessagesThreadRecyclerAdapter);

        singleMessageViewModel = new ViewModelProvider(this).get(
                SingleMessageViewModel.class);

        singleMessageViewModel.getMessages(getApplicationContext(), threadId).observe(this,
                new Observer<List<SMS>>() {
                    @Override
                    public void onChanged(List<SMS> smsList) {
                        singleMessagesThreadRecyclerAdapter.submitList(smsList);
                    }
                });

//        processForSharedIntent();
//
//        handleIncomingMessage();
//
//        cancelNotifications(getIntent().getStringExtra(THREAD_ID));
//
//        improveMessagingUX();
    }

    private void getMessagesThreadId() {
        if(getIntent().hasExtra(THREAD_ID))
            threadId = getIntent().getStringExtra(THREAD_ID);

        else if(getIntent().hasExtra(ADDRESS)) {
            String address = getIntent().getStringExtra(ADDRESS);
            Cursor cursor = SMSHandler.fetchSMSMessagesAddress(getApplicationContext(), address);
            if(cursor.moveToFirst()) {
                do {
                    SMS sms = new SMS(cursor);
                    String smsThreadId = sms.getThreadId();

                    if(PhoneNumberUtils.compare(address, sms.getAddress()) && !smsThreadId.equals("-1")) {
                        threadId = smsThreadId;
                        break;
                    }
                }
                while(cursor.moveToNext());
            }
        }
    }

    private void improveMessagingUX() {
        runOnUiThread(new Runnable() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public void run() {
                ActionBar ab = getSupportActionBar();
                String address = getIntent().getStringExtra(ADDRESS);

                EditText smsText = findViewById(R.id.sms_text);
                smsText.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {

                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        if ((motionEvent.getAction() & MotionEvent.ACTION_UP) != 0 &&
                                (motionEvent.getActionMasked() & MotionEvent.ACTION_UP) != 0)
                        {
                            view.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        return false;
                    }
                });

                if(!PhoneNumberUtils.isWellFormedSmsAddress(address)) {
                    ConstraintLayout smsLayout = findViewById(R.id.send_message_content_layouts);
                    smsLayout.setVisibility(View.GONE);
                }

                // TODO: if has letters, make sure reply cannot happen
                ab.setTitle(Contacts.retrieveContactName(getApplicationContext(), address));
            }
        });
    }

    private void processForSharedIntent() {
        String indentAction = getIntent().getAction();
        if(indentAction != null && getIntent().getAction().equals(Intent.ACTION_SENDTO)) {
            String sendToString = getIntent().getDataString();

            if(BuildConfig.DEBUG)
                Log.d("", "Processing shared #: " + sendToString);

            if(sendToString.contains("%2B"))
                sendToString = sendToString.replace("%2B", "+")
                                .replace("%20", "");

            if(BuildConfig.DEBUG)
                Log.d("", "Working on a shared Intent... " + sendToString);

            if(sendToString.indexOf("smsto:") > -1 || sendToString.indexOf("sms:") > -1) {
               String address = sendToString.substring(sendToString.indexOf(':') + 1);
               String text = getIntent().getStringExtra("sms_body");

               byte[] bytesData = getIntent().getByteArrayExtra(Intent.EXTRA_STREAM);
               if(bytesData != null) {
                   Log.d(getClass().getName(), "Byte data: " + bytesData);
                   Log.d(getClass().getName(), "Byte data: " + new String(bytesData, StandardCharsets.UTF_8));

                   text = new String(bytesData, StandardCharsets.UTF_8);
               }

               getIntent().putExtra(ADDRESS, address);
               TextInputEditText send_text = findViewById(R.id.sms_text);
               send_text.setText(text);
            }
        }
    }

    public void handleSentMessages() {
//        https://developer.android.com/reference/android/telephony/SmsManager.html#sendTextMessage(java.lang.String,%20java.lang.String,%20java.lang.String,%20android.app.PendingIntent,%20android.app.PendingIntent,%20long)
        BroadcastReceiver sentBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, @NonNull Intent intent) {
                long id = intent.getLongExtra(ID, -1);
                switch(getResultCode()) {

                    case Activity.RESULT_OK:
                        try {
                            SMSHandler.registerSentMessage(context, id);
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }
                        break;

                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                    default:
                        SMSHandler.registerFailedMessage(context, id, getResultCode());
                        if(BuildConfig.DEBUG) {
                            Log.d(getLocalClassName(), "Failed to send: " + getResultCode());
                            Log.d(getLocalClassName(), "Failed to send: " + getResultData());
                            Log.d(getLocalClassName(), "Failed to send: " + intent.getData());
                        }
                }
                if(isCurrentlyActive()) {
                    unregisterReceiver(this);
                }
            }
        };
        registerReceiver(sentBroadcastReceiver, new IntentFilter(SMS_SENT_INTENT));
    }

    public boolean isCurrentlyActive() {
        return this.getWindow().getDecorView().getRootView().isShown();
    }

    public void handleDeliveredMessages() {
//        https://developer.android.com/reference/android/telephony/SmsManager.html#sendTextMessage(java.lang.String,%20java.lang.String,%20java.lang.String,%20android.app.PendingIntent,%20android.app.PendingIntent,%20long)
        BroadcastReceiver deliveredBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(ID, -1);
                switch(getResultCode()) {

                    case Activity.RESULT_OK:
                        SMSHandler.registerDeliveredMessage(context, id);
                        break;
                    default:
                        if(BuildConfig.DEBUG)
                            Log.d(getLocalClassName(), "Failed to deliver: " + getResultCode());
                }
                if(isCurrentlyActive()) {
                    unregisterReceiver(this);
                }
            }
        };
        registerReceiver(deliveredBroadcastReceiver, new IntentFilter(SMS_DELIVERED_INTENT));
    }

    public void cancelNotifications(String threadId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(
                getApplicationContext());

        if(getIntent().hasExtra(THREAD_ID))
            notificationManager.cancel(Integer.parseInt(threadId));
    }

    private void handleIncomingMessage() {
        BroadcastReceiver incomingBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
                    for (SmsMessage currentSMS: Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                        // currentSMS = SMSHandler.getIncomingMessage(aObject, bundle);

                        // TODO: Fetch address name from contact list if present
                        String address = currentSMS.getDisplayOriginatingAddress();
                        Cursor cursor = SMSHandler.fetchSMSMessagesAddress(context, address);
                        if(cursor.moveToFirst()) {
                            SMS sms = new SMS(cursor);
                            if (isCurrentlyActive() && sms.getThreadId().equals(getIntent().getStringExtra(THREAD_ID))) {
                                getIntent().putExtra(ADDRESS, sms.getAddress());
                                cancelNotifications(sms.getThreadId());
                            }
                        }
                    }
                }
            }
        };

        // SMS_RECEIVED = global broadcast informing all apps listening a message has arrived
         registerReceiver(incomingBroadcastReceiver, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
    }

    List<SMS> getMessagesFromCursor(@NonNull Cursor cursor) {
        List<SMS> appendedList = new ArrayList<>();
        Date previousDate = null;
        Calendar currentDate = Calendar.getInstance();
        Calendar previousDateCalendar = Calendar.getInstance();

        if(cursor.moveToFirst()) {
            do {
                SMS sms = new SMS(cursor);
                Date date = new Date(Long.parseLong(sms.getDate()));
                if (previousDate != null) {
                    currentDate.setTime(date);
                    previousDateCalendar.setTime(previousDate);
                    if ((currentDate.get(Calendar.DATE) != previousDateCalendar.get(Calendar.DATE)) ||
                            (currentDate.get(Calendar.HOUR_OF_DAY) != previousDateCalendar.get(Calendar.HOUR_OF_DAY))) {
                        appendedList.add(new SMS(appendedList.get(appendedList.size() - 1).getDate()));
                    }
                }
                appendedList.add(sms);
                previousDate = date;
            }
            while (cursor.moveToNext());
            cursor.moveToLast();
            appendedList.add(new SMS(new SMS(cursor).getDate()));
        }

        return appendedList;
    }

    void populateMessageThread() {
        String threadId = "-1";
        if(getIntent().hasExtra(THREAD_ID))
            threadId = getIntent().getStringExtra(THREAD_ID);

        else if(getIntent().hasExtra(ADDRESS)) {
            String address = getIntent().getStringExtra(ADDRESS);
            Cursor cursor = SMSHandler.fetchSMSMessagesAddress(getApplicationContext(), address);
            if(cursor.moveToFirst()) {
                do {
                    SMS sms = new SMS(cursor);
                    String smsThreadId = sms.getThreadId();

                    if(PhoneNumberUtils.compare(address, sms.getAddress()) && !smsThreadId.equals("-1")) {
                        threadId = smsThreadId;
                        break;
                    }
                }
                while(cursor.moveToNext());
            }
        }

        Cursor cursor = SMSHandler.fetchSMSForThread(getApplicationContext(), threadId);
        List<SMS> messagesForThread = getMessagesFromCursor(cursor);

        String finalThreadId = threadId;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SMSHandler.updateSMSMessagesThreadStatus(getApplicationContext(), finalThreadId, "1");
                }
                catch(Exception e ) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    public void sendMessage(View view) {
        // TODO: Don't let sending happen if message box is empty
        String destinationAddress = getIntent().getStringExtra(ADDRESS);
        TextView smsTextView = findViewById(R.id.sms_text);
        String text = smsTextView.getText().toString();

        try {
//            SMSHandler.registerOutgoingMessage(getApplicationContext(), destinationAddress, text);
            long messageId = Helpers.generateRandomNumber();
            Intent sentIntent = new Intent(SMS_SENT_INTENT);
            sentIntent.putExtra(ID, messageId);

            Intent deliveredIntent = new Intent(SMS_DELIVERED_INTENT);
            deliveredIntent.putExtra(ID, messageId);

             PendingIntent sentPendingIntent = PendingIntent.getBroadcast(this, 200,
                     sentIntent,
                     PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

            PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(this, 150,
                    deliveredIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

            handleSentMessages();
            handleDeliveredMessages();

//            if(getIntent().hasExtra(Intent.EXTRA_STREAM)) {
//                byte[] data = getIntent().getByteArrayExtra(Intent.EXTRA_STREAM);
//                SMSHandler.sendSMS(getApplicationContext(), destinationAddress, data,
//                        sentPendingIntent, deliveredPendingIntent, messageId);
//                getIntent().removeExtra(Intent.EXTRA_STREAM);
//            }
//            else
//                SMSHandler.sendSMS(getApplicationContext(), destinationAddress, text,
//                        sentPendingIntent, deliveredPendingIntent, messageId);

            SMSHandler.sendSMS(getApplicationContext(), destinationAddress, text.getBytes(StandardCharsets.UTF_8),
                    sentPendingIntent, deliveredPendingIntent, messageId);


            smsTextView.setText("");
        }

        catch(IllegalArgumentException e ) {
            e.printStackTrace();
            Toast.makeText(this, "Make sure Address and Text are provided.", Toast.LENGTH_LONG).show();
        }
        catch(Exception e ) {
            e.printStackTrace();
            Toast.makeText(this, "Something went wrong, check log stack", Toast.LENGTH_LONG).show();
        }

    }

    public boolean checkPermissionToSendSMSMessages() {
        int check = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);

        return (check == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case SEND_SMS_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {
                    Toast.makeText(this, "Let's do this!!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // cancelNotifications(getIntent().getStringExtra(THREAD_ID));
    }

}