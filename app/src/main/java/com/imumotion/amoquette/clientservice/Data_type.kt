package com.imumotion.amoquette.clientservice

data class Message(val sender: String,val receiver: String,val time: Long,val text:String)//누가 어떤 메세지를 보여주는지 알기위해 만들어놓음
data class User(val username:String,val id:String,var time : Long,var waitnum:Int,var state : Int)//유저의 정보를 저장한다.
//0-> 상담전
//1-> 상담중
//2-> 상담후

//type들은 상수로 선언한다.int
/*type
    이용자가 보내는 type
        wait_enter      ->이용자가 대기리스트로 들어가는 경우(사용자 이름 요소 추가)
        wait_exit       ->이용자가 대기리스트에서 나가는 경우
    사용자가 보내는 type
        wait_enter_check->사용자가 해당 이용자에게 들어온 것을 확인했다고 보낸다.(대기번호,현재 대기번호)
        wait_enter_chat ->사용자가 이용자에게 몇번 들어오라고 보낸다.(현재대기번호 추가)
 */
//data class Wait(val type:Int,val sender: String,val receiver:String,val time: Long,val username: String?,val waitNumber: Int?, val currentWaitNumber: Int?)
//data class Chat(val type: Int,val sender: String, val receiver: String,val time: Long,val text:String?)


open class Wait(val type: Int,
                val sender: String,
                val receiver: String,
                val time: Long)

data class WaitEnter(val wait: Wait, val username: String)

data class WaitExit(val wait: Wait)

data class WaitEnterCheck(val wait: Wait, val waitNumber: Int, val currentWaitNumber: Int)

data class WaitEnterChat(val wait: Wait, val currentWaitNumber: Int)
/*type
    이용자가 보내는 type
        chat_enter          ->대화방에 입장했다고 사용자에게 알려준다.
        chat_exit_agree     ->대화방종료에 동의 했다고 사용자에게 알려준다.
        chat_exit_disagree  ->대화방종료에 동의하지 않았다고 사용자에게 알려준다.
        chat_text           ->실제대화 type
    사용자가 보내는 type
        chat_exit_permission->대화방을 종료할지 확인한다.(대화종료 버튼 누를때 생성)
        chat_exit_force     ->대화방을 강제로 종료한다.
        chat_text           ->실제 대화 type
 */

open class Chat(
    open val type: Int,
    open val sender: String,
    open val receiver: String,
    open val time: Long
)

data class ChatEnter(val chat: Chat)

data class ChatExitAgree(val chat: Chat)

data class ChatExitDisagree(val chat: Chat)

data class ChatText(val chat: Chat, val text: String)

data class ChatExitPermission(val chat: Chat)

data class ChatExitForce(val chat: Chat)