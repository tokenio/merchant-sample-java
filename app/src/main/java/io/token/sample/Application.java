package io.token.sample;

import static com.google.common.base.Charsets.UTF_8;
import static io.grpc.Status.Code.NOT_FOUND;
import static io.token.TokenIO.TokenCluster.SANDBOX;
import static io.token.util.Util.generateNonce;

import com.google.common.io.Resources;
import io.grpc.StatusRuntimeException;
import io.token.Member;
import io.token.TokenIO;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.security.UnsecuredFileSystemKeyStore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        // Endpoint for transfer payment, called by client side after user approval.
        Spark.post("/transfer", (req, res) -> {
            Map<String, String> formData = parseFormData(req.body());
            String tokenId = formData.get("tokenId");

            // Make sure to get the token first, and check its validity
            Token token = merchantMember.getToken(tokenId);

            // Redeem the token at the server, to move the funds
            merchantMember.redeemToken(token, 4.99, "EUR", "example");
            return "";
        });
        // (If user closes browser before this function is called, we don't redeem the token.
        //  Since this function is where we get the shipping information, we probably don't
        //  want to redeem the token: we wouldn't know where to ship the goods.)

        // Serve the web page and JS script:
        String script = Resources.toString(Resources.getResource("script.js"), UTF_8)
                .replace("{alias}", merchantMember.firstUsername());
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
                // Here, it's set up to read them from the ./keys directory.
                .withKeyStore(new UnsecuredFileSystemKeyStore(keys.toFile()))
                .build();
    }

    /**
     * Using a TokenIO SDK client and the member ID of a previously-created
     * Member (whose private keys we have stored locally), log in as that member.
     *
     * @param tokenIO SDK
     * @param memberId ID of member
     * @return Logged-in member.
     */
    private static Member loginMember(TokenIO tokenIO, String memberId) {
        return tokenIO.login(memberId);
    }

    /**
     * Using a TokenIO SDK client, create a new Member.
     * This has the side effect of storing the new Member's private
     * keys in the ./keys directory.
     *
     * @param tokenIO
     * @return
     */
    private static Member createMember(TokenIO tokenIO) {
        // Generate a random username.
        // If we try to create a member with an already-used name,
        // it will fail.
        String username = "merchant-sample-" + generateNonce();
        return tokenIO.createMember(username);
        // The newly-created member is automatically logged in.
    }

    /**
     * We keep the ID of our merchant member in a file.
     * If we see the file, try to read it and log in using the ID within.
     * If we don't see the file, create a new Member.
     *
     * @param tokenIO Token SDK client
     * @return Logged-in member
     */
    private static Member initializeMember(TokenIO tokenIO) {
        String path = "./merchant_member_id.txt";
        File memberIdFile = new File(path);
        if (memberIdFile.exists() && !memberIdFile.isDirectory()) {
            // We see the file, so log in existing member.
            try {
                String memberId = new String(Files.readAllBytes(Paths.get(path))).trim();
                return loginMember(tokenIO, memberId);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read " + path);
            } catch (StatusRuntimeException sre) {
                if (sre.getStatus().getCode() == NOT_FOUND) {
                    // We think we have a member's ID and keys, but we can't log in.
                    // In the sandbox testing environment, this can happen:
                    // Sometimes, the member service erases the test members.

                    // Fall through to create new member
                } else {
                    throw new RuntimeException(sre);
                }
            }
        }
        // We don't see the file
        // (or we're falling through from a weird case),
        // so create a new member.
        // (Then save its ID so next time we know it exists.)
        Member member = createMember(tokenIO);
        try {
            PrintWriter writer = new PrintWriter(path);
            writer.print(member.memberId());
            writer.close();
            return member;
        } catch (FileNotFoundException ex) {
            System.out.printf("Failed to save " + path);
            return member;
        }

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
