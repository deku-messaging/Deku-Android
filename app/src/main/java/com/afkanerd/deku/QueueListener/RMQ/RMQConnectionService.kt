package com.afkanerd.deku.QueueListener.RMQ

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SubscriptionInfo
import android.util.Log
import android.view.inputmethod.CorrectionInfo
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.afkanerd.deku.DefaultSMS.BroadcastReceivers.IncomingTextSMSBroadcastReceiver
import com.afkanerd.deku.DefaultSMS.Models.Conversations.Conversation
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.DefaultSMS.Models.NativeSMSDB
import com.afkanerd.deku.DefaultSMS.Models.SIMHandler
import com.afkanerd.deku.DefaultSMS.Models.SMSDatabaseWrapper
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.Modules.SemaphoreManager
import com.afkanerd.deku.Modules.ThreadingPoolExecutor
import com.afkanerd.deku.QueueListener.GatewayClients.GatewayClient
import com.afkanerd.deku.QueueListener.GatewayClients.GatewayClientHandler
import com.afkanerd.deku.QueueListener.GatewayClients.GatewayClientListingActivity
import com.afkanerd.deku.QueueListener.GatewayClients.GatewayClientProjects
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.ConsumerShutdownSignalCallback
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.ShutdownSignalException
import com.rabbitmq.client.impl.DefaultExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.junit.Assert
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException

class RMQConnectionService : Service() {
    private lateinit var databaseConnector: Datastore
    private lateinit var subscriptionInfoList: List<SubscriptionInfo>
    private lateinit var messageStateChangedBroadcast: BroadcastReceiver
    private var gatewayClientLiveData: LiveData<GatewayClient> = MutableLiveData()

