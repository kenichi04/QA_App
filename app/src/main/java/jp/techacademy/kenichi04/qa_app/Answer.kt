package jp.techacademy.kenichi04.qa_app

import java.io.Serializable

// Answerクラスを保持するQuestionクラスがSerializableを実装させるため、このクラスも同様にする必要あり
class Answer(val body: String, val name: String, val uid: String, val answerUid: String) : Serializable {
}