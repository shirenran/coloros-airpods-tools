import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class Sign {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) { ks.load(in, a[1].toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey("key", a[1].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate("key");
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("key", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
            .setInputApk(new File(a[2])).setOutputApk(new File(a[3]))
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false)
            .setMinSdkVersion(30).build().sign();
        System.out.println("signed " + a[3]);
    }
}
