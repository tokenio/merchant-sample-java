package io.token.sample;

import static com.google.common.base.Charsets.UTF_8;
import static io.token.TokenIO.TokenCluster.SANDBOX;

import com.google.gson.JsonParser;
import io.token.proto.common.token.TokenProtos.Token;
import com.google.common.io.Resources;
import io.token.Member;
import io.token.TokenIO;
import io.token.security.UnsecuredFileSystemKeyStore;

import spark.Spark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application main entry point.
 * To execute, one needs to run something like:
 * <p>
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
        // Connect to Token's development sandbox
        TokenIO tokenIO = initializeSDK();

        // Log in using the test merchant account created
        // by the setup utility:
        Member loggedInMember = tokenIO.login("m:28i4rgDUaM7sUBMCaP4ZfHJpnmMX:5zKtXEAq");

        // Initializes the server
        Spark.port(3000);

        // Endpoint for transfering, called by client side after user approval
        Spark.post("/transfer", (req, res) -> {
            String tokenId = new JsonParser()
                    .parse(req.body())
                    .getAsJsonObject()
                    .get("tokenId")
                    .getAsString();
            
            // Make sure to get the token first, and check its validity
            Token token = loggedInMember.getToken(tokenId);
            // Redeem the token at the server, to move the funds
            loggedInMember.redeemToken(token, 4.99, "EUR", "example");
            return "";
        });
        String page = Resources.toString(Resources.getResource("index.html"), UTF_8);
        Spark.get("/", (req, res) -> page);
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
