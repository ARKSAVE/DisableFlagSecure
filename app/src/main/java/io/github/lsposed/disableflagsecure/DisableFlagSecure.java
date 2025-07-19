//1 
//package //io.github.lsposed.disableflagsecure;
package io.github.lsposed.disableflagsecure;

import android.annotation.SuppressLint;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class DisableFlagSecure extends XposedModule {
    private static XposedModule module;
    private static Field captureSecureLayersField;

    public DisableFlagSecure(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        module = this;
    }

    @Override
    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();

        try {
            hookWindowState(classLoader);
        } catch (Throwable t) {
            log("hook WindowState failed", t);
        }

        try {
            hookScreenCapture(classLoader);
        } catch (Throwable t) {
            log("hook ScreenCapture failed", t);
        }
    }

    private void hookWindowState(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var windowStateClazz = classLoader.loadClass("com.android.server.wm.WindowState");
        var isSecureLockedMethod = windowStateClazz.getDeclaredMethod("isSecureLocked");
        hook(isSecureLockedMethod, SecureEnforceHooker.class);
    }

    private void hookScreenCapture(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException {
        var screenCaptureClazz = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
                classLoader.loadClass("android.window.ScreenCapture") :
                SurfaceControl.class;
        var captureArgsClazz = classLoader.loadClass(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
                "android.window.ScreenCapture$CaptureArgs" :
                "android.view.SurfaceControl$CaptureArgs");
        captureSecureLayersField = captureArgsClazz.getDeclaredField("mCaptureSecureLayers");
        captureSecureLayersField.setAccessible(true);
        hookMethods(screenCaptureClazz, SecureLayerHooker.class, "nativeCaptureDisplay", "nativeCaptureLayers");
    }

    private void hookMethods(Class<?> clazz, Class<? extends Hooker> hooker, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(method -> hook(method, hooker));
    }

    @XposedHooker
    private static class SecureEnforceHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            callback.returnAndSkip(true); // 强制返回 true，启用 FLAG_SECURE
        }
    }

    @XposedHooker
    private static class SecureLayerHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            var captureArgs = callback.getArgs()[0];
            try {
                captureSecureLayersField.set(captureArgs, false); // 禁用捕获安全层
            } catch (IllegalAccessException t) {
                module.log("SecureLayerHooker failed", t);
            }
        }
    }
}