    val observer = Observer<GatewayClient> {
        Log.d(javaClass.name, "Gateway client changed")
        if(!it.activated) {
            stopSelf()
        }
        else if(it.state == GatewayClient.STATE_DISCONNECTED)
            ThreadingPoolExecutor.executorService.execute {
                connectGatewayClient(it)
            }
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createForegroundNotification()
        val gatewayClientId = intent.getLongExtra(GatewayClient.GATEWAY_CLIENT_ID, -1)
        Assert.assertTrue(gatewayClientId.toInt() != -1)
        gatewayClientLiveData = databaseConnector.gatewayClientDAO().fetchLiveData(gatewayClientId)
        gatewayClientLiveData.observeForever(observer)
//        ThreadingPoolExecutor.executorService.execute {
//            val gatewayClient = databaseConnector.gatewayClientDAO().fetch(gatewayClientId)
//            connectGatewayClient(gatewayClient)
//        }
//        gatewayClientLiveData.value!!.state = GatewayClient.STATE_RECONNECTING
//        databaseConnector.gatewayClientDAO().update(gatewayClientLiveData.value)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(messageStateChangedBroadcast)
        gatewayClientLiveData.removeObserver(observer)
        ThreadingPoolExecutor.executorService.execute {
            val gatewayClient = gatewayClientLiveData.value
            gatewayClient?.state = GatewayClient.STATE_DISCONNECTED
            Datastore.getDatastore(applicationContext).gatewayClientDAO().update(gatewayClient)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        databaseConnector = Datastore.getDatastore(applicationContext)
        subscriptionInfoList = SIMHandler.getSimCardInformation(applicationContext)
        handleBroadcast()
    }

    private fun handleBroadcast() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(IncomingTextSMSBroadcastReceiver.SMS_SENT_BROADCAST_INTENT)
        messageStateChangedBroadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != null && intentFilter.hasAction(intent.action)) {
                    Log.d(javaClass.name, "Received incoming broadcast")
                    if (intent.hasExtra(RMQConnection.MESSAGE_SID) &&
                            intent.hasExtra(RMQConnection.RMQ_DELIVERY_TAG)) {

                        val sid = intent.getStringExtra(RMQConnection.MESSAGE_SID)
                        val messageId = intent.getStringExtra(NativeSMSDB.ID)

                        val consumerTag = intent.getStringExtra(RMQConnection.RMQ_CONSUMER_TAG)
                        val deliveryTag =
                                intent.getLongExtra(RMQConnection.RMQ_DELIVERY_TAG, -1)
                        val rmqID = intent.getLongExtra(RMQConnection.RMQ_ID, -1)

                        Assert.assertTrue(!consumerTag.isNullOrEmpty())
                        Assert.assertTrue(deliveryTag != -1L)
                        Assert.assertTrue(rmqID != -1L)

                        rmqConnectionList.forEach {rmq ->
                            if(rmq.id == rmqID) {
                                rmq.findChannelByTag(consumerTag!!)?.let {
                                    Log.d(javaClass.name, "Received an ACK of the message...")
                                    if (resultCode == Activity.RESULT_OK) {
                                        ThreadingPoolExecutor.executorService.execute {
                                            if (it.isOpen) it.basicAck(deliveryTag, false)
                                        }
                                    } else {
                                        Log.w(javaClass.name, "Rejecting message sent")
                                        ThreadingPoolExecutor.executorService.execute {
                                            if (it.isOpen) it.basicReject(deliveryTag, true)
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }

        registerReceiver(messageStateChangedBroadcast, intentFilter, RECEIVER_EXPORTED)
    }

    @Serializable
    private data class SMSRequest(val text: String, val to: String, val sid: String, val id: String)
    private suspend fun sendSMS(smsRequest: SMSRequest,
                                subscriptionId: Int,
                                consumerTag: String,
                                deliveryTag: Long,
                                rmqConnectionId: Long) {
        SemaphoreManager.resourceSemaphore.acquire()
        val messageId = System.currentTimeMillis()
        SemaphoreManager.resourceSemaphore.release()

        val threadId = Telephony.Threads.getOrCreateThreadId(applicationContext, smsRequest.to)

        val bundle = Bundle()
        bundle.putString(RMQConnection.MESSAGE_SID, smsRequest.sid)
        bundle.putString(RMQConnection.RMQ_CONSUMER_TAG, consumerTag)
        bundle.putLong(RMQConnection.RMQ_DELIVERY_TAG, deliveryTag)
        bundle.putLong(RMQConnection.RMQ_ID, rmqConnectionId)

        val conversation = Conversation()
        conversation.message_id = messageId.toString()
        conversation.text = smsRequest.text
        conversation.address = smsRequest.to
        conversation.subscription_id = subscriptionId
        conversation.type = Telephony.Sms.MESSAGE_TYPE_OUTBOX
        conversation.date = System.currentTimeMillis().toString()
        conversation.thread_id = threadId.toString()
        conversation.status = Telephony.Sms.STATUS_PENDING

        databaseConnector.threadedConversationsDao()
                .insertThreadAndConversation(applicationContext, conversation)
        SMSDatabaseWrapper.send_text(applicationContext, conversation, bundle)
        Log.d(javaClass.name, "SMS sent...")
    }
    private fun getDeliverCallback(channel: Channel, subscriptionId: Int,
                                   rmqConnectionId: Long): DeliverCallback {
        return DeliverCallback { consumerTag: String, delivery: Delivery ->
            val message = String(delivery.body, StandardCharsets.UTF_8)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val smsRequest = Json.decodeFromString<SMSRequest>(message)
                    sendSMS(smsRequest,
                            subscriptionId,
                            consumerTag,
                            delivery.envelope.deliveryTag,
                            rmqConnectionId)
                } catch (e: Exception) {
                    Log.e(javaClass.name, "", e)
                    when(e) {
                        is SerializationException -> {
                            channel.let {
                                if (it.isOpen)
                                    it.basicReject(delivery.envelope.deliveryTag, false)
                            }
                        }
                        is IllegalArgumentException -> {
                            channel.let {
                                if (it.isOpen)
                                    it.basicReject(delivery.envelope.deliveryTag, true)
                            }
                        }
                        else -> {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }


    private val rmqConnectionList = ArrayList<RMQConnection>()
    private fun startConnection(factory: ConnectionFactory, gatewayClient: GatewayClient) {
        Log.d(javaClass.name, "Starting new connection...")

        // TODO: use this without internet connection and catch that error and best handle
        gatewayClient.state = GatewayClient.STATE_INITIALIZING
        try {
            val connection = factory.newConnection(ThreadingPoolExecutor.executorService,
                    gatewayClient.friendlyConnectionName)
            gatewayClient.state = GatewayClient.STATE_CONNECTED
            databaseConnector.gatewayClientDAO().update(gatewayClient)

            val rmqConnection = RMQConnection(gatewayClient.id, connection)
            rmqConnectionList.add(rmqConnection)

            connection.addShutdownListener {
                /**
                 * The logic here, if the user has not deactivated this - which can be known
                 * from the database connection state then reconnect this client
                 */
                Log.e(javaClass.name, "Connection shutdown cause: $it")
                val gatewayClient1 = databaseConnector.gatewayClientDAO().fetch(gatewayClient.id)
                if(gatewayClient1.activated) {
                    rmqConnectionList.remove(rmqConnection)
                    gatewayClient1.state = GatewayClient.STATE_RECONNECTING
                    databaseConnector.gatewayClientDAO().update(gatewayClient1)
                    GatewayClientHandler.startListening(applicationContext, gatewayClient)
                }
            }

            val gatewayClientProjectsList = databaseConnector.gatewayClientProjectDao()
                    .fetchGatewayClientIdList(gatewayClient.id)

            for (i in gatewayClientProjectsList.indices) {
                for (j in subscriptionInfoList.indices) {
                    val channel = rmqConnection.createChannel()
                    val gatewayClientProjects = gatewayClientProjectsList[i]
                    val subscriptionId = subscriptionInfoList[j].subscriptionId
                    val bindingName = if (j > 0)
                        gatewayClientProjects.binding2Name else gatewayClientProjects.binding1Name

                    Log.d(javaClass.name, "Starting channel for sim slot $j in project #$i")
                    startChannelConsumption(rmqConnection, channel, subscriptionId,
                            gatewayClientProjects, bindingName)
                }
            }

        } catch (e: Exception) {
            when(e) {
                is TimeoutException -> {
                    e.printStackTrace()
                    Thread.sleep(3000)
                    Log.d(javaClass.name, "Attempting a reconnect to the server...")
                    startConnection(factory, gatewayClient)
                }
                is IOException -> {
                    Log.e(javaClass.name, "IO Exception connecting rmq", e)
                }
                else -> {
                    Log.e(javaClass.name, "Exception connecting rmq", e)
                }
            }
        }
    }

    private fun startChannelConsumption(rmqConnection: RMQConnection,
                                channel: Channel,
                                subscriptionId: Int,
                                gatewayClientProjects: GatewayClientProjects,
                                bindingName: String) {
        Log.d(javaClass.name, "Starting channel connection")
        channel.basicRecover(true)
        val deliverCallback = getDeliverCallback(channel, subscriptionId, rmqConnection.id)
        val queueName = rmqConnection.createQueue(gatewayClientProjects.name, bindingName, channel)
        val messagesCount = channel.messageCount(queueName)

        val consumerTag = channel.basicConsume(queueName, false, deliverCallback,
                object : ConsumerShutdownSignalCallback {
                    override fun handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {
                        if (rmqConnection.connection.isOpen) {
                            Log.e(javaClass.name, "Consumer error", sig)
                            startChannelConsumption(rmqConnection,
                                    rmqConnection.createChannel(),
                                    subscriptionId,
                                    gatewayClientProjects,
                                    bindingName)
                        }
                    }
                })
        Log.d(javaClass.name, "Created Queue: $queueName ($messagesCount) - tag: $consumerTag")
        rmqConnection.bindChannelToTag(channel, consumerTag)
    }

    private fun connectGatewayClient(gatewayClient: GatewayClient) {
        Log.d(javaClass.name, "Starting new service connection...")

        val factory = ConnectionFactory()
        factory.username = gatewayClient.username
        factory.password = gatewayClient.password
        factory.virtualHost = gatewayClient.virtualHost
        factory.host = gatewayClient.hostUrl
        factory.port = gatewayClient.port
        factory.isAutomaticRecoveryEnabled = true
        factory.exceptionHandler = DefaultExceptionHandler()

        factory.setRecoveryDelayHandler {
            Log.w(javaClass.name, "Factory recovering...: $it")
            10000
        }

        startConnection(factory, gatewayClient)
    }


    private fun stop(gatewayClientId: Long) {
        val iterator = rmqConnectionList.iterator()
        while(iterator.hasNext()) {
            val gatewayClient = iterator.next()
            if(gatewayClient.id == gatewayClientId) {
                rmqConnectionList.remove(gatewayClient)
                break
            }
        }

    }

    private fun createForegroundNotification() {
        val notificationIntent = Intent(applicationContext, GatewayClientListingActivity::class.java)
        val pendingIntent = PendingIntent
                .getActivity(applicationContext,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE)

//        databaseConnector.gatewayClientDAO().fetch()
//
//        var nConnected = 0
//        var nReconnecting = 0
//        gatewayClients.forEach {
//            if(it.activated) {
//                when(it.state) {
//                    GatewayClient.STATE_CONNECTED -> nConnected++
//                    GatewayClient.STATE_RECONNECTING -> nReconnecting++
//                }
//                if (nConnected < 1 && nReconnecting < 1) {
//                    stopForeground(STOP_FOREGROUND_REMOVE)
//                    stopSelf()
//                }
//            }
//        }

//        val description = "$nConnected ${getString(R.string.gateway_client_running_description)}\n" +
//                "$nReconnecting ${getString(R.string.gateway_client_reconnecting_description)}"
        val description = applicationContext.getString(R.string.gateway_client_running_description)

        val notification =
                NotificationCompat.Builder(applicationContext,
                        applicationContext.getString(R.string.running_gateway_clients_channel_id))
                        .setContentTitle(applicationContext
                                .getString(R.string.gateway_client_running_title))
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setPriority(NotificationCompat.DEFAULT_ALL)
                        .setSilent(true)
                        .setOngoing(true)
                        .setContentText(description)
                        .setContentIntent(pendingIntent)
                        .build()

        val NOTIFICATION_ID = 1234
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, notification)
    }
}
