package jp.techacademy.kenichi04.qa_app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_question_send.*
import java.io.ByteArrayOutputStream

class QuestionSendActivity : AppCompatActivity(), View.OnClickListener, DatabaseReference.CompletionListener {
    companion object {
        private val PERMISSION_REQUEST_CODE = 100
        // Intent連携からActivityに戻った時の識別用定数
        private val CHOOSER_REQUEST_CODE = 100
    }

    private var mGenre: Int = 0
    // カメラ撮影した画像を保存するURI
    private var mPictureUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question_send)

        // 渡ってきたジャンルの番号を保持
        val extras = intent.extras
        mGenre = extras!!.getInt("genre")

        // UIの準備
        title = getString(R.string.question_send_title)

        sendButton.setOnClickListener(this)
        imageView.setOnClickListener(this)
    }

    // Intent連携から戻ってきた時に画像を取得し、ImageViewに設定する
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHOOSER_REQUEST_CODE) {

            if (resultCode != Activity.RESULT_OK) {
                if (mPictureUri != null) {
                    contentResolver.delete(mPictureUri!!, null, null)
                    mPictureUri = null
                }
                return
            }

            // 画像を取得
            // data==nullもしくはdata.data==nullの場合はカメラ撮影
            val uri = if (data == null || data.data == null) mPictureUri else data.data

            // URIからBitmapを取得
            val image: Bitmap
            try {
                val contentResolver = contentResolver
                val inputStream = contentResolver.openInputStream(uri!!)
                image = BitmapFactory.decodeStream(inputStream)
                inputStream!!.close()
            } catch (e: Exception) {
                return
            }

            // 取得したBitmapの長辺を500ピクセルにリサイズ
            val imageWidth = image.width
            val imageHeight = image.height
            val scale = Math.min(500.toFloat() / imageWidth, 500.toFloat() / imageHeight)

            val matrix = Matrix()
            matrix.postScale(scale, scale)

            val resizedImage = Bitmap.createBitmap(image, 0, 0, imageWidth, imageHeight, matrix, true)

            // BitmapをimageViewに設定
            imageView.setImageBitmap(resizedImage)

            mPictureUri = null
        }
    }

    override fun onClick(v: View?) {
        if (v === imageView) {
            // パーミッションの許可状態を確認(Android6.0以降で必要)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    // 許可されている時
                    showChooser()
                } else {
                    // 許可ダイアログを表示
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
                    return
                }
            } else {
                showChooser()
            }

        } else if (v === sendButton) {
            // キーボードが出ていたら閉じる
            val im = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            val dataBaseReference = FirebaseDatabase.getInstance().reference
            val genreRef = dataBaseReference.child(ContentsPATH).child(mGenre.toString())

            val data = HashMap<String, String>()
            // UID
            data["uid"] = FirebaseAuth.getInstance().currentUser!!.uid
            // タイトルと本文を取得
            val title = titleText.text.toString()
            val body = bodyText.text.toString()

            if (title.isEmpty()) {
                // エラー表示
                Snackbar.make(v, getString(R.string.input_title), Snackbar.LENGTH_LONG).show()
                return
            }
            if (body.isEmpty()) {
                // エラー表示
                Snackbar.make(v, getString(R.string.question_message), Snackbar.LENGTH_LONG).show()
                return
            }

            // Preferenceから名前取得
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val name = sp.getString(NameKEY, "")

            data["title"] = title
            data["body"] = body
            data["name"] = name!!

            // 添付画像を取得する(as?により、画像がない場合はnullが返る)
            val drawable = imageView.drawable as? BitmapDrawable
            // 添付画像が設定されていれば画像を取り出しBASE64エンコードする
            if (drawable != null) {
                val bitmap = drawable.bitmap
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val bitmapString = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

                data["image"] = bitmapString
            }

            // 第2引数にはCompletionListenerクラス（今回はActivity）、画像保存に時間を要するためCompletionListenerで完了を受け取る
            // push()メソッドを挟む事で、タイムスタンプに基づいた一意のIDが払い出される
            genreRef.push().setValue(data, this)
            progressBar.visibility = View.VISIBLE
        }
    }

    // 許可ダイアログでユーザーが選択した結果を受け取る
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // ユーザーが許可した時
                    showChooser()
                }
                return
            }
        }
    }

    // ギャラリーから選択するIntent,カメラで撮影するIntent作成し、さらにそれらを選択するIntent作成してダイアログ表示
    private fun showChooser() {
        // ギャラリーから選択するIntent
        // コンテンツを取得するアクション（暗黙的Intent）
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
        galleryIntent.type = "image/*"
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE)

        // カメラで撮影するIntent
        val filename = System.currentTimeMillis().toString() + ".jpg"
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, filename)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        mPictureUri = contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        // カメラを使って画像を撮影するためのアクション（暗黙的Intent）
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPictureUri)

        // ギャラリー選択のIntentを与えてcreateChooserメソッドを呼ぶ(第二引数はダイアログに表示するタイトル)
        val chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.get_image))
        // EXTRA_INITIAL_INTENTSにカメラ撮影のIntentを追加
        // 2つ目のIntentを指定することで、2つのIntentを選択するダイアログが表示される
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))

        // ユーザーがダイアログでintent選択した時、onActivityResultが呼ばれる（CHOOSER_REQUEST_CODEで判定できる）
        startActivityForResult(chooserIntent, CHOOSER_REQUEST_CODE)
    }

    override fun onComplete(error: DatabaseError?, ref: DatabaseReference) {
        progressBar.visibility = View.GONE

        if (error == null) {
            finish()
        } else {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.question_send_error_message), Snackbar.LENGTH_LONG).show()
        }
    }

}