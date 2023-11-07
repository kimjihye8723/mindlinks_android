package kr.co.mindlinks

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.webkit.WebView.WebViewTransport
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), DialogInterface.OnClickListener {

    //region 기본 설정
    val TAG: String = "로그"

    //카메라
    private var photoPath = ""
    private var videoPath = ""
    private var mWebViewImageUpload: ValueCallback<Array<Uri>>? = null

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private val reQuestPermissions = 1

    //endregion

    //권한
    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    val MULTIPLE_PERMISSIONS = 99
    var cookieManager: CookieManager? = null
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(this);
        }
//        swipe = findViewById(R.id.swipe)
//        swipe.setOnRefreshListener {
//            webView.reload()
//            Log.d("==새로고침==","웹뷰새로고침")
//            swipe.isRefreshing = false
//        }

        //region WebView
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE );
        webView = findViewById(R.id.webView)
        var settings = webView.settings
        settings.javaScriptEnabled = true //자바스크립트 허용
        settings.domStorageEnabled = true //로컬 저장 허용
        settings.setSupportMultipleWindows(true) //새창 허용
        settings.loadWithOverviewMode = true //메타태그 허용
        settings.javaScriptCanOpenWindowsAutomatically = true //자바스크립트 새창 허용
        settings.useWideViewPort = true //화면 사이즈 맟춤 허용
        settings.setSupportZoom(false) //화면 줌 허용 여부
        settings.builtInZoomControls = false //화면 확대 축소 허용 여부
        settings.allowFileAccess = true

        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.apply {

            settings.javaScriptEnabled = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true

            webViewClient = object:WebViewClient(){
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 페이지 로딩이 완료된 후, CookieManager를 사용하여 쿠키를 가져옵니다.
                    CookieManager.getInstance().flush()
                }

            }

            webChromeClient = WebChromeClient()

            //오레오보다 같거나 크면
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

                webView.loadUrl("https://mindlinks.co.kr/user")
            }else{
                webView.loadUrl("https://mindlinks.co.kr/user")
            }

            //https://mindlinks.co.kr/user

            webView.webChromeClient = object : WebChromeClient() {


                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    onJsAlert(message!!, result!!)
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    onJsConfirm(message!!, result!!)
                    return true
                }

                override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                    try{
                        mWebViewImageUpload = filePathCallback!!
                        val intentArray: Array<Intent?>

                        var takePictureIntent : Intent?
                        takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if(takePictureIntent.resolveActivity(packageManager) != null){
                            var photoFile : File?

                            photoFile = createImageFile()
                            takePictureIntent.putExtra("PhotoPath",photoPath)

                            if(photoFile != null){
                                photoPath = "file:${photoFile.absolutePath}"
                                takePictureIntent.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    FileProvider.getUriForFile(this@MainActivity, BuildConfig.APPLICATION_ID + ".provider", photoFile)
                                )
                            }
                            else takePictureIntent = null
                        }

                        var takeVideoIntent : Intent?
                        takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                        if(takeVideoIntent.resolveActivity(packageManager) != null){
                            var videoFile : File?

                            videoFile = createVideoFile()
                            takeVideoIntent.putExtra("VideoPath",videoPath)

                            if(videoFile != null){
                                videoPath = "file:${videoFile!!.absolutePath}"
                                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(this@MainActivity, BuildConfig.APPLICATION_ID + ".provider", videoFile) )
                                takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                                // takeVideoIntent.putExtra( MediaStore.EXTRA_SIZE_LIMIT, 15000000L ); //about 14Mb
                                takeVideoIntent.putExtra(
                                    MediaStore.EXTRA_DURATION_LIMIT,
                                    15
                                ) //15 sec
                            }
                            else takeVideoIntent = null
                        }

                        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                        contentSelectionIntent.type = "*/*"
                        contentSelectionIntent.putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf("image/*", "video/*", "*/*")
                        )


                        if(photoPath != ""){
                            intentArray = takePictureIntent?.let { arrayOf(it) } ?: arrayOfNulls(0)
                        }else{
                            intentArray = takeVideoIntent?.let { arrayOf(it) } ?: arrayOfNulls(0)
                        }

                        val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Choose file")
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                        launcher.launch(chooserIntent)
                    }
                    catch (e : Exception){
                        Log.d(TAG, e.toString())
                    }
                    return true
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {
                    // Dialog Create Code
                    val newWebView = WebView(this@MainActivity)
                    val webSettings = newWebView.settings
                    webSettings.javaScriptEnabled = true
                    val dialog = Dialog(this@MainActivity)
                    dialog.setContentView(newWebView)
                    val params: ViewGroup.LayoutParams = dialog.window!!.attributes
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    dialog.window!!.attributes = params as WindowManager.LayoutParams
                    dialog.show()
                    newWebView.webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView) {
                            dialog.dismiss()
                        }
                    }

                    // WebView Popup에서 내용이 안보이고 빈 화면만 보여 아래 코드 추가
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            return false
                        }
                    }
                    (resultMsg.obj as WebViewTransport).webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }



            }

            //권한 확인
            checkPermission()
            cookieManager?.let { setCookieAllow(it, webView) };
        }
        //endregion

        //region PUSH Notification
        /** DynamicLink 수신확인 */
        initDynamicLink()
        //endregion
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll();

    }


    private fun setCookieAllow(cookieManager: CookieManager, webView: WebView) {
        var cookieManager = cookieManager
        try {
            cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cookieManager.setAcceptThirdPartyCookies(webView, true)
            }
        } catch (e: java.lang.Exception) {
        }
    }

    private fun checkPermission() {
        // 카메라 권한이 뭔지를 체크를 해서 cameraPermission에 저장
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        if(cameraPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_DENIED || readPermission != PackageManager.PERMISSION_DENIED) {
            requestPermission()
        }
    }

    private fun requestPermission() {
        // 실제 권한 요청
        // ActivityCompat를 쓰는 이유는 하위 버전에서는 호환이 안될 수 있어서임
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 99)
        // 이렇게 하면 사용자에게 위 권한을 사용할건지 물어보게 됨
        // 그리고 승인하든 거절하든 그 결과값은 onRequestPermissionsResult으로 가게 됨.
    }

    // onRequestPermissionsResult으로 가게 됨.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            99 -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "권한을 승인하지 않으면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /**
     * 권한 거부 시 실행 메소드
     */
    private fun showNoPermissionToastAndFinish() {
        val toast = Toast.makeText(
            this,
            "권한 요청에 동의 해주셔야 이용 가능합니다. 설정에서 권한 허용 하시기 바랍니다.",
            Toast.LENGTH_SHORT
        )
        toast.show()
        finish()
    }


    private fun initDynamicLink() {
        val dynamicLinkData = intent.extras
        if (dynamicLinkData != null) {
            var dataStr = "DynamicLink 수신받은 값\n"
            for (key in dynamicLinkData.keySet()) {
                dataStr += "key: $key / value: ${dynamicLinkData.getString(key)}\n"
            }

        }
    }

    //region 카메라/사진 불러오기
    fun createImageFile(): File? {
        @SuppressLint("SimpleDateFormat")
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "img_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    fun createVideoFile(): File? {
        @SuppressLint("SimpleDateFormat")
        var file_name: String? = SimpleDateFormat("yyyy_mm_ss").format(Date())
        val new_name = "file_" + file_name + "_"
        val sd_directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(new_name, ".mp4", sd_directory)
    }

    val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == RESULT_OK) {
            val intent = result.data

            if(intent == null){ //바로 사진을 찍어서 올리는 경우
                var results: Array<Uri>? = null
                if(photoPath != ""){
                    results = arrayOf(Uri.parse(photoPath))
                }else{
                    results = arrayOf(Uri.parse(videoPath))
                }

                mWebViewImageUpload!!.onReceiveValue(results!!)
            }
            else{ //사진 앱을 통해 사진을 가져온 경우
                val results = intent!!.data!!
                mWebViewImageUpload!!.onReceiveValue(arrayOf(results!!))
            }
        }
        else{ //취소 한 경우 초기화
            mWebViewImageUpload!!.onReceiveValue(null)
            mWebViewImageUpload = null
        }
    }
    //endregion

    //region 기본 액션 설정
    fun onJsConfirm(message : String, result : JsResult) : Unit {
        var builder = AlertDialog.Builder(this)
        builder.setTitle("알 림")
        builder.setMessage(message)

        // 버튼 클릭 이벤트
        var listener = DialogInterface.OnClickListener { _, clickEvent ->
            when (clickEvent) {
                DialogInterface.BUTTON_POSITIVE ->{
                    result!!.confirm()
                }

                DialogInterface.BUTTON_NEUTRAL -> {
                    result!!.cancel()
                }
            }
        }
        builder.setPositiveButton(android.R.string.ok, listener)
        builder.setNeutralButton(android.R.string.cancel, listener)
        builder.show()
    }

    fun onJsAlert(message : String, result : JsResult) : Unit{
        var builder = AlertDialog.Builder(this)
        builder.setTitle("알 림")
        builder.setMessage(message)
        builder.setCancelable(false)
        // 버튼 클릭 이벤트
        var listener = DialogInterface.OnClickListener { _, clickEvent ->
            when (clickEvent) {
                DialogInterface.BUTTON_POSITIVE ->{
                    result!!.confirm()
                }
            }
        }
        builder.setPositiveButton(android.R.string.ok, listener)
        builder.show()
    }
    private var backBtnTime: Long = 0


    override fun onClick(p0: DialogInterface?, p1: Int) {
        val pid = android.os.Process.myPid()

        when(p1){
            -1 -> {
                android.os.Process.killProcess(pid)
                finish()
            }

            0 -> {
                //취소
            }
        }
    }

    /* 뒤로가기 버튼 처리 */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val curTime = System.currentTimeMillis()
        val gapTime = curTime - backBtnTime
        if (webView.canGoBack()) {
            webView.goBack()
        } else if (gapTime in 0..2000) {
            super.onBackPressed()
        } else {
            backBtnTime = curTime
            Toast.makeText(this, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    //endregion

    //region WebView 통신 (남겨놓음)
    /** Instantiate the interface and set the context  */
    inner class WebAppInterface(private val mContext: Context) {

        //디바이스별 토큰 받아오기
        @JavascriptInterface
        fun setToken(){

            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.d(TAG, "Fetching FCM registration token failed ${task.exception}")
                    return@OnCompleteListener
                }
                var deviceToken = task.result
                Log.d(TAG, "deviceToken $deviceToken")
                webView.post(Runnable { //디바이스 토큰 웹뷰로 전송
                    Log.d(TAG, "token=${deviceToken}")
                    webView.loadUrl("javascript:setToken('${deviceToken.toString()}')")
                })
            })

        }

        //전화번호 받는 부분
        @JavascriptInterface
        fun callNumber(num: String){

            val mIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${num}"))
            startActivity(mIntent)

        }

    }
}

