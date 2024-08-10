package com.imumotion.amoquette.clientservice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ClientMainActivity : AppCompatActivity() {
    companion object {
        val LOG_TAG = ClientMainActivity::class.java.simpleName
    }


    val gson = Gson()//json 라이브러리 변수
    private lateinit var dbHelper: DbHelper//db 관리 변수
    var topic1 = "wait"//wait topic
    var topic2 = "chat"//chat topic
    var server_uri: String = "192.168.128.142"//브로커 ip주소
    var server_port: String = "1883"//포트
    var server: String = "tcp://$server_uri:$server_port"
    var client: MqttAndroidClient? = null
    var qos = 0//quality of service 0~2
    var waitnum = 1//대기번호 -> 새로 들어올때마다 ++
    var curwaitnum = 1//현재 상담중인 사람의 waitnum

    lateinit var listView: ListView
    lateinit var waitview: TextView
    lateinit var lookupButton: Button

    val userListForAdapter = mutableListOf<User>()//사용자 관리 리스트
    private lateinit var adapter: UserAdapter//화면에 보여주기 위한 어뎁터 -> 리스트뷰로 보여주는 것 관리

    private val userList: MutableList<User> = mutableListOf()//객체화
    // 사용자 추가 메서드
    fun addUser(user: User?) {
        if (user != null) {
            userList.add(user)}
    }

    // 사용자 삭제 메서드
    fun removeUser(user: User) {
        userList.remove(user)
    }

    // 사용자 목록 반환 메서드
    fun getUsers(): List<User> {
        return userList
    }

    // 사용자 목록 초기화 메서드
    fun clearUsers() {
        userList.clear()
    }
    fun updateWaitView() {//대기인원 최신화 함수
        val count = userList.count { it.state == 0 }
        waitview.text = "대기 인원: $count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.client_main_activity)
        dbHelper = DbHelper(this)
        val usersFromDB = dbHelper.getAllUsers()//db로 부터 상담후의 사용자들을 가져온다 -> 여기서 하지 않고 조회 페이지(Lookupactivity에 저장해 놓고)로 넘기면 된다
        for (user in usersFromDB) {//이용자 추가
            userList.add(user)
        }
        for (user in userList) {
            userListForAdapter.add(user)//이용자 보여주기
        }
        val count = userList.count { it.state == 0 }//상담 전인 상태의 이용자들만 추가한다. -> 이것도 lookup으로 넘어가서 구현

        client = MqttAndroidClient(this, server, MqttClient.generateClientId())//client 생성
        waitview = findViewById(R.id.waiting_count_text)
        listView = findViewById(R.id.sub)
        adapter = UserAdapter(this, R.layout.user_listview, userListForAdapter)
        waitview.text = "대기 인원: $count"
        listView.adapter = adapter//useradapter 장착
        lookupButton = findViewById(R.id.lookupbutton)

        lookupButton.setOnClickListener {//조회 화면
            // LookupActivity를 실행합니다.
            val intent = Intent(this, LookupActivity::class.java)
            startActivity(intent)
        }
        listView.setOnItemClickListener { parent, view, position, id ->
            // 클릭한 아이템을 가져옵니다.
            val selectedItem = parent.getItemAtPosition(position) as User

            // userList에서 클릭한 사용자를 찾습니다.
            val clickedUser = userList.find { it.id == selectedItem.id }

            // 사용자를 찾았을 경우 상태를 변경합니다.
            if(clickedUser?.state == 0) {//상담전 상태에서 상담중 상태로 바꾼다.(즉 첫 클릭)
                clickedUser?.state = 1 // 예시로 상태를 1로 변경하였습니다. 필요에 따라 다른 값으로 변경할 수 있습니다.

                // 변경된 상태를 리스트뷰에 반영합니다.
                val waitEnterchat = WaitEnterChat(
                    Wait(
                        WAIT_ENTER_CHAT,
                        "server",
                        clickedUser!!.id,
                        System.currentTimeMillis()
                    ), waitnum
                )
                curwaitnum = clickedUser.waitnum
                val jsonWaitData = gson.toJson(waitEnterchat)
                sendMessage(topic1, jsonWaitData)
            }
            adapter.notifyDataSetChanged()
            // ClientChatActivity를 시작합니다.
            val intent = Intent(this, ClientChatActivity::class.java)
            intent.putExtra("userid", selectedItem.id)
            intent.putExtra("username", selectedItem.username)// 클릭한 사용자 정보를 전달합니다.
            chatLauncher.launch(intent)
        }
        updateWaitView()

        connectToMqttBroker()
    }
    //activity에서 data를 받아오기 위해 startactivityforresult와 동일
    private val chatLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(LOG_TAG, "진행됨")
            // 활동이 성공적으로 종료되었을 때의 처리
            val userIdToUpdate = result.data?.getStringExtra("userid")
            userIdToUpdate?.let { id ->
                val userToUpdate = userList.find { it.id == id }
                userToUpdate?.let {
                    it.state = 2 // 해당 ID를 갖는 사용자의 상태를 2로 변경
                    adapter.notifyDataSetChanged()
                    updateWaitView()
                }
            }
        } else {
            Log.d(LOG_TAG, "resultCode가 RESULT_OK가 아닙니다.")
        }
    }//받아 올때 clientchatactivity가 ondestroy가 실행되면 정상적으로 작동 안됨

    // 브로커에 접속한다.
    // TOPIC에 SUBSCRIBE까지 처리
    // 여기까지 성공하면 isConnected, isSubscribed가 true
    override fun onDestroy() {//activity 종료시
        super.onDestroy()
        disconnect(topic1,topic2)
        dbHelper.close()
    }
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
        subscribe(topic1)
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

    fun disconnect(topic1:String,topic2:String) {
        if (isTopicSubcribed) {
            // unsubscribe
            try {
                client?.let {
                    it.unsubscribe(topic1).let {
                        it.actionCallback = listenerUnsubscribe
                    }
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
            subscribe(topic1)
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
            if (topic == topic1) { // 대기 관련 메시지 처리
                val json = message?.toString()
                Log.d(LOG_TAG, "$json")
                val jsonObject = gson.fromJson(json, JsonObject::class.java)
                val type = jsonObject.get("wait").asJsonObject.get("type").asInt
                when (type) {
                    WAIT_ENTER -> {
                        // 대기 진입 처리
                        Log.d(LOG_TAG, "Received wait enter message:")
                        val waitMessage = gson.fromJson(json, WaitEnter::class.java)
                        Log.d(LOG_TAG, "${waitMessage.wait}")
                        var newUser: User = User(
                            waitMessage.username,
                            waitMessage.wait.sender,
                            waitMessage.wait.time,
                            waitnum,
                            0
                        )
                        // 새로운 사용자 추가
                        addUser(newUser)
                        // 다음 대기번호 증가
                        waitnum++
                        Log.d(LOG_TAG, "User added to the list: $newUser")
                        userListForAdapter.add(newUser!!)

                        // 대기 인원 업데이트
                        updateWaitView()

                        // 리스트뷰 어댑터에 변경 알리기
                        adapter.notifyDataSetChanged()
                        // 대기 진입 체크 메시지 생성 및 전송
                        val waitEnterCheck = WaitEnterCheck(
                            Wait(
                            WAIT_ENTER_CHECK,
                            "server",
                            newUser?.id,
                            System.currentTimeMillis()),
                            newUser?.waitnum!!,
                            curwaitnum
                        )
                        val jsonWaitData = gson.toJson(waitEnterCheck)
                        sendMessage(topic1, jsonWaitData)
                        dbHelper.addUser(newUser)
                    }

                    WAIT_EXIT -> {
                        // 대기 나가기 처리
                        val waitMessage = gson.fromJson(json, WaitExit::class.java)
                        Log.d(LOG_TAG, "Received wait exit message: $waitMessage")
                        // 대기 나가기 메시지 처리
                        val userToRemove: User = userList.find { it.id == waitMessage.wait.sender }!!
                        removeUser(userToRemove)
                        Log.d(LOG_TAG, "User removed from the list: $userToRemove")
                        updateWaitView()
                        dbHelper.removeUser(userToRemove.id)
                    }

                    WAIT_ENTER_CHECK -> {//no action 사용자 발행 메세지
                        // 대기 확인 처리
                        val waitEnterCheckMessage =
                            gson.fromJson(json, WaitEnterCheck::class.java)
                        Log.d(
                            LOG_TAG,
                            "Received wait enter check message: $waitEnterCheckMessage"
                        )
                        // 대기 확인 메시지 처리
                    }

                    WAIT_ENTER_CHAT -> {//no action  사용자 발행 메세지
                        // 대기 채팅 처리
                        val waitEnterChatMessage =
                            gson.fromJson(json, WaitEnterChat::class.java)
                        Log.d(
                            LOG_TAG,
                            "Received wait enter chat message: $waitEnterChatMessage"
                        )
                        // 대기 채팅 메시지 처리
                    }
                }
            } else if (topic == topic2) {
                val json = message?.toString()
                Log.d(LOG_TAG, "$json")
                val jsonObject = gson.fromJson(json, JsonObject::class.java)
                val type = jsonObject.get("type").asInt// 채팅 관련 메시지 처리
                when (type) {
                    CHAT_ENTER -> {
                        // 채팅 입장 처리
                        val chatEnterMessage = gson.fromJson(json, ChatEnter::class.java)
                        Log.d(LOG_TAG, "Received chat enter message: $chatEnterMessage")
                        // 채팅 입장 메시지 처리
                        //이후에 채팅방을 띄운다.
                    }
                }
            }
        }

        // topic의 전달이 끝났을 때....
        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            Log.d("Callback..", "deliveryComplete")
        }
    }
}
fun convertLongToTime(time: Long?): String {//시간 치환 함수
    val date = Date(time!!)
    val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return format.format(date)
}
