package com.imumotion.amoquette.clientservice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.imumotion.amoquette.R

class UserAdapter(context: Context, resource: Int, objects: MutableList<User>) :
    ArrayAdapter<User>(context, resource, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.user_listview, parent, false)
        }

        // 사용자 정보를 가져옵니다.
        val user = getItem(position)

        // 사용자 정보를 각각의 TextView에 설정합니다.
        val usernameTextView: TextView = convertView!!.findViewById(R.id.username)
        usernameTextView.text = "이름 : ${user?.username}"

        val useridTextView: TextView = convertView.findViewById(R.id.userid)
        useridTextView.text = "전화번호 : ${user?.id}"

        val timeTextView: TextView = convertView.findViewById(R.id.time)
        timeTextView.text = "입장시간 : ${convertLongToTime(user?.time)}"

        val waitnumTextView: TextView = convertView.findViewById(R.id.waitnum)
        waitnumTextView.text = "대기번호 : ${user?.waitnum}"

        val stateTextView : TextView = convertView.findViewById(R.id.state)
        when (user?.state) {
            0 -> stateTextView.text = "상태 : 상담전"
            1 -> stateTextView.text = "상태 : 상담중"
            2 -> stateTextView.text = "상태 : 상담후"
            else -> stateTextView.text = "상태 : 알 수 없음"
        }

        return convertView
    }
}
