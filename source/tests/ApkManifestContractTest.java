import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Verifies real delivery bytes against the installed loader's APK manifest protocol. */
public final class ApkManifestContractTest {
    private static boolean accepts(PublicKey key, String payload, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(signature);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        require(args.length >= 6 && (args.length - 1) % 5 == 0, "public key and manifest tuples required");
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(args[0])));
        int checks = 0;
        for (int i = 1; i < args.length; i += 5) {
            String version = args[i], name = args[i + 1], expectedSha = args[i + 2];
            byte[] signature = Base64.getDecoder().decode(args[i + 3]);
            StringBuilder digest = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(args[i + 4]))))
                digest.append(String.format("%02x", b & 255));
            require(expectedSha.equals(digest.toString()), "APK bytes differ from signed manifest"); checks++;
            require(accepts(key, version + "|" + name + "|" + expectedSha, signature), "installed-loader manifest signature rejected"); checks++;
            require(!accepts(key, version + "|" + name + "-tampered|" + expectedSha, signature), "version name is not authenticated"); checks++;
            require(!accepts(key, version + "|" + expectedSha, signature), "obsolete two-field protocol unexpectedly accepted"); checks++;
        }
        System.out.println("PASS " + checks + " APK manifest protocol and delivery checks");
    }
}
