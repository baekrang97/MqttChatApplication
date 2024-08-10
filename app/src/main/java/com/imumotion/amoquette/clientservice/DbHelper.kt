package com.imumotion.amoquette.clientservice

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "ChatDatabase.db"//dbhelper를 두개 만들면 같은 db파일에 넣을수 없음
        private const val TABLE_NAME_CHAT = "Chat"
        private const val TABLE_NAME_CHAT_ROOM = "ChatRoom"
        private const val COLUMN_SENDER = "sender"
        private const val COLUMN_RECEIVER = "receiver"
        private const val COLUMN_TIME = "time"
        private const val COLUMN_TEXT = "text"
        private const val COLUMN_USER_ID = "userid"
        private const val COLUMN_USERNAME = "username"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TABLE_CHAT_QUERY = ("CREATE TABLE $TABLE_NAME_CHAT ($COLUMN_SENDER TEXT, $COLUMN_RECEIVER TEXT, $COLUMN_TIME BIGINT, $COLUMN_TEXT TEXT)")
        db.execSQL(CREATE_TABLE_CHAT_QUERY)
        Log.d("$TABLE_NAME_CHAT", "생성됨")

        val CREATE_TABLE_CHAT_ROOM_QUERY = ("CREATE TABLE $TABLE_NAME_CHAT_ROOM ($COLUMN_USER_ID TEXT PRIMARY KEY, $COLUMN_USERNAME TEXT)")
        db.execSQL(CREATE_TABLE_CHAT_ROOM_QUERY)
        Log.d("$TABLE_NAME_CHAT_ROOM", "생성됨")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME_CHAT")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME_CHAT_ROOM")
        onCreate(db)
    }

    fun addChatMessage(message: Message) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SENDER, message.sender)
            put(COLUMN_RECEIVER, message.receiver)
            put(COLUMN_TIME, message.time)
            put(COLUMN_TEXT, message.text)
        }
        db.insert(TABLE_NAME_CHAT, null, values)
        Log.d("dbtable$TABLE_NAME_CHAT", "성공적으로 들어감$values")
        db.close()
    }

    fun getAllChatMessages(userId: String): List<Message> {
        val chatMessageList = mutableListOf<Message>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME_CHAT WHERE $COLUMN_SENDER = ? OR $COLUMN_RECEIVER = ? ORDER BY $COLUMN_TIME", arrayOf(userId, userId))


        if (cursor.moveToFirst()) {
            do {
                val sender = cursor.getString(cursor.getColumnIndex(COLUMN_SENDER))
                val receiver = cursor.getString(cursor.getColumnIndex(COLUMN_RECEIVER))
                val time = cursor.getLong(cursor.getColumnIndex(COLUMN_TIME))
                val text = cursor.getString(cursor.getColumnIndex(COLUMN_TEXT))
                chatMessageList.add(Message(sender, receiver, time, text))
                Log.d("cursor","${Message(sender, receiver, time, text)}")
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return chatMessageList
    }

    fun addUser(user: User) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_ID, user.id)
            put(COLUMN_USERNAME, user.username)
        }
        db.insert(TABLE_NAME_CHAT_ROOM, null, values)
        db.close()
    }

    fun removeUser(userId: String) {
        val db = this.writableDatabase
        db.delete(TABLE_NAME_CHAT_ROOM, "$COLUMN_USER_ID = ?", arrayOf(userId))
        db.close()
    }

    fun getAllUsers(): List<User> {
        val userList = mutableListOf<User>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME_CHAT_ROOM", null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getString(cursor.getColumnIndex(COLUMN_USER_ID))
                val username = cursor.getString(cursor.getColumnIndex(COLUMN_USERNAME))
                userList.add(User(username, id, 0, 0, 2))
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return userList
    }
}
