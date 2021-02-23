package jp.techacademy.kenichi04.qa_app

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity : AppCompatActivity() {
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mCreateAccountListener: OnCompleteListener<AuthResult>
    private lateinit var mLoginListener: OnCompleteListener<AuthResult>
    private lateinit var mDataBaseReference: DatabaseReference

    // アカウント作成時にフラグを立て、ログイン処理後に名前をFirebaseに保存
    private var mIsCreateAccount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // データベースへのリファレンス取得
        mDataBaseReference = FirebaseDatabase.getInstance().reference
        // FirebaseAuthのオブジェクト取得
        mAuth = FirebaseAuth.getInstance()

        // アカウント作成処理のリスナー
        mCreateAccountListener = OnCompleteListener { task ->
            if (task.isSuccessful) {
                // 成功した場合、ログイン
                val email = emailText.text.toString()
                val password = passwordText.text.toString()
                // ログイン処理
                login(email, password)

            } else {
                // 失敗した場合、エラー表示
                val view = findViewById<View>(android.R.id.content)
                Snackbar.make(view, getString(R.string.create_account_failure_message), Snackbar.LENGTH_LONG).show()

                // プログレスバーを非表示
                progressBar.visibility = View.GONE
            }
        }

        // ログイン処理のリスナー
        mLoginListener = OnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = mAuth.currentUser
                val userRef = mDataBaseReference.child(UsersPATH).child(user!!.uid)

                // アカウント作成ボタンを押してのログイン処理
                if (mIsCreateAccount) {
                    // アカウント作成時は、表示名をFirebaseに保存する
                    val name = nameText.text.toString()

                    val data = HashMap<String, String>()
                    data["name"] = name
                    userRef.setValue(data)

                    // 表示名をPreferenceに保存
                    saveName(name)

                // ログインボタン押下時
                } else {
                    // Firebaseから表示名を取得して、Preferenceに保存
                    userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val data = snapshot.value as Map<*, *>?
                            saveName(data!!["name"] as String)
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }

                // プログレスバーを非表示
                progressBar.visibility = View.GONE
                // Activityを閉じる
                finish()

            } else {
                // エラー表示
                val view = findViewById<View>(android.R.id.content)
                Snackbar.make(view, getString(R.string.login_failure_message), Snackbar.LENGTH_LONG).show()

                // プログレスバーを非表示
                progressBar.visibility = View.GONE
            }
        }

        // タイトルの設定
        title = getString(R.string.login_title)

        createButton.setOnClickListener { v ->
            // キーボードが出ていたら閉じる
            val im = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            val email = emailText.text.toString()
            val password = passwordText.text.toString()
            val name = nameText.text.toString()

            if (email.length != 0 && password.length >= 6 && name.length != 0) {
                // ログイン時に表示名を保存するようフラグを立てる
                mIsCreateAccount = true
                createAccount(email, password)

            } else {
                // エラー表示
                Snackbar.make(v, getString(R.string.login_error_message), Snackbar.LENGTH_LONG).show()
            }
        }

        loginButton.setOnClickListener { v ->
            // キーボードが出ていたら閉じる
            val im = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            val email = emailText.text.toString()
            val password = passwordText.text.toString()

            if (email.length != 0 && password.length >= 6) {
                // フラグを落としておく
                mIsCreateAccount = false
                login(email, password)

            } else {
                Snackbar.make(v, getString(R.string.login_error_message), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun createAccount(email: String, password: String) {
        // プログレスバーを表示
        progressBar.visibility = View.VISIBLE

        // アカウント作成
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(mCreateAccountListener)
    }

    private fun login(email: String, password: String) {
        progressBar.visibility = View.VISIBLE

        // ログイン
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(mLoginListener)
    }

    private fun saveName(name: String) {
        // Preferenceに保存
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sp.edit()
        editor.putString(NameKEY, name)
        editor.commit()
    }

}