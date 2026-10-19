import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Local-only signing. Never creates an identity or uploads private material. */
public final class SignAndroidRelease {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("input.apk output.apk identity.p12 alias");
        Path input=Path.of(args[0]), output=Path.of(args[1]);
        if (!Files.isRegularFile(input) || Files.exists(output)) throw new IllegalArgumentException("Input must exist and output must be new");
        String secret=System.getenv("CAMERA_STATS_KEYSTORE_PASSWORD");
        if (secret==null || secret.isEmpty()) throw new IllegalArgumentException("Keychain password handoff is required");
        char[] password=secret.toCharArray();KeyStore store=KeyStore.getInstance("PKCS12");
        PrivateKey key;List<X509Certificate> chain=new ArrayList<>();
        try (var stream=Files.newInputStream(Path.of(args[2]))) {
            store.load(stream,password);key=(PrivateKey)store.getKey(args[3],password);
            for (var certificate:store.getCertificateChain(args[3])) {var cert=(X509Certificate)certificate;cert.checkValidity();chain.add(cert);}
        } finally {Arrays.fill(password,'\0');}
        if (key==null || chain.isEmpty()) throw new IllegalArgumentException("Signing identity is unavailable");
        var signer=new ApkSigner.SignerConfig.Builder(args[3],key,chain).build();
        Path temp=Files.createTempFile(output.toAbsolutePath().getParent(),"camera-stats-sign-",".apk");
        try {
            new ApkSigner.Builder(List.of(signer)).setInputApk(input.toFile()).setOutputApk(temp.toFile()).setMinSdkVersion(26)
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false).build().sign();
            var result=new ApkVerifier.Builder(temp.toFile()).setMinCheckedPlatformVersion(26).build().verify();
            if (!result.isVerified() || !result.isVerifiedUsingV2Scheme() || !result.isVerifiedUsingV3Scheme() || result.getSignerCertificates().size()!=1)
                throw new IllegalStateException("Signed APK verification failed: "+result.getErrors());
            if (!Arrays.equals(result.getSignerCertificates().get(0).getEncoded(),chain.get(0).getEncoded())) throw new IllegalStateException("Unexpected signer");
            Files.move(temp,output,StandardCopyOption.ATOMIC_MOVE);
            System.out.println("PASS: APK signatures verified (v2/v3), one expected signer, minimum API 26.");
            System.out.println("Public certificate SHA-256: "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chain.get(0).getEncoded())));
        } finally {Files.deleteIfExists(temp);}
    }
}
