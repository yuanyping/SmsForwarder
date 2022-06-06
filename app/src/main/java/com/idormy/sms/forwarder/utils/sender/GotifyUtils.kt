package com.idormy.sms.forwarder.utils.sender

import android.util.Log
import com.google.gson.Gson
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.database.entity.Rule
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.result.GotifyResult
import com.idormy.sms.forwarder.entity.setting.GotifySetting
import com.idormy.sms.forwarder.utils.SendUtils
import com.idormy.sms.forwarder.utils.SettingUtils
import com.idormy.sms.forwarder.utils.XToastUtils
import com.xuexiang.xhttp2.XHttp
import com.xuexiang.xhttp2.cache.model.CacheMode
import com.xuexiang.xhttp2.callback.SimpleCallBack
import com.xuexiang.xhttp2.exception.ApiException
import com.xuexiang.xui.utils.ResUtils

@Suppress("PrivatePropertyName", "UNUSED_PARAMETER", "unused")
class GotifyUtils {
    companion object {

        private val TAG: String = GotifyUtils::class.java.simpleName

        fun sendMsg(
            setting: GotifySetting,
            msgInfo: MsgInfo,
            rule: Rule?,
            logId: Long?,
        ) {
            val title: String = if (rule != null) {
                msgInfo.getTitleForSend(setting.title.toString(), rule.regexReplace)
            } else {
                msgInfo.getTitleForSend(setting.title.toString())
            }
            val content: String = if (rule != null) {
                msgInfo.getContentForSend(rule.smsTemplate, rule.regexReplace)
            } else {
                msgInfo.getContentForSend(SettingUtils.smsTemplate.toString())
            }

            val requestUrl: String = setting.webServer //推送地址
            Log.i(TAG, "requestUrl:$requestUrl")

            XHttp.post(requestUrl)
                .params("title", title)
                .params("message", content)
                .params("priority", setting.priority)
                .keepJson(true)
                .timeOut((SettingUtils.requestTimeout * 1000).toLong()) //超时时间10s
                .cacheMode(CacheMode.NO_CACHE)
                .retryCount(SettingUtils.requestRetryTimes) //超时重试的次数
                .retryDelay(SettingUtils.requestDelayTime) //超时重试的延迟时间
                .retryIncreaseDelay(SettingUtils.requestDelayTime) //超时重试叠加延时
                .timeStamp(true)
                .execute(object : SimpleCallBack<String>() {

                    override fun onError(e: ApiException) {
                        SendUtils.updateLogs(logId, 0, e.displayMessage)
                        Log.e(TAG, e.detailMessage)
                        XToastUtils.error(e.displayMessage)
                    }

                    override fun onSuccess(response: String) {
                        Log.i(TAG, response)

                        val resp = Gson().fromJson(response, GotifyResult::class.java)
                        if (resp?.id != null) {
                            SendUtils.updateLogs(logId, 2, response)
                            XToastUtils.success(ResUtils.getString(R.string.request_succeeded))
                        } else {
                            SendUtils.updateLogs(logId, 0, response)
                            XToastUtils.error(ResUtils.getString(R.string.request_failed) + response)
                        }
                    }

                })

        }

        fun sendMsg(setting: GotifySetting, msgInfo: MsgInfo) {
            sendMsg(setting, msgInfo, null, null)
        }
    }
}