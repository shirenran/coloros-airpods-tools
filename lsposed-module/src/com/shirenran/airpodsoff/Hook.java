package com.shirenran.airpodsoff;

import java.io.OutputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ColorOS 系统蓝牙把 AirPods Pro 2/3 的功能位写死为不支持“关闭”，
 * 且 AirPods 只有收到“允许关闭”控制指令后才接受关闭模式。
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "AirPodsOff: ";
    private static final String MANAGER = "com.oplus.bluetooth.feature.airpods.AirPodsManager";
    private static final int FEATURE_NOISE_MODE = 4;
    private static final int FEATURE_NOISE_OFF = 512;
    private static final int FEATURE_NOISE_TRANSPARENT = 1024;
    private static final int NOISE_MODE_OFF = 1;
    // AAP 控制指令 0x34：允许关闭模式
    private static final byte[] ALLOW_OFF = {4, 0, 4, 0, 9, 0, 0x34, 1, 0, 0, 0};

    private static volatile boolean hooked;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.android.bluetooth".equals(lp.packageName)) {
            return;
        }
        XposedBridge.log(TAG + "loaded in " + lp.processName);
        tryHook(lp.classLoader);
        // AirPodsManager 所在 jar 由 OplusBTFactory 通过 addDexPath 动态加入
        XposedBridge.hookAllMethods(XposedHelpers.findClass("dalvik.system.BaseDexClassLoader", null), "addDexPath", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                if (!hooked && String.valueOf(p.args[0]).contains("oplus_bluetooth_common_ext")) {
                    tryHook((ClassLoader) p.thisObject);
                }
            }
        });
    }

    private static synchronized void tryHook(ClassLoader cl) {
        if (hooked) {
            return;
        }
        Class<?> manager = XposedHelpers.findClassIfExists(MANAGER, cl);
        if (manager == null) {
            return;
        }
        hooked = true;

        XposedBridge.hookAllMethods(manager, "getDeviceExtendFeatureMask", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                Object r = p.getResult();
                if (r instanceof Integer) {
                    int mask = (Integer) r;
                    if (mask > 0 && (mask & FEATURE_NOISE_MODE) != 0 && (mask & FEATURE_NOISE_TRANSPARENT) != 0) {
                        p.setResult(mask | FEATURE_NOISE_OFF);
                    }
                }
            }
        });

        XposedBridge.hookAllMethods(manager, "setNoiseMode", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length == 2 && Integer.valueOf(NOISE_MODE_OFF).equals(p.args[1])) {
                    Object out = XposedHelpers.callMethod(p.thisObject, "getDeviceOutputStream", p.args[0]);
                    sendAllowOff(p.thisObject, out, "before setNoiseMode(off)");
                }
            }
        });

        // 连接握手完成后补发，让耳机柄按压循环里的“关闭”也能生效
        for (String name : new String[] {"lambda$handleAirPodsSocket$3", "lambda$handleAirPodsSocket$10"}) {
            try {
                XposedBridge.hookAllMethods(manager, name, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        sendAllowOff(p.thisObject, p.args[0], "after handshake");
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook " + name + " failed: " + t);
            }
        }
        XposedBridge.log(TAG + "hooked " + MANAGER);
    }

    private static void sendAllowOff(Object manager, Object out, String reason) {
        if (!(out instanceof OutputStream)) {
            XposedBridge.log(TAG + "no output stream, " + reason);
            return;
        }
        try {
            XposedHelpers.callMethod(manager, "lambda$handleAirPodsSocket$9", out, ALLOW_OFF);
            XposedBridge.log(TAG + "sent allow-off, " + reason);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "send allow-off failed, " + reason + ": " + t);
        }
    }
}
