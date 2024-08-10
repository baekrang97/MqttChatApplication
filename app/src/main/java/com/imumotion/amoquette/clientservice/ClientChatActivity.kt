package com.imumotion.amoquette.clientservice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.imumotion.amoquette.R
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.nio.charset.Charset


class ClientChatActivity : AppCompatActivity() {
    companion object {
        val LOG_TAG = ClientChatActivity::class.java.simpleName
    }

    lateinit var DbHelper: DbHelper
    val gson = Gson()
    var topic2 = "chat"
    var server_uri: String = "192.168.128.142"
    var server_port: String = "1883"
    var server: String = "tcp://$server_uri:$server_port"
    var client: MqttAndroidClient? = null
    var qos = 0

    lateinit var editTextClient: EditText
    lateinit var listView: ListView
    lateinit var sendButton: Button
    lateinit var offButton: Button
    lateinit var userinfo: TextView
    lateinit var offButtonForce: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.client_activity)
        val id: String = intent.getStringExtra("userid")!!
        val username: String = intent.getStringExtra("username")!!

        DbHelper = DbHelper(this)
        client = MqttAndroidClient(this, server, MqttClient.generateClientId())

        editTextClient = findViewById(R.id.client)
        listView = findViewById(R.id.sub)

        val items = mutableListOf<Message>(
        )
        val adapter = ChatAdapter(this, R.layout.chat_listview, items)
        listView.adapter = adapter
        sendButton = findViewById(R.id.button_message)//메세지 전송버튼
        offButton = findViewById(R.id.button_off)//대화종료버튼
        offButtonForce = findViewById(R.id.button_off_force)
        userinfo = findViewById(R.id.userinfo)
        userinfo.text = "채팅 상대: " + username

        connectToMqttBroker()
        sendButton.setOnClickListener {
            var text: String = editTextClient.text.toString().trim()//edittext 내용 가져온다
            if (text.length != 0) {
                try{
                val chatmessage = ChatText(Chat(CHAT_TEXT,"server",id,System.currentTimeMillis()),text)
                val json = gson.toJson(chatmessage)
                sendMessage(topic2,json)
                //edittextview 초기화
                editTextClient.setText("")}
                catch (e:Exception)
                {
                    Log.d(LOG_TAG,"에러메세지 보낼때 $e")
                }
            }
        }
        offButton.setOnClickListener {
            // ClientMainActivity로 돌아가기 위한 Intent 생성
            val intent = Intent(this@ClientChatActivity, ClientMainActivity::class.java)
            intent.putExtra("userid", id)
            setResult(RESULT_OK,intent)
            disconnect(topic2)
            // 현재의 ClientChatActivity 종료
            if(!isFinishing)
                finish()
        }
        offButtonForce.setOnClickListener {

        }

    }

    // 브로커에 접속한다.
    // TOPIC에 SUBSCRIBE까지 처리
    // 여기까지 성공하면 isConnected, isSubscribed가 true

    fun connectToMqttBroker() {
        try {
            client?.let {
                it.connect(MqttConnectOptions())?.let {
                    it.actionCallback = listenerConnect
                }
                it.setCallback(clientCallback)
            }
        } catch (e: MqttException) {
            Log.e(LOG_TAG, "ERROR CONNECTING TO BROKER", e)
            isConnected = false;
        }
        subscribe(topic2)
    }

    // TOPIC에 SUBSCRIBE...
    fun subscribe(topic:String) {
        try {
            if (isConnected) {
                client?.let {
                    it.subscribe(topic, qos)?.let {
                        it.actionCallback = listenerSubscribe
                    }
                }
            }
        } catch (e: MqttException) {
            Log.d(LOG_TAG, "ERROR SUBSCRIBE TO BROKER", e)
        }
    }

    //   구독한 TOPIC을 해지
    //   CONNTION 끊고

    fun disconnect(topic2:String) {
        if (isTopicSubcribed) {
            // unsubscribe
            try {
                client?.let {
                    it.unsubscribe(topic2).let {
                        it.actionCallback = listenerUnsubscribe
                    }
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
        if (isConnected) {
            // disconnect
            try {
                client?.let {
                    it.disconnect().let {
                        it.actionCallback = listenerDisconnect
                    }
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    // clinet를 닫는다.
    //   구독한 TOPIC을 해지
    //   CONNTION 끊고
    //   Client.close()
    //
    // 앱이 종료할 때는 close()를 호출하여 정리하도록 한다.

    private fun sendMessage(topic: String, text: String) {
        // publish()할 메시지를 만든다.
        val message = MqttMessage()
        message.payload = text.toByteArray(Charset.defaultCharset())

        // 브로커에 연결되어 있다면...
        if (isConnected) {
            client?.let {
                try {
                    it.publish(topic, message)?.let {
                        it.actionCallback = listenerPublish
                    }
                } catch (e: Exception) {
                    Log.d(LOG_TAG, " Failure .. ", e)
                }
            }
            if (isTopicSubcribed) client?.setCallback(clientCallback)
        }
    }

    var isConnected: Boolean = false
    var isTopicSubcribed: Boolean = false

    val listenerConnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d(LOG_TAG, "CONNECTED TO BROKER")
            isConnected = true;
            subscribe(topic2)
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.d(LOG_TAG, "CONNECTION TO BROKER FAILED")
            isConnected = false;
        }
    }

    val listenerSubscribe = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            isTopicSubcribed = true
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            isTopicSubcribed = false
        }
    }

    val listenerPublish = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d(LOG_TAG, "PUBLISHED")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        }
    }

    val listenerUnsubscribe = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            isTopicSubcribed = false
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            isTopicSubcribed = true
        }
    }

    val listenerDisconnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d(LOG_TAG, "DISCONNECTED")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        }
    }

    var clientCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            Log.d("Callback..", "connectionLost")
            isTopicSubcribed = false
            isConnected = false
        }


        override fun messageArrived(topic: String?, message: MqttMessage?) {
            if (topic == topic2) { // 채팅 관련 메시지 처리
                val json = message?.toString()
                Log.d(LOG_TAG, "$json")
                val jsonObject = gson.fromJson(json, JsonObject::class.java)
                val type = jsonObject.get("chat").asJsonObject.get("type").asInt
                Log.d(ClientMainActivity.LOG_TAG, "$type")
                when (type) {
                    CHAT_EXIT_AGREE -> {//이용자가 보내주는 type
                        // 채팅 종료 동의 처리
                        val chatExitAgreeMessage = gson.fromJson(json, ChatExitAgree::class.java)
                        Log.d(LOG_TAG, "Received chat exit agree message: $chatExitAgreeMessage")
                        // 채팅 종료 동의 메시지 처리
                        //채팅방에서 처리
                    }
                    CHAT_EXIT_DISAGREE -> {//이용자가 보내주는 type
                        // 채팅 종료 거부 처리
                        val chatExitDisagreeMessage = gson.fromJson(json, ChatExitDisagree::class.java)
                        Log.d(LOG_TAG, "Received chat exit disagree message: $chatExitDisagreeMessage")
                        // 채팅 종료 거부 메시지 처리
                        //채팅방에서 처리
                    }
                    CHAT_TEXT -> {
                        // 채팅 텍스트 메시지 처리
                        Log.d(LOG_TAG, "enter chattext")
                        val chatTextMessage = gson.fromJson(json, ChatText::class.java)

                        Log.d(LOG_TAG, "Received chat text message: $chatTextMessage")

                        // ListView에 추가할 새로운 메시지를 생성합니다.
                        val newMessage =
                             Message(chatTextMessage.chat.sender, chatTextMessage.chat.receiver,chatTextMessage.chat.time,chatTextMessage.text)
                        DbHelper.addChatMessage(newMessage)
                        Log.d("newMessage","$newMessage")
                        // CustomAdapter에 새로운 메시지를 추가합니다.
                        if (newMessage != null) {
                            (listView.adapter as? ChatAdapter)?.addMessage(newMessage)
                        }
                        // ListView를 업데이트합니다.
                        (listView.adapter as? ChatAdapter)?.notifyDataSetChanged()
                    }
                    CHAT_EXIT_PERMISSION -> {//no action
                        // 채팅 종료 허가 처리
                        val chatExitPermissionMessage = gson.fromJson(json, Chat::class.java)
                        Log.d(LOG_TAG, "Received chat exit permission message: $chatExitPermissionMessage")
                        // 채팅 종료 허가 메시지 처리
                    }
                    CHAT_EXIT_FORCE -> {//no action
                        // 채팅 종료 강제 처리
                        val chatExitForceMessage = gson.fromJson(json, Chat::class.java)
                        Log.d(LOG_TAG, "Received chat exit force message: $chatExitForceMessage")
                        // 채팅 종료 강제 메시지 처리
                    }
                }
            }
        }
        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            Log.d("Callback..", "deliveryComplete")
        }
    }
}
