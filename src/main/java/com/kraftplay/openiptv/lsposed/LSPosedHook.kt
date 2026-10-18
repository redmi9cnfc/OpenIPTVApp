package com.kraftplay.openiptv.lsposed

import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Fermata-style Android Auto unlocker для OpenIPTV.
 *
 * Как это работает (тот же приём, что в Fermata: fermata/src/auto/.../Xposed.java):
 * современный Android Auto (com.google.android.projection.gearhead) скрывает из
 * автомобильного лаунчера все приложения, установленные НЕ из Google Play, —
 * для этого он спрашивает у PackageManager источник установки пакета
 * (InstallSourceInfo.getInitiatingPackageName() / getInstallingPackageName() /
 * IPackageManager.getInstallerPackageName()).
 *
 * Здесь, внутри процесса Gearhead, эти методы подменяются так, что любое
 * приложение выглядит установленным из Play Store (com.android.vending).
 * После этого OpenIPTV (его media-сервис с meta-data
 * com.google.android.gms.car.application) появляется в списке приложений AA.
 */
class LSPosedHook : IXposedHookLoadPackage {

    companion object {
        const val TARGET_APP = "com.kraftplay.openiptv"
        const val GEARHEAD_PKG = "com.google.android.projection.gearhead"
        const val GPLAY_PKG = "com.android.vending"

        // Binder-прокси PackageManager (fallback для API < 30)
        private const val IPM_STUB_PROXY = "android.content.pm.IPackageManager.Stub.Proxy"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            TARGET_APP -> markSelfActive(lpparam)
            GEARHEAD_PKG -> {
                XposedBridge.log("OpenIPTV: hooking Gearhead, spoofing install source -> $GPLAY_PKG")
                hookInstallSource(lpparam)
            }
        }
    }

    /** Помечает в самом приложении, что модуль активен (индикатор в UI). */
    private fun markSelfActive(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val statusClass = XposedHelpers.findClass(
                "$TARGET_APP.lsposed.LSPosedStatus",
                lpparam.classLoader
            )
            XposedHelpers.setStaticBooleanField(statusClass, "isActive", true)
            XposedBridge.log("OpenIPTV: LSPosed module activated in-app")
        } catch (t: Throwable) {
            XposedBridge.log("OpenIPTV: markSelfActive failed: ${t.message}")
        }
    }

    /**
     * Подмена источника установки внутри процесса Android Auto.
     * Хукаются фреймворк-классы, поэтому ссылки на них — только строками,
     * чтобы не было проблем верификации классов на старых Android.
     */
    private fun hookInstallSource(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Gearhead читает InstallSourceInfo
            spoofVending("android.content.pm.InstallSourceInfo", "getInitiatingPackageName", lpparam.classLoader)
            spoofVending("android.content.pm.InstallSourceInfo", "getInstallingPackageName", lpparam.classLoader)
            spoofVending("android.content.pm.InstallSourceInfo", "getOriginatingPackageName", lpparam.classLoader)
        }

        // API < 30 и старые/альтернативные пути: прямой вызов binder-прокси.
        // На новых версиях метод депрекейтнут, но хук безвреден — держим как fallback.
        try {
            XposedHelpers.findAndHookMethod(
                IPM_STUB_PROXY, lpparam.classLoader, "getInstallerPackageName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = GPLAY_PKG
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("OpenIPTV: getInstallerPackageName hook skipped: ${t.message}")
        }
    }

    private fun spoofVending(className: String, methodName: String, classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                className, classLoader, methodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = GPLAY_PKG
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("OpenIPTV: hook $className.$methodName failed: ${t.message}")
        }
    }
}
