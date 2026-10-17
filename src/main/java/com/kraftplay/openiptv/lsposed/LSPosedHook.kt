package com.kraftplay.openiptv.lsposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LSPosedHook : IXposedHookLoadPackage {

    companion object {
        const val TARGET_APP = "com.kraftplay.openiptv"
        const val GEARHEAD_PKG = "com.google.android.projection.gearhead"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == TARGET_APP) {
            markSelfActive(lpparam)
            return
        }

        if (lpparam.packageName == GEARHEAD_PKG) {
            XposedBridge.log("OpenIPTV: Hooking Android Auto Gearhead")
            hookAndroidAuto(lpparam)
        }
    }

    private fun markSelfActive(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val statusClass = XposedHelpers.findClass(
                "com.kraftplay.openiptv.lsposed.LSPosedStatus",
                lpparam.classLoader
            )
            XposedHelpers.setStaticBooleanField(statusClass, "isActive", true)
            XposedBridge.log("OpenIPTV: LSPosed module activated in-app")
        } catch (e: Throwable) {
            XposedBridge.log("OpenIPTV: Error setting active state: ${e.message}")
        }
    }

    private fun hookAndroidAuto(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.pm.PackageManager",
                lpparam.classLoader,
                "getPackageInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val args = param.args as? Array<*>
                        val pkgName = if (args != null && args.isNotEmpty()) args[0] as? String else null
                        if (pkgName == TARGET_APP) {
                            XposedBridge.log("OpenIPTV: Bypassed AA package check for $pkgName")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("OpenIPTV: Error hooking PackageManager: ${e.message}")
        }

        val classNamesToHook = arrayOf(
            "com.google.android.apps.auto.carservice.v2.f",
            "com.google.android.apps.auto.carservice.service.p",
            "com.google.android.apps.auto.components.graphics.surface.SurfaceControl",
            "com.google.android.apps.auto.carservice.carapp.CarAppValidation"
        )

        for (className in classNamesToHook) {
            try {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
                val methods = clazz.declaredMethods
                for (method in methods) {
                    if (method.returnType == java.lang.Boolean.TYPE) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val args = param.args as? Array<*>
                                if (args != null) {
                                    for (arg in args) {
                                        if (arg == TARGET_APP || arg?.toString()?.contains(TARGET_APP) == true) {
                                            param.result = true
                                            break
                                        }
                                    }
                                }
                            }
                        })
                    }
                }
            } catch (e: Throwable) {
                // Ignore version-specific missing classes
            }
        }
    }
}
