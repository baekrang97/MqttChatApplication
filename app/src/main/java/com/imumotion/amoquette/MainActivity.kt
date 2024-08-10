/*
 * Copyright (c) 2021 Rene F. van Ee
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0,
 * which accompanies this distribution.
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.imumotion.amoquette

import android.content.Intent
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.imumotion.amoquette.broker.Action
import com.imumotion.amoquette.broker.BrokerService
import com.imumotion.amoquette.client.MqttClient
import com.imumotion.amoquette.client.MqttClientState
import com.imumotion.amoquette.clientservice.ClientMainActivity
import kotlinx.coroutines.*
import java.util.*

enum class ServiceAction {
    UNDEFINED,
    STOPPED,
    STARTED,
    PROBING,
    STARTING,
    STOPPING
}

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {//CoroutineScope는 코루틴을 실행하고 관리하기 위한 범위를 정의하는 인터페이스 비동기 작업 효율적 처리를 위해 사용
    val LOGTAG = this.javaClass.name//클래스의 이름을 가져옴
    private val gson = Gson()//gson 라이브러리 객체 -> json 형식의 데이터를 객체로 변환

    private var backPressedTime = 0L//long 형식 변수 선언

    private var serviceAction = ServiceAction.UNDEFINED//service action 변수 아직 정의 되지 않은 것으로 선언
    private var client: MqttClient? = null//mqttclient타입 변수



    internal fun updateStateMachine() {//내부 클래스 모듈 내에서만 활용
        //mqtt클라이언트의 상태를 확인하고 그에 따른 동작 구현
        fun logStateError(clientState: MqttClientState) {//오류상태 로그를 기록하는 함수
            Log.e(LOGTAG, "updateStateMachine: Illegal combination: " +
                    "serviceAction=${serviceAction.name}, " +
                    "client state=${clientState.name}")
        }

        val clientstate = client?.getState() ?: MqttClientState.UNDEFINED//null일경우 값을 UNDEFINED로 설정-> 처음에는 undefined값
        when(serviceAction) {
            //멈춘 경우
            ServiceAction.STOPPED -> {
                if(clientstate != MqttClientState.DISCONNECTED) {
                    logStateError(clientstate)
                }
            }
            //시작된 경우
            ServiceAction.STARTED -> {
                when(clientstate) {
                    MqttClientState.CONNECTED -> { /* Do nothing, totally OK */ }
                    MqttClientState.DISCONNECTED -> {
                        // Do nothing, will happen when switching to Settings Activity
                    }
                    else -> logStateError(clientstate)
                }
            }
            //probing 중인 경우
            ServiceAction.PROBING -> {//시작되고 아직 브로커가 실행되지 않았을떄
                when(clientstate) {
                    MqttClientState.CREATED -> {//클라이언트가 생성된 경우
                        connectClient()  // Try to connect
                    }
                    MqttClientState.CONNECTING -> showServiceTransition()//연결중인 경우
                    MqttClientState.CONNECTFAILED -> {//연결 실패의 경우
                        showServiceStopped()//밑에서 구현된 함수실행
                        disconnectClient()  //상태를 disconnected로 바꾸기위한 ->밑에 함수로 구현
                        serviceAction = ServiceAction.STOPPED//service action 상태 멈춤으로 변경
                    }
                    MqttClientState.CONNECTED -> {//연결된 경우
                        showServiceStarted()//연결된 경우 화면 표시
                        serviceAction = ServiceAction.STARTED//action상태 STARTED로 변경
                    }
                    else -> {//정의되지 않은 상태  즉 오류
                        logStateError(clientstate)//그외의 경우
                    }
                }
            }
            ServiceAction.STARTING -> {//시작 버튼 누를떄
                when(clientstate) {
                    MqttClientState.DISCONNECTED -> {//연결된 클라이언트가 없을때
                        startService()
                        connectClient()  // Try to connect
                    }
                    MqttClientState.CONNECTING -> showServiceTransition()
                    MqttClientState.CONNECTFAILED -> {
                        showServiceTransition()
                        connectClient()  // Try again, we expect to have the service up and
                                         // running soon
                    }
                    MqttClientState.CONNECTED -> {
                        showServiceStarted()
                        serviceAction = ServiceAction.STARTED
                    }
                    else -> {
                        logStateError(clientstate)
                    }
                }
            }
            ServiceAction.STOPPING -> {
                when(clientstate) {
                    MqttClientState.CONNECTED -> {
                        showServiceTransition()
                        stopService()
                    }
                    MqttClientState.CONNECTIONLOST -> {
                        showServiceStopped()
                        disconnectClient()  // In order to set its state to DISCONNECTED
                        serviceAction = ServiceAction.STOPPED
                    }
                    else -> {
                        logStateError(clientstate)
                    }
                }
            }
            else -> {
                logStateError(clientstate)
            }
        }
    }

    // External entry point (from MqttClient)
    // Receives key/value pairs from the Mqtt client, to be used for updating UI etc.
    fun processMap(map: Map<String, Map<String, Any>>) {//해당 맵의 데이터를 UI에 표시하는 함수
        launch {//백그라운드에서 비동기적으로 작업 수행
            map.forEach { topicmap ->
                topicmap.value.forEach {//topicmap의 value값을 출력
                    // Create id name by concatenating topic (stripped of $, with
                    // forward slashes / replaced by underscore _ and converted to lowercase)
                    // and the key from the topicmap:
                    val idName = "${topicmap.key}_${it.key}".lowercase().
                                    replace("/", "_").replace("\$", "")

                    val id = resources.getIdentifier(idName, "  id", packageName)//리소스의 id검색 후 id 반환한다.
                    if (id != 0) {
                        val strvalue = if (it.value is Double) String.format("%.2f", it.value)
                                       else it.value.toString()
                        findViewById<TextView>(id).text = strvalue
                    }
                }
            }
        }
    }

    // Actions
    private fun startService() {
        // Retrieve preferences/settings
        val brokerproperties = PreferenceManager.getDefaultSharedPreferences(this).all
        // Start service
        Intent(applicationContext, BrokerService::class.java).also {//서비스: 백그라운드에서 실행되는 서비스 -> 여기서 brokerservice를 호출한다.
            // also는 생성된 객체에 추가하고 싶을떄 사용
            it.action = Action.START.name//brokerservice.kt의 enum class로 Action 정의
            it.putExtra(CONST_JSONSTRINGDATA, gson.toJson(brokerproperties).toString())//putExtra함수를 이용하여 키-값 쌍 형태로 데이터 추가한다.CONST_JSONSTRINGDATA은 constants.kt에 저장되어 있음
            //json형식으로 property 전달 brokerservice에
            startForegroundService(it)//벡그라운드의 제한을 받지않게 foreground로 실행한다.
        }
    }
    private fun stopService() {
        // Stop the broker
        Intent(applicationContext, BrokerService::class.java).also {
            it.action = Action.STOP.name
            startForegroundService(it)
        }
    }
    private fun connectClient() {
        val connect = client?.connect("", "")
    }
    private fun disconnectClient() {
        client?.disconnect()
    }

    // Activity life cycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serviceAction = ServiceAction.UNDEFINED

        title = "AMoQueTTe Broker"

        // If no settings available, start the Settings activity. This will set default
        // values and give the user the opportunity to review and change them.
        val preferences = PreferenceManager.getDefaultSharedPreferences(this).all
        if (preferences.isEmpty())
        {
            // Start settings activity
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        val clientButton = findViewById<Button>(R.id.start_client_button)
        clientButton.setOnClickListener {
            startActivity(Intent(this, ClientMainActivity::class.java))
        }
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setOnClickListener {
            when(serviceAction) {
                ServiceAction.STARTED -> {
                    serviceAction = ServiceAction.STOPPING
                    updateStateMachine()
                }
                ServiceAction.STOPPED -> {
                    serviceAction = ServiceAction.STARTING
                    updateStateMachine()
                }
                else -> {
                    // Do nothing
                }
            }
        }
    }

    override fun onStop() {
        if(client?.isConnected() == true) {
            client!!.disconnect()
        }
        super.onStop()
    }

    override fun onStart() {
        super.onStart()

        val properties = Properties()
        PreferenceManager.getDefaultSharedPreferences(this).all.forEach {
            properties.set(it.key, it.value?.toString())
        }
        val host = when(properties["host"]) {
            "0.0.0.0" -> "localhost"
            else -> properties["host"]
        }
        val connectionString = "tcp://$host:${properties["port"]}"

        client = object: MqttClient(
            this, MQTTCLIENT_ID, properties, connectionString) {
            override fun onConnectionStatusChanged() {//mqtt 클라이언트의 연결상태가 변경될떄 호출
                updateStateMachine()
            }
        }

        // Probe current service status by trying to connect once.
        // If it succeeds, the service is active, otherwise it is not.
        serviceAction = ServiceAction.PROBING
        updateStateMachine()
    }

    // Status updates on the GUI
    private fun showServiceTransition() {
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setImageResource(R.drawable.ic_powerbutton_transition)
        findViewById<TextView>(R.id.connStringTextView).text = "N/A"
    }
    private fun showServiceStopped() {
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setImageResource(R.drawable.ic_powerbutton_off)
        findViewById<TextView>(R.id.connStringTextView).text = "N/A"
    }
    private fun showServiceStarted() {
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setImageResource(R.drawable.ic_powerbutton_on)
        launch {
            displayBrokerConnectionString()
        }
    }
    internal suspend fun displayBrokerConnectionString() {
        withContext(Dispatchers.IO) {
            val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ipULong = wifiMgr.connectionInfo.ipAddress.toULong()
            val ipbytes = ShortArray(4) {i -> ((ipULong shr (i shl 3)) and 255u).toShort()}
            val ipstr = ipbytes.joinToString(separator = ".")

            val brokerProperties =
                PreferenceManager.getDefaultSharedPreferences(applicationContext).all
            val port = brokerProperties["port"]
            val connectionstring = "tcp://$ipstr:$port"

            val connStringTextView: TextView = findViewById(R.id.connStringTextView)
            connStringTextView.text = connectionstring
        }
    }

    // Options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Kill application after two consecutive back presses. Note that this does not influence
    // the service state!
    override fun onBackPressed() {
        // BackPressed twice within 2 seconds?
        if (backPressedTime + 2000L > System.currentTimeMillis()) {
            super.onBackPressed()
            finishAffinity()
        } else {
            Toast.makeText(baseContext, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}