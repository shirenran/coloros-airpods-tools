/*
 * 调试工具：root 下调用 ColorOS 的 android.bluetooth.OplusBluetoothDevice
 *
 * 编译（先运行一次 ../build.sh 下载 r8.jar）：
 *       javac --release 8 -d cls Anc.java
 *       java -cp ../build-tools/r8.jar com.android.tools.r8.D8 --min-api 26 --output . cls/Anc.class
 *       adb push classes.dex /data/local/tmp/anc.dex
 * 运行：su -c "CLASSPATH=/data/local/tmp/anc.dex app_process /system/bin Anc <耳机MAC> info"
 *       ... Anc <MAC> get 4        读降噪模式（1关闭 2降噪 3通透 4自适应）
 *       ... Anc <MAC> set 4 <值>   切换降噪模式
 */
import java.lang.reflect.Method;

public class Anc {
    public static void main(String[] args) throws Exception {
        if (Class.forName("android.os.Looper").getMethod("myLooper").invoke(null) == null) {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
        }
        Class<?> ba = Class.forName("android.bluetooth.BluetoothAdapter");
        Class<?> at = Class.forName("android.app.ActivityThread");
        at.getMethod("initializeMainlineModules").invoke(null);
        Object thread = at.getMethod("systemMain").invoke(null);
        Object ctx = at.getMethod("getSystemContext").invoke(thread);
        Object bm = Class.forName("android.content.Context").getMethod("getSystemService", String.class).invoke(ctx, "bluetooth");
        Object adapter = bm.getClass().getMethod("getAdapter").invoke(bm);
        System.out.println("adapter=" + adapter);
        Object dev = ba.getMethod("getRemoteDevice", String.class).invoke(adapter, args[0]);
        Class<?> bd = Class.forName("android.bluetooth.BluetoothDevice");
        Class<?> obd = Class.forName("android.bluetooth.OplusBluetoothDevice");
        Object o = obd.getConstructor(bd).newInstance(dev);
        String cmd = args.length > 1 ? args[1] : "info";
        if (cmd.equals("info")) {
            for (Method m : obd.getDeclaredMethods()) {
                String n = m.getName();
                if (n.toLowerCase().contains("feature")) System.out.println("method: " + m);
            }
            for (Method m : obd.getDeclaredMethods()) {
                if (m.getName().equals("getDeviceExtendFeatureMask") && m.getParameterCount() == 0) {
                    int mask = (Integer) m.invoke(o);
                    System.out.println("mask=" + mask + " (0x" + Integer.toHexString(mask) + ") offBit512=" + ((mask & 512) != 0));
                }
            }
        } else if (cmd.equals("get")) {
            Method m = obd.getMethod("getExtendFeatureStatus", int.class);
            System.out.println("status(" + args[2] + ")=" + m.invoke(o, Integer.parseInt(args[2])));
        } else if (cmd.equals("set")) {
            Method m = obd.getMethod("setExtendFeatureStatus", int.class, int.class);
            System.out.println("set(" + args[2] + "," + args[3] + ")=" + m.invoke(o, Integer.parseInt(args[2]), Integer.parseInt(args[3])));
        }
    }
}
