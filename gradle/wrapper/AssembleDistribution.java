import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

/** Reassemble the official Gradle ZIP for the unmodified wrapper, including on a cold, offline checkout. */
class AssembleDistribution {
    public static void main(String[] args) throws Exception {
        Path wrapper = Path.of(args[0]);
        Properties configuration = new Properties();
        try (var input = Files.newInputStream(wrapper.resolve("gradle-wrapper.properties"))) {
            configuration.load(input);
        }
        Path target = wrapper.resolve(configuration.getProperty("distributionUrl"));
        Path temporary = Files.createTempFile(target.getParent(), "gradle-", ".tmp");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temporary), digest);
                 var files = Files.list(target.getParent())) {
                var prefix = target.getFileName() + ".part-";
                for (Path part : files.filter(p -> p.getFileName().toString().startsWith(prefix)).sorted().toList()) {
                    Files.copy(part, out);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(configuration.getProperty("distributionSha256Sum"))) {
                throw new IOException("Gradle distribution parts do not match distributionSha256Sum");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
