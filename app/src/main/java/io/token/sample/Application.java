package io.token.sample;

import static com.google.common.base.Charsets.UTF_8;
import static io.grpc.Status.Code.NOT_FOUND;
import static io.token.TokenIO.TokenCluster.SANDBOX;
import static io.token.proto.common.alias.AliasProtos.Alias.Type.EMAIL;
import static io.token.util.Util.generateNonce;

import com.google.common.io.Resources;
import io.grpc.StatusRuntimeException;
import io.token.Destinations;
import io.token.Member;
import io.token.TokenIO;
import io.token.proto.common.alias.AliasProtos.Alias;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.proto.common.transfer.TransferProtos.Transfer;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos.TransferEndpoint;
import io.token.security.UnsecuredFileSystemKeyStore;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import spark.Spark;

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

        // If we're running the first time, create a new member (Token user account)
        // for this test merchant.
        // If we're running again, log in the previously-created member.
        Member merchantMember = initializeMember(tokenIO);

        // Initializes the server
        Spark.port(3000);

        // Endpoint for transfer payment, called by client side after user approves payment.
        Spark.post("/transfer", (req, res) -> {
            Map<String, String> formData = parseFormData(req.body());
            String tokenId = formData.get("tokenId");

            // Make sure to get the token first,
            // and check its validity
            Token token = merchantMember.getToken(tokenId);

            // Redeem the token at the server, to move the funds
            Transfer transfer = merchantMember.redeemToken(token, 4.99, "EUR", "example");
            return "";
        });
        // (If user closes browser before this function is called, we don't redeem the token.
        //  Since this function is where we get the shipping information, we probably don't
        //  want to redeem the token: we wouldn't know where to ship the goods.)

        // Serve the web page and JS script:
        String script = Resources.toString(Resources.getResource("script.js"), UTF_8)
                .replace("{alias}", merchantMember.firstAlias().getValue());
        Spark.get("/script.js", (req, res) -> script);
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
                // This KeyStore reads private keys from files.
                // Here, it's set up to read the ./keys dir.
                .withKeyStore(new UnsecuredFileSystemKeyStore(
                        keys.toFile()))
                .devKey("4qY7lqQw8NOl9gng0ZHgT4xdiDqxqoGVutuZwrUYQsI")
                .build();
    }

    /**
     * Using a TokenIO SDK client and the member ID of a previously-created
     * Member (whose private keys we have stored locally).
     *
     * @param tokenIO SDK
     * @param memberId ID of member
     * @return Logged-in member.
     */
    private static Member loadMember(TokenIO tokenIO, String memberId) {
        try {
            return tokenIO.getMember(memberId);
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() == NOT_FOUND) {
                // We think we have a member's ID and keys, but we can't log in.
                // In the sandbox testing environment, this can happen:
                // Sometimes, the member service erases the test members.
                throw new RuntimeException(
                        "Couldn't log in saved member, not found. Remove keys dir and try again.");
            } else {
                throw new RuntimeException(sre);
            }
        }
    }

    /**
     * Using a TokenIO SDK client, create a new Member.
     * This has the side effect of storing the new Member's private
     * keys in the ./keys directory.
     *
     * @param tokenIO Token SDK client
     * @return newly-created member
     */
    private static Member createMember(TokenIO tokenIO) {
        // Generate a random username.
        // If we try to create a member with an already-used name,
        // it will fail.
        String email = "merchant-sample-" + generateNonce().toLowerCase() + "+noverify@example.com";
        Alias alias = Alias.newBuilder()
                .setType(EMAIL)
                .setValue(email)
                .build();
        return tokenIO.createMember(alias);
        // The newly-created member is automatically logged in.
    }

    /**
     * Log in existing member or create new member.
     *
     * @param tokenIO Token SDK client
     * @return Logged-in member
     */
    private static Member initializeMember(TokenIO tokenIO) {
        // The UnsecuredFileSystemKeyStore stores keys in a directory
        // named on the member's memberId, but with ":" replaced by "_".
        // Look for such a directory.
        //   If found, try to log in with that memberId
        //   If not found, create a new member.
        File keysDir = new File("./keys");
        String[] paths = keysDir.list();

        return Arrays.stream(paths)
                .filter(p -> p.contains("_")) // find dir names containing "_"
                .map(p -> p.replace("_", ":")) // member ID
                .findFirst()
                .map(memberId -> loadMember(tokenIO, memberId))
                .orElse(createMember(tokenIO));
    }

    /**
     * Parse form data
     */
    private static Map<String, String> parseFormData(String query) {
        try {
            Map<String, String> queryPairs = new LinkedHashMap<String, String>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                queryPairs.put(
                        URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
            return queryPairs;
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException("Couldn't parse form data");
        }
    }
}
