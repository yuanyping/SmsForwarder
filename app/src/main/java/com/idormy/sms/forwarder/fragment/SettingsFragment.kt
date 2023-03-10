package com.idormy.sms.forwarder.fragment

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.idormy.sms.forwarder.App
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.adapter.spinner.AppListAdapterItem
import com.idormy.sms.forwarder.adapter.spinner.AppListSpinnerAdapter
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.databinding.FragmentSettingsBinding
import com.idormy.sms.forwarder.entity.SimInfo
import com.idormy.sms.forwarder.receiver.BootReceiver
import com.idormy.sms.forwarder.utils.*
import com.idormy.sms.forwarder.workers.LoadAppListWorker
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.button.SmoothCheckBox
import com.xuexiang.xui.widget.button.switchbutton.SwitchButton
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xui.widget.picker.XRangeSlider
import com.xuexiang.xui.widget.picker.XRangeSlider.OnRangeSliderListener
import com.xuexiang.xui.widget.picker.XSeekBar
import com.xuexiang.xui.widget.picker.widget.builder.OptionsPickerBuilder
import com.xuexiang.xui.widget.picker.widget.builder.TimePickerBuilder
import com.xuexiang.xui.widget.picker.widget.listener.OnOptionsSelectListener
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.XUtil.getPackageManager
import com.xuexiang.xutil.app.AppUtils.getAppPackageName
import com.xuexiang.xutil.data.DateUtils
import kotlinx.coroutines.*
import java.util.*

@Suppress("PropertyName", "SpellCheckingInspection")
@Page(name = "????????????")
class SettingsFragment : BaseFragment<FragmentSettingsBinding?>(), View.OnClickListener {

    val TAG: String = SettingsFragment::class.java.simpleName
    private val mTimeOption = DataProvider.timePeriodOption

    //?????????App????????????
    private val appListSpinnerList = ArrayList<AppListAdapterItem>()
    private lateinit var appListSpinnerAdapter: AppListSpinnerAdapter<*>
    private val appListObserver = Observer { it: String ->
        Log.d(TAG, "EVENT_LOAD_APP_LIST: $it")
        initAppSpinner()
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    /**
     * @return ????????? null????????????????????????
     */
    override fun initTitle(): TitleBar? {
        return null
    }

    @SuppressLint("NewApi", "SetTextI18n")
    override fun initViews() {

        //??????????????????
        switchEnableSms(binding!!.sbEnableSms)
        //??????????????????
        switchEnablePhone(
            binding!!.sbEnablePhone, binding!!.scbCallType1, binding!!.scbCallType2, binding!!.scbCallType3, binding!!.scbCallType4, binding!!.scbCallType5, binding!!.scbCallType6
        )
        //??????????????????
        switchEnableAppNotify(
            binding!!.sbEnableAppNotify, binding!!.scbCancelAppNotify, binding!!.scbNotUserPresent
        )

        //????????????
        switchEnableSmsCommand(binding!!.sbEnableSmsCommand, binding!!.etSafePhone)

        //????????????????????????APP??????
        editExtraAppList(binding!!.etAppList)
        //??????????????????????????????App??????
        switchEnableLoadAppList(
            binding!!.sbEnableLoadAppList, binding!!.scbLoadUserApp, binding!!.scbLoadSystemApp
        )
        //???????????????????????????
        binding!!.xsbDuplicateMessagesLimits.setDefaultValue(SettingUtils.duplicateMessagesLimits)
        binding!!.xsbDuplicateMessagesLimits.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            SettingUtils.duplicateMessagesLimits = newValue
        }
        //?????????(????????????)?????????
        binding!!.tvSilentPeriod.text = mTimeOption[SettingUtils.silentPeriodStart] + " ~ " + mTimeOption[SettingUtils.silentPeriodEnd]
        //????????????N?????????????????????
        binding!!.xsbAutoCleanLogs.setDefaultValue(SettingUtils.autoCleanLogsDays)
        binding!!.xsbAutoCleanLogs.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            SettingUtils.autoCleanLogsDays = newValue
        }

        //????????????????????????
        switchNetworkStateReceiver(binding!!.sbNetworkStateReceiver)

        //????????????????????????
        switchBatteryReceiver(binding!!.sbBatteryReceiver)
        //????????????
        editBatteryLevelAlarm(binding!!.xrsBatteryLevelAlarm, binding!!.scbBatteryLevelAlarmOnce)
        //????????????????????????
        switchBatteryCron(binding!!.sbBatteryCron)
        //??????????????????????????????
        editBatteryCronTiming(binding!!.etBatteryCronStartTime, binding!!.etBatteryCronInterval)

        //????????????
        checkWithReboot(binding!!.sbWithReboot, binding!!.tvAutoStartup)
        //????????????????????????
        batterySetting(binding!!.layoutBatterySetting, binding!!.sbBatterySetting)
        //?????????????????????????????????
        switchExcludeFromRecents(binding!!.layoutExcludeFromRecents, binding!!.sbExcludeFromRecents)

        //Cactus??????????????????
        switchEnableCactus(
            binding!!.sbEnableCactus, binding!!.scbPlaySilenceMusic, binding!!.scbOnePixelActivity
        )

        //????????????????????????????????????
        editRetryDelayTime(binding!!.etRetryTimes, binding!!.etDelayTime, binding!!.etTimeout)

