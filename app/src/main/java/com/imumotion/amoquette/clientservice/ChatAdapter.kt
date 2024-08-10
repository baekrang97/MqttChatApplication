package com.imumotion.amoquette.clientservice

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.imumotion.amoquette.R


class ChatAdapter(private val context: Context, resource: Int, private val items: MutableList<Message>) : BaseAdapter() {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(R.layout.chat_listview, parent, false)
        }

        val item = getItem(position)
        val senderTextView = view?.findViewById<TextView>(R.id.senderTextView)
        val messageTextView = view?.findViewById<TextView>(R.id.messageTextView)
        val timeTextView = view?.findViewById<TextView>(R.id.timeTextView)

        item?.let {
            senderTextView?.text = it.sender // 발신자 표시
            messageTextView?.text = it.text // 메시지 내용 표시
            timeTextView?.text = convertLongToTime(it.time)
        }

        return view!!
    }

    override fun getItem(position: Int): Message? {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return items.size
    }

    fun addMessage(message: Message) {
        items.add(message)
        notifyDataSetChanged() // ListView 업데이트
    }
}
