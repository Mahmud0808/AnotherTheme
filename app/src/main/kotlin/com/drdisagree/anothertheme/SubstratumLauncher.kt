@file:Suppress("ConstantConditionIf")

package com.drdisagree.anothertheme

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.TransactionDetails
import com.github.javiersantos.piracychecker.*
import com.github.javiersantos.piracychecker.enums.InstallerID
import com.github.javiersantos.piracychecker.utils.apkSignature
import com.drdisagree.anothertheme.AdvancedConstants.ORGANIZATION_THEME_SYSTEMS
import com.drdisagree.anothertheme.AdvancedConstants.OTHER_THEME_SYSTEMS
import com.drdisagree.anothertheme.ThemeFunctions.checkApprovedSignature
import com.drdisagree.anothertheme.ThemeFunctions.getSelfSignature
import com.drdisagree.anothertheme.ThemeFunctions.getSelfVerifiedPirateTools
import com.drdisagree.anothertheme.ThemeFunctions.isCallingPackageAllowed

/**
 * NOTE TO THEMERS
 *
 * This class is a TEMPLATE of how you should be launching themes. As long as you keep the structure
 * of launching themes the same, you can avoid easy script crackers by changing how
 * functions/methods are coded, as well as boolean variable placement.
 *
 * The more you play with this the harder it would be to decompile and crack!
 */

class SubstratumLauncher : Activity(), BillingProcessor.IBillingHandler {
    private var bp: BillingProcessor? = null

    private val debug = false
    private val tag = "SubstratumThemeReport"
    private val substratumIntentData = "projekt.substratum.THEME"
    private val getKeysIntent = "projekt.substratum.GET_KEYS"
    private val receiveKeysIntent = "projekt.substratum.RECEIVE_KEYS"

