package com.afkanerd.deku.DefaultSMS.Models.Conversations;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import com.afkanerd.deku.DefaultSMS.DAO.ThreadedConversationsDao;
import com.afkanerd.deku.DefaultSMS.Models.NativeSMSDB;

public class ThreadedConversationsHandler {

    public static ThreadedConversations get(Context context, String address) {
        long threadId = Telephony.Threads.getOrCreateThreadId(context, address);
        ThreadedConversations threadedConversations = new ThreadedConversations();
        threadedConversations.setAddress(address);
        threadedConversations.setThread_id(String.valueOf(threadId));
        return threadedConversations;
    }

    public static ThreadedConversations get(Context context,
                                            ThreadedConversationsDao threadedConversationsDao,
                                            ThreadedConversations threadedConversations) throws InterruptedException {
//        final ThreadedConversations[] threadedConversations1 = {threadedConversations};
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                threadedConversations1[0] = threadedConversationsDao
//                        .get(threadedConversations.getThread_id());
//            }
//        });
//        thread.start();
//        thread.join();
        try(Cursor cursor =
                    NativeSMSDB.fetchByThreadId(context, threadedConversations.getThread_id())) {
            if(cursor.moveToFirst())
                threadedConversations = ThreadedConversations.build(cursor);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return threadedConversations;

//        return threadedConversations1[0];
    }

    public static void call(Context context, ThreadedConversations threadedConversations) {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        callIntent.setData(Uri.parse("tel:" + threadedConversations.getAddress()));

        context.startActivity(callIntent);
    }
}