        //????????????
        editAddExtraDeviceMark(binding!!.etExtraDeviceMark)
        //SIM1??????
        editAddSubidSim1(binding!!.etSubidSim1)
        //SIM2??????
        editAddSubidSim2(binding!!.etSubidSim2)
        //SIM1??????
        editAddExtraSim1(binding!!.etExtraSim1)
        //SIM2??????
        editAddExtraSim2(binding!!.etExtraSim2)
        //????????????
        editNotifyContent(binding!!.etNotifyContent)

        //?????????????????????
        switchSmsTemplate(binding!!.sbSmsTemplate)
        //???????????????
        editSmsTemplate(binding!!.etSmsTemplate)

        //????????????
        switchHelpTip(binding!!.sbHelpTip)

        //??????????????????
        switchDirectlyToClient(binding!!.sbDirectlyToClient)
    }

    override fun onResume() {
        super.onResume()
        //?????????APP????????????
        initAppSpinner()
    }

    override fun initListeners() {
        binding!!.btnSilentPeriod.setOnClickListener(this)
        binding!!.btnExtraDeviceMark.setOnClickListener(this)
        binding!!.btnExtraSim1.setOnClickListener(this)
        binding!!.btnExtraSim2.setOnClickListener(this)
        binding!!.btInsertSender.setOnClickListener(this)
        binding!!.btInsertContent.setOnClickListener(this)
        binding!!.btInsertExtra.setOnClickListener(this)
        binding!!.btInsertTime.setOnClickListener(this)
        binding!!.btInsertDeviceName.setOnClickListener(this)

        //???????????????App??????????????????????????????
        LiveEventBus.get(EVENT_LOAD_APP_LIST, String::class.java).observeStickyForever(appListObserver)

    }

    @SuppressLint("SetTextI18n")
    @SingleClick
    override fun onClick(v: View) {
        val etSmsTemplate: EditText = binding!!.etSmsTemplate
        when (v.id) {
            R.id.btn_silent_period -> {
                OptionsPickerBuilder(context, OnOptionsSelectListener { _: View?, options1: Int, options2: Int, _: Int ->
                    SettingUtils.silentPeriodStart = options1
                    SettingUtils.silentPeriodEnd = options2
                    val txt = mTimeOption[options1] + " ~ " + mTimeOption[options2]
                    binding!!.tvSilentPeriod.text = txt
                    XToastUtils.toast(txt)
                    return@OnOptionsSelectListener false
                }).setTitleText(getString(R.string.select_time_period)).setSelectOptions(SettingUtils.silentPeriodStart, SettingUtils.silentPeriodEnd).build<Any>().also {
                    it.setNPicker(mTimeOption, mTimeOption)
                    it.show()
                }
            }
            R.id.btn_extra_device_mark -> {
                binding!!.etExtraDeviceMark.setText(PhoneUtils.getDeviceName())
                return
            }
            R.id.btn_extra_sim1 -> {
                App.SimInfoList = PhoneUtils.getSimMultiInfo()
                if (App.SimInfoList.isEmpty()) {
                    XToastUtils.error(R.string.tip_can_not_get_sim_infos)
                    XXPermissions.startPermissionActivity(
                        requireContext(), "android.permission.READ_PHONE_STATE"
                    )
                    return
                }
                Log.d(TAG, App.SimInfoList.toString())
                if (!App.SimInfoList.containsKey(0)) {
                    XToastUtils.error(
                        String.format(
                            getString(R.string.tip_can_not_get_sim_info), 1
                        )
                    )
                    return
                }
                val simInfo: SimInfo? = App.SimInfoList[0]
                binding!!.etSubidSim1.setText(simInfo?.mSubscriptionId.toString())
                binding!!.etExtraSim1.setText(simInfo?.mCarrierName.toString() + "_" + simInfo?.mNumber.toString())
                return
            }
            R.id.btn_extra_sim2 -> {
                App.SimInfoList = PhoneUtils.getSimMultiInfo()
                if (App.SimInfoList.isEmpty()) {
                    XToastUtils.error(R.string.tip_can_not_get_sim_infos)
                    XXPermissions.startPermissionActivity(
                        requireContext(), "android.permission.READ_PHONE_STATE"
                    )
                    return
                }
                Log.d(TAG, App.SimInfoList.toString())
                if (!App.SimInfoList.containsKey(1)) {
                    XToastUtils.error(
                        String.format(
                            getString(R.string.tip_can_not_get_sim_info), 2
                        )
                    )
                    return
                }
                val simInfo: SimInfo? = App.SimInfoList[1]
                binding!!.etSubidSim2.setText(simInfo?.mSubscriptionId.toString())
                binding!!.etExtraSim2.setText(simInfo?.mCarrierName.toString() + "_" + simInfo?.mNumber.toString())
                return
            }
            R.id.bt_insert_sender -> {
                CommonUtils.insertOrReplaceText2Cursor(etSmsTemplate, getString(R.string.tag_from))
                return
            }
            R.id.bt_insert_content -> {
                CommonUtils.insertOrReplaceText2Cursor(etSmsTemplate, getString(R.string.tag_sms))
                return
            }
            R.id.bt_insert_extra -> {
                CommonUtils.insertOrReplaceText2Cursor(
                    etSmsTemplate, getString(R.string.tag_card_slot)
                )
                return
            }
            R.id.bt_insert_time -> {
                CommonUtils.insertOrReplaceText2Cursor(
                    etSmsTemplate, getString(R.string.tag_receive_time)
                )
                return
            }
            R.id.bt_insert_device_name -> {
                CommonUtils.insertOrReplaceText2Cursor(
                    etSmsTemplate, getString(R.string.tag_device_name)
                )
                return
            }
            else -> {}
        }
    }

    //????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchEnableSms(sbEnableSms: SwitchButton) {
        sbEnableSms.isChecked = SettingUtils.enableSms
        sbEnableSms.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableSms = isChecked
            if (isChecked) {
                //????????????????????????
                XXPermissions.with(this)
                    // ????????????
                    .permission(Permission.RECEIVE_SMS)
                    // ????????????
                    //.permission(Permission.SEND_SMS)
                    // ????????????
                    .permission(Permission.READ_SMS).request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                XToastUtils.info(R.string.toast_granted_all)
                            } else {
                                XToastUtils.info(R.string.toast_granted_part)
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                XToastUtils.info(R.string.toast_denied_never)
                                // ??????????????????????????????????????????????????????????????????
                                XXPermissions.startPermissionActivity(requireContext(), permissions)
                            } else {
                                XToastUtils.info(R.string.toast_denied)
                            }
                            SettingUtils.enableSms = false
                            sbEnableSms.isChecked = false
                        }
                    })
            }
        }
    }

    //????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchEnablePhone(
        sbEnablePhone: SwitchButton, scbCallType1: SmoothCheckBox, scbCallType2: SmoothCheckBox, scbCallType3: SmoothCheckBox, scbCallType4: SmoothCheckBox, scbCallType5: SmoothCheckBox, scbCallType6: SmoothCheckBox
    ) {
        sbEnablePhone.isChecked = SettingUtils.enablePhone
        scbCallType1.isChecked = SettingUtils.enableCallType1
        scbCallType2.isChecked = SettingUtils.enableCallType2
        scbCallType3.isChecked = SettingUtils.enableCallType3
        scbCallType4.isChecked = SettingUtils.enableCallType4
        scbCallType5.isChecked = SettingUtils.enableCallType5
        scbCallType6.isChecked = SettingUtils.enableCallType6
        sbEnablePhone.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
                return@setOnCheckedChangeListener
            }
            SettingUtils.enablePhone = isChecked
            if (isChecked) {
                //????????????????????????
                XXPermissions.with(this)
                    // ??????????????????
                    .permission(Permission.READ_PHONE_STATE)
                    // ??????????????????
                    .permission(Permission.READ_PHONE_NUMBERS)
                    // ??????????????????
                    .permission(Permission.READ_CALL_LOG)
                    // ???????????????
                    .permission(Permission.READ_CONTACTS).request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                XToastUtils.info(R.string.toast_granted_all)
                            } else {
                                XToastUtils.info(R.string.toast_granted_part)
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                XToastUtils.info(R.string.toast_denied_never)
                                // ??????????????????????????????????????????????????????????????????
                                XXPermissions.startPermissionActivity(requireContext(), permissions)
                            } else {
                                XToastUtils.info(R.string.toast_denied)
                            }
                            SettingUtils.enablePhone = false
                            sbEnablePhone.isChecked = false
                        }
                    })
            }
        }
        scbCallType1.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCallType1 = isChecked
            if (!isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
            }
        }
        scbCallType2.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCallType2 = isChecked
            if (!isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
            }
        }
        scbCallType3.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCallType3 = isChecked
            if (!isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
            }
        }
        scbCallType4.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCallType4 = isChecked
            if (!isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
            }
        }
        scbCallType5.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCallType5 = isChecked
            if (!isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
            }
        }
        scbCallType6.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCallType6 = isChecked
            if (!isChecked && !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 && !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6) {
                XToastUtils.info(R.string.enable_phone_fw_tips)
                SettingUtils.enablePhone = false
                sbEnablePhone.isChecked = false
            }
        }
    }

    //??????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchEnableAppNotify(
        sbEnableAppNotify: SwitchButton, scbCancelAppNotify: SmoothCheckBox, scbNotUserPresent: SmoothCheckBox
    ) {
        val isEnable: Boolean = SettingUtils.enableAppNotify
        sbEnableAppNotify.isChecked = isEnable

        val layoutOptionalAction: LinearLayout = binding!!.layoutOptionalAction
        layoutOptionalAction.visibility = if (isEnable) View.VISIBLE else View.GONE
        //val layoutAppList: LinearLayout = binding!!.layoutAppList
        //layoutAppList.visibility = if (isEnable) View.VISIBLE else View.GONE

        sbEnableAppNotify.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            layoutOptionalAction.visibility = if (isChecked) View.VISIBLE else View.GONE
            //layoutAppList.visibility = if (isChecked) View.VISIBLE else View.GONE
            SettingUtils.enableAppNotify = isChecked
            if (isChecked) {
                //????????????????????????
                XXPermissions.with(this).permission(Permission.BIND_NOTIFICATION_LISTENER_SERVICE).request(OnPermissionCallback { _, allGranted ->
                    if (!allGranted) {
                        SettingUtils.enableAppNotify = false
                        sbEnableAppNotify.isChecked = false
                        XToastUtils.error(R.string.tips_notification_listener)
                        return@OnPermissionCallback
                    }

                    SettingUtils.enableAppNotify = true
                    sbEnableAppNotify.isChecked = true
                    CommonUtils.toggleNotificationListenerService(requireContext())
                })
            }
        }
        scbCancelAppNotify.isChecked = SettingUtils.enableCancelAppNotify
        scbCancelAppNotify.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableCancelAppNotify = isChecked
        }
        scbNotUserPresent.isChecked = SettingUtils.enableNotUserPresent
        scbNotUserPresent.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableNotUserPresent = isChecked
        }
    }

    //??????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchEnableSmsCommand(sbEnableSmsCommand: SwitchButton, etSafePhone: EditText) {
        sbEnableSmsCommand.isChecked = SettingUtils.enableSmsCommand
        etSafePhone.visibility = if (SettingUtils.enableSmsCommand) View.VISIBLE else View.GONE

        sbEnableSmsCommand.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableSmsCommand = isChecked
            etSafePhone.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                //????????????????????????
                XXPermissions.with(this)
                    // ????????????
                    .permission(Permission.RECEIVE_SMS)
                    // ????????????
                    //.permission(Permission.SEND_SMS)
                    // ????????????
                    .permission(Permission.READ_SMS).request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                XToastUtils.info(R.string.toast_granted_all)
                            } else {
                                XToastUtils.info(R.string.toast_granted_part)
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                XToastUtils.info(R.string.toast_denied_never)
                                // ??????????????????????????????????????????????????????????????????
                                XXPermissions.startPermissionActivity(requireContext(), permissions)
                            } else {
                                XToastUtils.info(R.string.toast_denied)
                            }
                            SettingUtils.enableSmsCommand = false
                            sbEnableSmsCommand.isChecked = false
                        }
                    })
            }
        }

        etSafePhone.setText(SettingUtils.smsCommandSafePhone)
        etSafePhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.smsCommandSafePhone = etSafePhone.text.toString().trim().removeSuffix("\n")
            }
        })
    }

    //????????????????????????APP??????
    private fun editExtraAppList(textAppList: EditText) {
        textAppList.setText(SettingUtils.cancelExtraAppNotify)
        textAppList.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.cancelExtraAppNotify = textAppList.text.toString().trim().removeSuffix("\n")
            }
        })
    }

    //??????????????????????????????App??????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchEnableLoadAppList(
        sbEnableLoadAppList: SwitchButton, scbLoadUserApp: SmoothCheckBox, scbLoadSystemApp: SmoothCheckBox
    ) {
        val isEnable: Boolean = SettingUtils.enableLoadAppList
        sbEnableLoadAppList.isChecked = isEnable

        sbEnableLoadAppList.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked && !SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) {
                sbEnableLoadAppList.isChecked = false
                SettingUtils.enableLoadAppList = false
                XToastUtils.error(getString(R.string.load_app_list_toast))
                return@setOnCheckedChangeListener
            }
            SettingUtils.enableLoadAppList = isChecked

            XToastUtils.info(getString(R.string.loading_app_list))
            val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
            WorkManager.getInstance(XUtil.getContext()).enqueue(request)
        }
        scbLoadUserApp.isChecked = SettingUtils.enableLoadUserAppList
        scbLoadUserApp.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableLoadUserAppList = isChecked
            if (SettingUtils.enableLoadAppList && !SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) {
                sbEnableLoadAppList.isChecked = false
                SettingUtils.enableLoadAppList = false
                XToastUtils.error(getString(R.string.load_app_list_toast))
            }
            if (isChecked && SettingUtils.enableLoadAppList && App.UserAppList.isEmpty()) {
                XToastUtils.info(getString(R.string.loading_app_list))
                val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
                WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            } else {
                initAppSpinner()
            }
        }
        scbLoadSystemApp.isChecked = SettingUtils.enableLoadSystemAppList
        scbLoadSystemApp.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableLoadSystemAppList = isChecked
            if (SettingUtils.enableLoadAppList && !SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) {
                sbEnableLoadAppList.isChecked = false
                SettingUtils.enableLoadAppList = false
                XToastUtils.error(getString(R.string.load_app_list_toast))
            }
            if (isChecked && SettingUtils.enableLoadAppList && App.SystemAppList.isEmpty()) {
                XToastUtils.info(getString(R.string.loading_app_list))
                val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
                WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            } else {
                initAppSpinner()
            }
        }
    }

    //????????????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchNetworkStateReceiver(sbNetworkStateReceiver: SwitchButton) {
        sbNetworkStateReceiver.isChecked = SettingUtils.enableNetworkStateReceiver
        sbNetworkStateReceiver.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableNetworkStateReceiver = isChecked
        }
    }

    //????????????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchBatteryReceiver(sbBatteryReceiver: SwitchButton) {
        sbBatteryReceiver.isChecked = SettingUtils.enableBatteryReceiver
        sbBatteryReceiver.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableBatteryReceiver = isChecked
        }
    }

    //?????????????????????
    private fun editBatteryLevelAlarm(
        xrsBatteryLevelAlarm: XRangeSlider, scbBatteryLevelAlarmOnce: SmoothCheckBox
    ) {
        xrsBatteryLevelAlarm.setStartingMinMax(
            SettingUtils.batteryLevelMin, SettingUtils.batteryLevelMax
        )
        xrsBatteryLevelAlarm.setOnRangeSliderListener(object : OnRangeSliderListener {
            override fun onMaxChanged(slider: XRangeSlider, maxValue: Int) {
                //SettingUtils.batteryLevelMin = slider.selectedMin
                SettingUtils.batteryLevelMax = slider.selectedMax
            }

            override fun onMinChanged(slider: XRangeSlider, minValue: Int) {
                SettingUtils.batteryLevelMin = slider.selectedMin
                //SettingUtils.batteryLevelMax = slider.selectedMax
            }
        })

        scbBatteryLevelAlarmOnce.isChecked = SettingUtils.batteryLevelOnce
        scbBatteryLevelAlarmOnce.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.batteryLevelOnce = isChecked
            if (isChecked && 0 == SettingUtils.batteryLevelMin && 0 == SettingUtils.batteryLevelMax) {
                XToastUtils.warning(R.string.tips_battery_level_alarm_once)
            }
        }
    }

    //????????????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchBatteryCron(sbBatteryCron: SwitchButton) {
        sbBatteryCron.isChecked = SettingUtils.enableBatteryCron
        binding!!.layoutBatteryCron.visibility = if (SettingUtils.enableBatteryCron) View.VISIBLE else View.GONE
        sbBatteryCron.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            binding!!.layoutBatteryCron.visibility = if (isChecked) View.VISIBLE else View.GONE
            SettingUtils.enableBatteryCron = isChecked
        }
    }

    //??????????????????????????????
    private fun editBatteryCronTiming(
        etBatteryCronStartTime: EditText, etBatteryCronInterval: EditText
    ) {
        etBatteryCronStartTime.setText(SettingUtils.batteryCronStartTime)
        etBatteryCronStartTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.time = DateUtils.getNowDate()
            val mTimePicker = TimePickerBuilder(context) { date: Date?, _: View? ->
                etBatteryCronStartTime.setText(DateUtils.date2String(date, DateUtils.HHmm.get()))
            }.setType(false, false, false, true, true, false).setTitleText(getString(R.string.time_picker)).setSubmitText(getString(R.string.ok)).setCancelText(getString(R.string.cancel)).setDate(calendar).build()
            mTimePicker.show()
        }

        etBatteryCronInterval.setText(SettingUtils.batteryCronInterval.toString())
        etBatteryCronInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val interval = etBatteryCronInterval.text.toString().trim()
                if (interval.isNotEmpty() && interval.toInt() > 0) {
                    SettingUtils.batteryCronInterval = interval.toInt()
                    //TODO:BatteryReportCronTask
                    //BatteryReportCronTask.getSingleton().updateTimer()
                } else {
                    SettingUtils.batteryCronInterval = 60
                }
            }
        })
    }

    //????????????
    private fun checkWithReboot(
        @SuppressLint("UseSwitchCompatOrMaterialCode") sbWithReboot: SwitchButton, tvAutoStartup: TextView
    ) {
        tvAutoStartup.text = getAutoStartTips()

        //????????????
        val cm = ComponentName(getAppPackageName(), BootReceiver::class.java.name)
        val pm: PackageManager = getPackageManager()
        val state = pm.getComponentEnabledSetting(cm)
        sbWithReboot.isChecked = !(state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
        sbWithReboot.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            try {
                val newState = if (isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                pm.setComponentEnabledSetting(cm, newState, PackageManager.DONT_KILL_APP)
                if (isChecked) startToAutoStartSetting(requireContext())
            } catch (e: Exception) {
                XToastUtils.error(e.message.toString())
            }
        }
    }

    //??????????????????
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun batterySetting(layoutBatterySetting: LinearLayout, sbBatterySetting: SwitchButton) {
        //??????6.0??????????????????????????????
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            layoutBatterySetting.visibility = View.GONE
            return
        }

        try {
            val isIgnoreBatteryOptimization: Boolean = KeepAliveUtils.isIgnoreBatteryOptimization(requireActivity())
            sbBatterySetting.isChecked = isIgnoreBatteryOptimization
            sbBatterySetting.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked && !isIgnoreBatteryOptimization) {
                    KeepAliveUtils.ignoreBatteryOptimization(requireActivity())
                } else if (isChecked) {
                    XToastUtils.info(R.string.isIgnored)
                    sbBatterySetting.isChecked = true
                } else {
                    XToastUtils.info(R.string.isIgnored2)
                    sbBatterySetting.isChecked = isIgnoreBatteryOptimization
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    //?????????????????????????????????
    @SuppressLint("ObsoleteSdkInt,UseSwitchCompatOrMaterialCode")
    fun switchExcludeFromRecents(
        layoutExcludeFromRecents: LinearLayout, sbExcludeFromRecents: SwitchButton
    ) {
        //??????6.0?????????????????????????????????????????????
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            layoutExcludeFromRecents.visibility = View.GONE
            return
        }
        sbExcludeFromRecents.isChecked = SettingUtils.enableExcludeFromRecents
        sbExcludeFromRecents.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableExcludeFromRecents = isChecked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val am = App.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.let {
                    val tasks = it.appTasks
                    if (!tasks.isNullOrEmpty()) {
                        tasks[0].setExcludeFromRecents(true)
                    }
                }
            }
        }
    }

    //??????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchEnableCactus(
        sbEnableCactus: SwitchButton, scbPlaySilenceMusic: SmoothCheckBox, scbOnePixelActivity: SmoothCheckBox
    ) {
        val layoutCactusOptional: LinearLayout = binding!!.layoutCactusOptional
        val isEnable: Boolean = SettingUtils.enableCactus
        sbEnableCactus.isChecked = isEnable
        layoutCactusOptional.visibility = if (isEnable) View.VISIBLE else View.GONE

        sbEnableCactus.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            layoutCactusOptional.visibility = if (isChecked) View.VISIBLE else View.GONE
            SettingUtils.enableCactus = isChecked
            XToastUtils.warning(getString(R.string.need_to_restart))
        }

        scbPlaySilenceMusic.isChecked = SettingUtils.enablePlaySilenceMusic
        scbPlaySilenceMusic.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enablePlaySilenceMusic = isChecked
            XToastUtils.warning(getString(R.string.need_to_restart))
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            binding!!.layoutOnePixelActivity.visibility = View.VISIBLE
        }
        scbOnePixelActivity.isChecked = SettingUtils.enableOnePixelActivity
        scbOnePixelActivity.setOnCheckedChangeListener { _: SmoothCheckBox, isChecked: Boolean ->
            SettingUtils.enableOnePixelActivity = isChecked
            XToastUtils.warning(getString(R.string.need_to_restart))
        }
    }

    //????????????????????????????????????
    private fun editRetryDelayTime(
        etRetryTimes: EditText, etDelayTime: EditText, etTimeout: EditText
    ) {
        etRetryTimes.setText(java.lang.String.valueOf(SettingUtils.requestRetryTimes))
        etRetryTimes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val retryTimes = etRetryTimes.text.toString().trim()
                if (retryTimes.isNotEmpty()) {
                    SettingUtils.requestRetryTimes = retryTimes.toInt()
                } else {
                    etRetryTimes.setText("0")
                    SettingUtils.requestRetryTimes = 0
                }
            }
        })
        etDelayTime.setText(java.lang.String.valueOf(SettingUtils.requestDelayTime))
        etDelayTime.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val delayTime = etDelayTime.text.toString().trim()
                if (delayTime.isNotEmpty()) {
                    SettingUtils.requestDelayTime = delayTime.toInt()
                    if (SettingUtils.requestDelayTime < 1) {
                        etDelayTime.setText("1")
                        XToastUtils.error(R.string.invalid_delay_time)
                    }
                } else {
                    XToastUtils.warning(R.string.invalid_delay_time)
                    etDelayTime.setText("1")
                    SettingUtils.requestDelayTime = 1
                }
            }
        })
        etTimeout.setText(java.lang.String.valueOf(SettingUtils.requestTimeout))
        etTimeout.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val timeout = etTimeout.text.toString().trim()
                if (timeout.isNotEmpty()) {
                    SettingUtils.requestTimeout = timeout.toInt()
                    if (SettingUtils.requestTimeout < 1) {
                        etTimeout.setText("1")
                        XToastUtils.error(R.string.invalid_timeout)
                    }
                } else {
                    XToastUtils.warning(R.string.invalid_timeout)
                    etTimeout.setText("1")
                    SettingUtils.requestTimeout = 1
                }
            }
        })
    }

    //??????????????????
    private fun editAddExtraDeviceMark(etExtraDeviceMark: EditText) {
        etExtraDeviceMark.setText(SettingUtils.extraDeviceMark)
        etExtraDeviceMark.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.extraDeviceMark = etExtraDeviceMark.text.toString().trim()
            }
        })
    }

    //??????SIM1??????
    private fun editAddSubidSim1(etSubidSim1: EditText) {
        etSubidSim1.setText(SettingUtils.subidSim1.toString())
        etSubidSim1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val v = etSubidSim1.text.toString()
                SettingUtils.subidSim1 = if (!TextUtils.isEmpty(v)) {
                    v.toInt()
                } else {
                    1
                }
            }
        })
    }

    //??????SIM2??????
    private fun editAddSubidSim2(etSubidSim2: EditText) {
        etSubidSim2.setText(SettingUtils.subidSim2.toString())
        etSubidSim2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val v = etSubidSim2.text.toString()
                SettingUtils.subidSim2 = if (!TextUtils.isEmpty(v)) {
                    v.toInt()
                } else {
                    2
                }
            }
        })
    }

    //??????SIM1??????
    private fun editAddExtraSim1(etExtraSim1: EditText) {
        etExtraSim1.setText(SettingUtils.extraSim1)
        etExtraSim1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.extraSim1 = etExtraSim1.text.toString().trim()
            }
        })
    }

    //??????SIM2??????
    private fun editAddExtraSim2(etExtraSim2: EditText) {
        etExtraSim2.setText(SettingUtils.extraSim2)
        etExtraSim2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.extraSim2 = etExtraSim2.text.toString().trim()
            }
        })
    }

    //??????????????????
    private fun editNotifyContent(etNotifyContent: EditText) {
        etNotifyContent.setText(SettingUtils.notifyContent)
        etNotifyContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.notifyContent = etNotifyContent.text.toString().trim()
                LiveEventBus.get(EVENT_UPDATE_NOTIFY, String::class.java).post(SettingUtils.notifyContent)
            }
        })
    }

    //????????????????????????????????????
    @SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
    fun switchSmsTemplate(sb_sms_template: SwitchButton) {
        val isOn: Boolean = SettingUtils.enableSmsTemplate
        sb_sms_template.isChecked = isOn
        val layoutSmsTemplate: LinearLayout = binding!!.layoutSmsTemplate
        layoutSmsTemplate.visibility = if (isOn) View.VISIBLE else View.GONE
        val etSmsTemplate: EditText = binding!!.etSmsTemplate
        sb_sms_template.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            layoutSmsTemplate.visibility = if (isChecked) View.VISIBLE else View.GONE
            SettingUtils.enableSmsTemplate = isChecked
            if (!isChecked) {
                etSmsTemplate.setText(
                    """
                    ${getString(R.string.tag_from)}
                    ${getString(R.string.tag_sms)}
                    ${getString(R.string.tag_card_slot)}
                    SubId???${getString(R.string.tag_card_subid)}
                    ${getString(R.string.tag_receive_time)}
                    ${getString(R.string.tag_device_name)}
                    """.trimIndent()
                )
            }
        }
    }

    //????????????????????????
    private fun editSmsTemplate(textSmsTemplate: EditText) {
        textSmsTemplate.setText(SettingUtils.smsTemplate)
        textSmsTemplate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.smsTemplate = textSmsTemplate.text.toString().trim()
            }
        })
    }

    //??????????????????
    private fun switchHelpTip(@SuppressLint("UseSwitchCompatOrMaterialCode") switchHelpTip: SwitchButton) {
        switchHelpTip.isChecked = SettingUtils.enableHelpTip
        switchHelpTip.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableHelpTip = isChecked
        }
    }

    //??????????????????
    private fun switchDirectlyToClient(@SuppressLint("UseSwitchCompatOrMaterialCode") switchDirectlyToClient: SwitchButton) {
        switchDirectlyToClient.isChecked = SettingUtils.enablePureClientMode
        switchDirectlyToClient.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enablePureClientMode = isChecked
            if (isChecked) {
                MaterialDialog.Builder(requireContext()).content(getString(R.string.enabling_pure_client_mode)).positiveText(R.string.lab_yes).onPositive { _: MaterialDialog?, _: DialogAction? ->
                    XUtil.exitApp()
                }.negativeText(R.string.lab_no).show()
            }
        }
    }

    //????????????????????????
    private fun getAutoStartTips(): String {
        return when (Build.BRAND.lowercase(Locale.ROOT)) {
            "huawei" -> getString(R.string.auto_start_huawei)
            "honor" -> getString(R.string.auto_start_honor)
            "xiaomi" -> getString(R.string.auto_start_xiaomi)
            "oppo" -> getString(R.string.auto_start_oppo)
            "vivo" -> getString(R.string.auto_start_vivo)
            "meizu" -> getString(R.string.auto_start_meizu)
            "samsung" -> getString(R.string.auto_start_samsung)
            "letv" -> getString(R.string.auto_start_letv)
            "smartisan" -> getString(R.string.auto_start_smartisan)
            else -> getString(R.string.auto_start_unknown)
        }
    }

    //Intent?????????[?????????]??????????????????????????????????????????
    private val hashMap = object : HashMap<String?, List<String?>?>() {
        init {
            put(
                "Xiaomi", listOf(
                    "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",  //MIUI10_9.8.1(9.0)
                    "com.miui.securitycenter"
                )
            )
            put(
                "samsung", listOf(
                    "com.samsung.android.sm_cn/com.samsung.android.sm.ui.ram.AutoRunActivity", "com.samsung.android.sm_cn/com.samsung.android.sm.ui.appmanagement.AppManagementActivity", "com.samsung.android.sm_cn/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity", "com.samsung.android.sm_cn/.ui.ram.RamActivity", "com.samsung.android.sm_cn/.app.dashboard.SmartManagerDashBoardActivity", "com.samsung.android.sm/com.samsung.android.sm.ui.ram.AutoRunActivity", "com.samsung.android.sm/com.samsung.android.sm.ui.appmanagement.AppManagementActivity", "com.samsung.android.sm/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity", "com.samsung.android.sm/.ui.ram.RamActivity", "com.samsung.android.sm/.app.dashboard.SmartManagerDashBoardActivity", "com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity", "com.samsung.android.sm_cn", "com.samsung.android.sm"
                )
            )
            put(
                "HUAWEI", listOf(
                    "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",  //EMUI9.1.0(??????,9.0)
                    "com.huawei.systemmanager/.appcontrol.activity.StartupAppControlActivity", "com.huawei.systemmanager/.optimize.process.ProtectActivity", "com.huawei.systemmanager/.optimize.bootstart.BootStartActivity", "com.huawei.systemmanager" //???????????????????????????, ???????????????????????????????????????????????????ROM???????????? ???????????????????????????????????????/???????????? ??????.
                )
            )
            put(
                "vivo", listOf(
                    "com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager", "com.iqoo.secure/.safeguard.PurviewTabActivity", "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",  //"com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity", //???????????????, ???????????????
                    "com.iqoo.secure", "com.vivo.permissionmanager"
                )
            )
            put(
                "Meizu", listOf(
                    "com.meizu.safe/.permission.SmartBGActivity",  //Flyme7.3.0(7.1.2)
                    "com.meizu.safe/.permission.PermissionMainActivity",  //?????????
                    "com.meizu.safe"
                )
            )
            put(
                "OPPO", listOf(
                    "com.coloros.safecenter/.startupapp.StartupAppListActivity", "com.coloros.safecenter/.permission.startup.StartupAppListActivity", "com.oppo.safe/.permission.startup.StartupAppListActivity", "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity", "com.coloros.safecenter/com.coloros.privacypermissionsentry.PermissionTopActivity", "com.coloros.safecenter", "com.oppo.safe", "com.coloros.oppoguardelf"
                )
            )
            put(
                "oneplus", listOf(
                    "com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity", "com.oneplus.security"
                )
            )
            put(
                "letv", listOf(
                    "com.letv.android.letvsafe/.AutobootManageActivity", "com.letv.android.letvsafe/.BackgroundAppManageActivity",  //????????????
                    "com.letv.android.letvsafe"
                )
            )
            put(
                "zte", listOf(
                    "com.zte.heartyservice/.autorun.AppAutoRunManager", "com.zte.heartyservice"
                )
            )

            //??????
            put(
                "F", listOf(
                    "com.gionee.softmanager/.MainActivity", "com.gionee.softmanager"
                )
            )

            //??????????????????(?????????????????????)
            put(
                "smartisanos", listOf(
                    "com.smartisanos.security/.invokeHistory.InvokeHistoryActivity", "com.smartisanos.security"
                )
            )

            //360
            put(
                "360", listOf(
                    "com.yulong.android.coolsafe/.ui.activity.autorun.AutoRunListActivity", "com.yulong.android.coolsafe"
                )
            )

            //360
            put(
                "ulong", listOf(
                    "com.yulong.android.coolsafe/.ui.activity.autorun.AutoRunListActivity", "com.yulong.android.coolsafe"
                )
            )

            //??????
            put(
                "coolpad" /*?????????????????????????????????*/, listOf(
                    "com.yulong.android.security/com.yulong.android.seccenter.tabbarmain", "com.yulong.android.security"
                )
            )

            //??????
            put(
                "lenovo" /*?????????????????????????????????*/, listOf(
                    "com.lenovo.security/.purebackground.PureBackgroundActivity", "com.lenovo.security"
                )
            )
            put(
                "htc" /*?????????????????????????????????*/, listOf(
                    "com.htc.pitroad/.landingpage.activity.LandingPageActivity", "com.htc.pitroad"
                )
            )

            //??????
            put(
                "asus" /*?????????????????????????????????*/, listOf(
                    "com.asus.mobilemanager/.MainActivity", "com.asus.mobilemanager"
                )
            )
        }
    }

    //?????????????????????
    private fun startToAutoStartSetting(context: Context) {
        Log.e("Util", "******************The current phone model is:" + Build.MANUFACTURER)
        val entries: MutableSet<MutableMap.MutableEntry<String?, List<String?>?>> = hashMap.entries
        var has = false
        for ((manufacturer, actCompatList) in entries) {
            if (Build.MANUFACTURER.equals(manufacturer, ignoreCase = true)) {
                if (actCompatList != null) {
                    for (act in actCompatList) {
                        try {
                            var intent: Intent?
                            if (act?.contains("/") == true) {
                                intent = Intent()
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val componentName = ComponentName.unflattenFromString(act)
                                intent.component = componentName
                            } else {
                                //?????????? ????????????????????????????????????... ??????????????????????????? ???????????????????????????????????????????????????????????????????????????app
                                //????????????????????????????????????????????????/????????????
                                intent = act?.let { context.packageManager.getLaunchIntentForPackage(it) }
                            }
                            context.startActivity(intent)
                            has = true
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        if (!has) {
            XToastUtils.info(R.string.tips_compatible_solution)
            try {
                val intent = Intent()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    //?????????APP????????????
    private fun initAppSpinner() {

        //??????????????????????????????App????????????????????????????????????APP?????????
        if (!SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) return

        if (App.UserAppList.isEmpty() && App.SystemAppList.isEmpty()) {
            //XToastUtils.info(getString(R.string.loading_app_list))
            val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
            WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            return
        }

        appListSpinnerList.clear()
        if (SettingUtils.enableLoadUserAppList) {
            for (appInfo in App.UserAppList) {
                if (TextUtils.isEmpty(appInfo.packageName)) continue
                appListSpinnerList.add(AppListAdapterItem(appInfo.name, appInfo.icon, appInfo.packageName))
            }
        }
        if (SettingUtils.enableLoadSystemAppList) {
            for (appInfo in App.SystemAppList) {
                if (TextUtils.isEmpty(appInfo.packageName)) continue
                appListSpinnerList.add(AppListAdapterItem(appInfo.name, appInfo.icon, appInfo.packageName))
            }
        }

        //?????????????????????????????????
        if (appListSpinnerList.isEmpty()) return

        appListSpinnerAdapter = AppListSpinnerAdapter(appListSpinnerList).setIsFilterKey(true).setFilterColor("#EF5362").setBackgroundSelector(R.drawable.selector_custom_spinner_bg)
        binding!!.spApp.setAdapter(appListSpinnerAdapter)
        binding!!.spApp.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
            try {
                val appInfo = appListSpinnerAdapter.getItemSource(position) as AppListAdapterItem
                CommonUtils.insertOrReplaceText2Cursor(binding!!.etAppList, appInfo.packageName.toString() + "\n")
            } catch (e: Exception) {
                XToastUtils.error(e.message.toString())
            }
        }
        binding!!.layoutSpApp.visibility = View.VISIBLE

    }

}