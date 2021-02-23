package jp.techacademy.kenichi04.qa_app

import java.io.Serializable

// Intentでデータを渡せるようにするためSerializableクラスを実装
class Question(val title: String, val body: String, val name: String, val uid: String, val questionUid: String, val genre: Int, bytes: ByteArray, val answers: ArrayList<Answer>) : Serializable {
    val imageBytes: ByteArray

    init {
        imageBytes = bytes.clone()
    }
}