    private val themePiracyCheck by lazy {
        if (BuildConfig.ENABLE_APP_BLACKLIST_CHECK) {
            getSelfVerifiedPirateTools(applicationContext)
        } else {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*TODO replace the null with license key from play console (developement tools > service & APIs)*/
        bp = BillingProcessor(this, "ffffffffffffff", this)

        /* STEP 1: Block hijackers */
        val caller = callingActivity!!.packageName
        val organizationsSystem = ORGANIZATION_THEME_SYSTEMS.contains(caller)
        val supportedSystem = organizationsSystem || OTHER_THEME_SYSTEMS.contains(caller)
        if (!BuildConfig.SUPPORTS_THIRD_PARTY_SYSTEMS && !supportedSystem) {
            Log.e(tag, "This theme does not support the launching theme system. [HIJACK] ($caller)")
            Toast.makeText(this,
                    String.format(getString(R.string.unauthorized_theme_client_hijack), caller),
                    Toast.LENGTH_LONG).show()
            finish()
        }
        if (debug) {
            Log.d(tag, "'$caller' has been authorized to launch this theme. (Phase 1)")
        }

        /* STEP 2: Ensure that our support is added where it belongs */
        val action = intent.action
//        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        var verified = false
        if ((action == substratumIntentData) or (action == getKeysIntent)) {
            // Assume this called from organization's app
            if (organizationsSystem) {
                verified = when {
                    BuildConfig.ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS -> true
                    else -> checkApprovedSignature(this, caller)
                }
            }
        } else {
            OTHER_THEME_SYSTEMS
                    .filter { action?.startsWith(prefix = it, ignoreCase = true) ?: false }
                    .forEach { _ -> verified = true }
        }
        if (!verified) {
            Log.e(tag, "This theme does not support the launching theme system. ($action)")
            Toast.makeText(this, R.string.unauthorized_theme_client, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (debug) {
            Log.d(tag, "'$action' has been authorized to launch this theme. (Phase 2)")
        }

        showDialog()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!bp!!.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onProductPurchased(productId: String, details: TransactionDetails?) {

    }

    override fun onPurchaseHistoryRestored() {

    }

    override fun onBillingError(errorCode: Int, error: Throwable?) {

    }

    override fun onBillingInitialized() {

    }

    public override fun onDestroy() {
        if (bp != null) {
            bp!!.release()
        }
        super.onDestroy()
    }

    private fun startAntiPiracyCheck() {
        if (BuildConfig.BASE_64_LICENSE_KEY.isEmpty() && debug && !BuildConfig.DEBUG) {
            Log.e(tag, apkSignature)
        }

        if (!themePiracyCheck) {
            piracyChecker {
                if (BuildConfig.ENFORCE_GOOGLE_PLAY_INSTALL) {
                    enableInstallerId(InstallerID.GOOGLE_PLAY)
                }
                if (BuildConfig.BASE_64_LICENSE_KEY.isNotEmpty()) {
                    enableGooglePlayLicensing(BuildConfig.BASE_64_LICENSE_KEY)
                }
                if (BuildConfig.APK_SIGNATURE_PRODUCTION.isNotEmpty()) {
                    enableSigningCertificate(BuildConfig.APK_SIGNATURE_PRODUCTION)
                }
                callback {
                    allow {
                        val returnIntent = if (intent.action == getKeysIntent) {
                            Intent(receiveKeysIntent)
                        } else {
                            Intent()
                        }

                        val themeName = getString(R.string.ThemeName)
                        val themeAuthor = getString(R.string.ThemeAuthor)
                        val themePid = packageName
                        returnIntent.putExtra("theme_name", themeName)
                        returnIntent.putExtra("theme_author", themeAuthor)
                        returnIntent.putExtra("theme_pid", themePid)
                        returnIntent.putExtra("theme_debug", BuildConfig.DEBUG)
                        returnIntent.putExtra("theme_piracy_check", themePiracyCheck)
                        returnIntent.putExtra("encryption_key", BuildConfig.DECRYPTION_KEY)
                        returnIntent.putExtra("iv_encrypt_key", BuildConfig.IV_KEY)

                        val callingPackage = intent.getStringExtra("calling_package_name")
                        if (!isCallingPackageAllowed(callingPackage)) {
                            finish()
                        } else {
                            returnIntent.`package` = callingPackage
                        }

                        if (intent.action == substratumIntentData) {
                            setResult(getSelfSignature(applicationContext), returnIntent)
                        } else if (intent.action == getKeysIntent) {
                            returnIntent.action = receiveKeysIntent
                            sendBroadcast(returnIntent)
                        }
                        destroy()
                        finish()
                    }
                    doNotAllow { _, _ ->
                        val parse = String.format(
                                getString(R.string.toast_unlicensed),
                                getString(R.string.ThemeName))
                        Toast.makeText(this@SubstratumLauncher, parse, Toast.LENGTH_SHORT).show()
                        destroy()
                        finish()
                    }
                    onError { error ->
                        Toast.makeText(this@SubstratumLauncher, error.toString(), Toast.LENGTH_LONG)
                                .show()
                        destroy()
                        finish()
                    }
                }
            }.start()
        } else {
            Toast.makeText(this, R.string.unauthorized, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("InflateParams")
    private fun showDialog() {

        val alertDialog = AlertDialog.Builder(this, R.style.DialogStyle)
                .setCancelable(false)
        val view = LayoutInflater.from(this).inflate(R.layout.custom_dialog, null)
        val title = view.findViewById(R.id.title) as TextView
        title.text = getString(R.string.launch_dialog_title)

        /*Buttons*/
        val telegram = view.findViewById(R.id.telegram) as ImageButton
        telegram.setImageResource(R.drawable.ic_telegram)
        telegram.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.link_telegram))))
        }
        val support = view.findViewById(R.id.support) as ImageButton
        support.setImageResource(R.drawable.ic_support)
        support.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.link_support))))
        }
        val cont = view.findViewById(R.id.button_continue) as Button
        cont.setOnClickListener {
            startAntiPiracyCheck()
        }
        /*Checkbox*/
        val myCheckBox = view.findViewById(R.id.myCheckBox) as CheckBox
        myCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                storeDialogStatus(true)
            } else {
                storeDialogStatus(false)
            }
        }

        alertDialog.setView(view)

        if (getDialogStatus()) {
            startAntiPiracyCheck()
        } else {
            alertDialog.show()
        }

    }

    private fun storeDialogStatus(isChecked: Boolean) {
        val mSharedPreferences = getSharedPreferences("dialog", Context.MODE_PRIVATE)
        val mEditor = mSharedPreferences.edit()
        mEditor.putBoolean("show_dialog_" + BuildConfig.VERSION_CODE, isChecked)
        mEditor.apply()
    }

    private fun getDialogStatus(): Boolean {
        val mSharedPreferences = getSharedPreferences("dialog", Context.MODE_PRIVATE)
        return mSharedPreferences.getBoolean("show_dialog_" + BuildConfig.VERSION_CODE, false)
    }
}