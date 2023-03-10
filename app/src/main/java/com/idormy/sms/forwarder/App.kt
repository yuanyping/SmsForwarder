package com.idormy.sms.forwarder

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.multidex.MultiDex
import androidx.work.Configuration
import com.gyf.cactus.Cactus
import com.gyf.cactus.callback.CactusCallback
import com.gyf.cactus.ext.cactus
import com.idormy.sms.forwarder.activity.MainActivity
import com.idormy.sms.forwarder.core.Core
import com.idormy.sms.forwarder.database.AppDatabase
import com.idormy.sms.forwarder.database.repository.*
import com.idormy.sms.forwarder.entity.SimInfo
import com.idormy.sms.forwarder.receiver.CactusReceiver
import com.idormy.sms.forwarder.service.BatteryService
import com.idormy.sms.forwarder.service.ForegroundService
import com.idormy.sms.forwarder.service.HttpService
import com.idormy.sms.forwarder.service.NetworkStateService
import com.idormy.sms.forwarder.utils.*
import com.idormy.sms.forwarder.utils.sdkinit.UMengInit
import com.idormy.sms.forwarder.utils.sdkinit.XBasicLibInit
import com.idormy.sms.forwarder.utils.sdkinit.XUpdateInit
import com.idormy.sms.forwarder.utils.tinker.TinkerLoadLibrary
import com.xuexiang.xutil.app.AppUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("PrivatePropertyName")
class App : Application(), CactusCallback, Configuration.Provider by Core {

    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.getInstance(this) }
    val frpcRepository by lazy { FrpcRepository(database.frpcDao()) }
    val msgRepository by lazy { MsgRepository(database.msgDao()) }
    val logsRepository by lazy { LogsRepository(database.logsDao()) }
    val ruleRepository by lazy { RuleRepository(database.ruleDao()) }
    val senderRepository by lazy { SenderRepository(database.senderDao()) }

    companion object {
        const val TAG: String = "SmsForwarder"

        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context

        //?????????SIM?????????
        var SimInfoList: MutableMap<Int, SimInfo> = mutableMapOf()

        //?????????App??????
        var LoadingAppList = false
        var UserAppList: MutableList<AppUtils.AppInfo> = mutableListOf()
        var SystemAppList: MutableList<AppUtils.AppInfo> = mutableListOf()

        /**
         * @return ??????app???????????????????????????
         */
        val isDebug: Boolean
            get() = BuildConfig.DEBUG

        //Cactus????????????
        val mEndDate = MutableLiveData<String>()

        //Cactus??????????????????
        val mLastTimer = MutableLiveData<String>()

        //Cactus????????????
        val mTimer = MutableLiveData<String>()

        //Cactus????????????
        val mStatus = MutableLiveData<Boolean>().apply { value = true }

        var mDisposable: Disposable? = null
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        //??????4.x?????????????????????
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            context = applicationContext
            initLibs()

            //??????????????????
            if (SettingUtils.enablePureClientMode) return

            //????????????FrpcLib
            val libPath = filesDir.absolutePath + "/libs"
            val soFile = File(libPath)
            if (soFile.exists()) {
                try {
                    TinkerLoadLibrary.installNativeLibraryPath(classLoader, soFile)
                } catch (throwable: Throwable) {
                    Log.e("APP", throwable.message.toString())
                }
            }

            //??????????????????
            val intent = Intent(this, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            //??????????????????
            val networkStateServiceIntent = Intent(this, NetworkStateService::class.java)
            startService(networkStateServiceIntent)

            //??????????????????
            val batteryServiceIntent = Intent(this, BatteryService::class.java)
            startService(batteryServiceIntent)

            //??????HttpServer
            if (HttpServerUtils.enableServerAutorun) {
                startService(Intent(this, HttpService::class.java))
            }

            //Cactus ??????????????????????????????JobScheduler???onePix(?????????)???WorkManager???????????????
            if (SettingUtils.enableCactus) {
                //?????????????????????
                registerReceiver(CactusReceiver(), IntentFilter().apply {
                    addAction(Cactus.CACTUS_WORK)
                    addAction(Cactus.CACTUS_STOP)
                    addAction(Cactus.CACTUS_BACKGROUND)
                    addAction(Cactus.CACTUS_FOREGROUND)
                })
                //???????????????????????????
                val activityIntent = Intent(this, MainActivity::class.java)
                val flags = if (Build.VERSION.SDK_INT >= 30) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                val pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, flags)
                cactus {
                    setServiceId(FRONT_NOTIFY_ID) //??????Id
                    setChannelId(FRONT_CHANNEL_ID) //??????Id
                    setChannelName(FRONT_CHANNEL_NAME) //?????????
                    setTitle(getString(R.string.app_name))
                    setContent(SettingUtils.notifyContent)
                    setSmallIcon(R.drawable.ic_forwarder)
                    setLargeIcon(R.mipmap.ic_launcher)
                    setPendingIntent(pendingIntent)
                    //????????????
                    if (SettingUtils.enablePlaySilenceMusic) {
                        setMusicEnabled(true)
                        setBackgroundMusicEnabled(true)
                        setMusicId(R.raw.silence)
                        //?????????????????????????????????????????????????????????
                        setMusicInterval(10)
                        isDebug(true)
                    }
                    //????????????????????????????????????????????????????????????android p??????????????????
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && SettingUtils.enableOnePixelActivity) {
                        setOnePixEnabled(true)
                    }
                    //????????????????????????????????????
                    setCrashRestartUIEnabled(true)
                    addCallback({
                        Log.d(TAG, "Cactus?????????onStop??????")
                    }) {
                        Log.d(TAG, "Cactus?????????doWork??????")
                    }
                    //?????????????????????
                    addBackgroundCallback {
                        Log.d(TAG, if (it) "SmsForwarder ?????????????????????" else "SmsForwarder ?????????????????????")
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ??????????????????
     */
    private fun initLibs() {
        Core.init(this)
        // ?????????????????????
        SharedPreference.init(applicationContext)
        // ??????????????????????????????
        HistoryUtils.init(applicationContext)
        // X????????????????????????
        XBasicLibInit.init(this)
        // ?????????????????????
        XUpdateInit.init(this)
        // ??????????????????
        UMengInit.init(this)
    }

    @SuppressLint("CheckResult")
    override fun doWork(times: Int) {
        Log.d(TAG, "doWork:$times")
        mStatus.postValue(true)
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("GMT+00:00")
        var oldTimer = CactusSave.timer
        if (times == 1) {
            CactusSave.lastTimer = oldTimer
            CactusSave.endDate = CactusSave.date
            oldTimer = 0L
        }
        mLastTimer.postValue(dateFormat.format(Date(CactusSave.lastTimer * 1000)))
        mEndDate.postValue(CactusSave.endDate)
        mDisposable = Observable.interval(1, TimeUnit.SECONDS)
            .map {
                oldTimer + it
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { aLong ->
                CactusSave.timer = aLong
                CactusSave.date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).run {
                    format(Date())
                }
                mTimer.value = dateFormat.format(Date(aLong * 1000))
            }
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        mStatus.postValue(false)
        mDisposable?.apply {
            if (!isDisposed) {
                dispose()
            }
        }
    }

}