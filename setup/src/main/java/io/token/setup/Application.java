package io.token.setup;

import static io.token.TokenIO.TokenCluster.SANDBOX;

import io.token.TokenIO;
import io.token.Member;
import io.token.security.UnsecuredFileSystemKeyStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application main entry point. 
 * To execute, one needs to run something like:
 *
 * <pre>
 * ./gradlew :app:shadowJar
 * java -jar ./app/build/libs/app-1.0.0-all.jar
 * </pre>
 */
public class Application {
    /**
     * Main function.
     *
     * @param args command line arguments
     * @throws IOException thrown on errors
     */
    public static void main(String[] args) throws IOException {
        TokenIO tokenIO = initializeSDK();
        Member member = tokenIO.createMember("marianoTest5");
        System.out.printf("\nCreated Member with ID: %s \n", member.memberId());
    }

    /**
     * Initializes the SDK, pointing it to the specified environment and the
     * directory where keys are being stored.
     *
     * @return TokenIO SDK instance
     * @throws IOException
     */
    private static TokenIO initializeSDK() throws IOException {

        Path keys = Files.createDirectories(Paths.get("./keys"));
        return TokenIO.builder()
                .connectTo(SANDBOX)
                .withKeyStore(new UnsecuredFileSystemKeyStore(keys.toFile()))
                .build();
    }
}
