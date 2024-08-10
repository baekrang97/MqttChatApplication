package com.imumotion.amoquette.clientservice

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.imumotion.amoquette.R

class LookupActivity : AppCompatActivity() {
    private lateinit var dbHelper: DbHelper
    private lateinit var editTextUserId: EditText
    private lateinit var listViewLookupResults: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lookup_activity)

        // DbHelperChat 인스턴스 생성
        dbHelper = DbHelper(this)

        // UI 요소 초기화
        editTextUserId = findViewById(R.id.editTextUserId)
        listViewLookupResults = findViewById(R.id.listViewLookupResults)

        // 조회 버튼 클릭 이벤트 처리
        val buttonLookup: Button = findViewById(R.id.buttonLookup)
        buttonLookup.setOnClickListener {
            val userId = editTextUserId.text.toString().trim()
            // 사용자가 입력한 ID를 기반으로 대화 내용을 조회
            val chatMessages = dbHelper.getAllChatMessages(userId)
            // 조회 결과를 ListView에 표시
            val adapter = ChatAdapter(this, R.layout.chat_listview, chatMessages.toMutableList())
            listViewLookupResults.adapter = adapter
        }
    }
}
