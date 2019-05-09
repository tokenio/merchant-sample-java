package io.token.sample;

import static com.google.common.base.Charsets.UTF_8;
import static io.grpc.Status.Code.NOT_FOUND;
import static io.token.TokenClient.TokenCluster.SANDBOX;
import static io.token.proto.common.alias.AliasProtos.Alias.Type.EMAIL;
import static io.token.util.Util.generateNonce;

import com.google.common.io.Resources;
import io.grpc.StatusRuntimeException;
import io.token.proto.ProtoJson;
import io.token.proto.common.account.AccountProtos.BankAccount;
import io.token.proto.common.alias.AliasProtos.Alias;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.proto.common.transfer.TransferProtos.Transfer;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos.TransferEndpoint;
import io.token.security.UnsecuredFileSystemKeyStore;
import io.token.tokenrequest.TokenRequest;
import io.token.tpp.Member;
import io.token.tpp.TokenClient;
import io.token.tpp.tokenrequest.TokenRequestCallback;

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
import java.util.stream.Collectors;

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
    private static final String CSRF_TOKEN_KEY = "csrf_token";

    /**
     * Main function.
     *
     * @param args command line arguments
     * @throws IOException thrown on errors
     */
    public static void main(String[] args) throws IOException {
        // Connect to Token's development sandbox
        TokenClient tokenClient = initializeSDK();

        // If we're running the first time, create a new member (Token user account)
        // for this test merchant.
        // If we're running again, log in the previously-created member.
        Member merchantMember = initializeMember(tokenClient);

        // Initializes the server
        Spark.port(3000);

        // Endpoint for transfer payment, called by client side to initiate a payment.
        Spark.post("/transfer", (req, res) -> {
            Map<String, String> formData = parseFormData(req.body());

            double amount = Double.parseDouble(formData.get("amount"));
            String currency = formData.get("currency");
            BankAccount destination = ProtoJson.fromJson(
                    formData.get("destination"),
                    BankAccount.newBuilder());

            // generate CSRF token
            String csrfToken = generateNonce();

            // generate a reference ID for the token
            String refId = generateNonce();

            // set CSRF token in browser cookie
            res.cookie(CSRF_TOKEN_KEY, csrfToken);

            // create the token request
            TokenRequest request = TokenRequest.transferTokenRequestBuilder(amount, currency)
                    .setDescription(formData.get("description"))
                    .addDestination(TransferEndpoint.newBuilder()
                            .setAccount(destination)
                            .build())
                    .setRefId(refId)
                    .setToAlias(merchantMember.firstAliasBlocking())
                    .setToMemberId(merchantMember.memberId())
                    .setRedirectUrl("http://localhost:3000/redeem")
                    .setCsrfToken(csrfToken)
                    .build();

            String requestId = merchantMember.storeTokenRequestBlocking(request);

            //generate Token Request URL to redirect to
            String tokenRequestUrl = tokenClient.generateTokenRequestUrlBlocking(requestId);
            //send a 302 Redirect
            res.status(302);
            res.redirect(tokenRequestUrl);
            return null;
        });

        Spark.get("/redeem", (req, res) -> {
            Map<String, String> queryParams = req.queryParams()
                    .stream()
                    .collect(Collectors.toMap(k -> k, k -> req.queryParams(k)));

            // retrieve CSRF token from browser cookie
            String csrfToken = req.cookie(CSRF_TOKEN_KEY);

            // check CSRF token and retrieve state and token ID from callback parameters
            TokenRequestCallback callback = tokenClient.parseTokenRequestCallbackParamsBlocking(
                    queryParams,
                    csrfToken);

            //get the token and check its validity
            Token token = merchantMember.getTokenBlocking(callback.getTokenId());

            //redeem the token at the server to move the funds
            Transfer transfer = merchantMember.redeemTokenBlocking(token);
            res.status(200);
            return "Success! Redeemed transfer " + transfer.getId();
        });

        // Serve the web page, stylesheet and JS script:
        String script = Resources.toString(Resources.getResource("script.js"), UTF_8)
                .replace("{alias}", merchantMember.firstAliasBlocking().getValue());
        Spark.get("/script.js", (req, res) -> script);
        String style = Resources.toString(Resources.getResource("style.css"), UTF_8);
        Spark.get("/style.css", (req, res) -> {
            res.type("text/css");
            return style;
        });
        String page = Resources.toString(Resources.getResource("index.html"), UTF_8);
        Spark.get("/", (req, res) -> page);
    }

    /**
     * Initializes the SDK, pointing it to the specified environment and the
     * directory where keys are being stored.
     *
     * @return TokenClient SDK instance
     * @throws IOException
     */
    private static TokenClient initializeSDK() throws IOException {
        Path keys = Files.createDirectories(Paths.get("./keys"));
        return TokenClient.builder()
                .connectTo(SANDBOX)
                // This KeyStore reads private keys from files.
                // Here, it's set up to read the ./keys dir.
                .withKeyStore(new UnsecuredFileSystemKeyStore(
                        keys.toFile()))
                .devKey("4qY7lqQw8NOl9gng0ZHgT4xdiDqxqoGVutuZwrUYQsI")
                .build();
    }

    /**
     * Using a TokenClient SDK client and the member ID of a previously-created
     * Member (whose private keys we have stored locally).
     *
     * @param tokenClient SDK
     * @param memberId ID of member
     * @return Logged-in member.
     */
    private static Member loadMember(TokenClient tokenClient, String memberId) {
        try {
            return tokenClient.getMemberBlocking(memberId);
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
     * Using a TokenClient SDK client, create a new Member.
     * This has the side effect of storing the new Member's private
     * keys in the ./keys directory.
     *
     * @param tokenClient Token SDK client
     * @return newly-created member
     */
    private static Member createMember(TokenClient tokenClient) {
        // Generate a random username.
        // If we try to create a member with an already-used name,
        // it will fail.
        // If a domain alias is used instead of an email, please contact Token
        // with the domain and member ID for verification.
        // See https://developer.token.io/sdk/#aliases for more information.
        String email = "msjava-" + generateNonce().toLowerCase() + "+noverify@example.com";
        Alias alias = Alias.newBuilder()
                .setType(EMAIL)
                .setValue(email)
                .build();
        return tokenClient.createMemberBlocking(alias);
        // The newly-created member is automatically logged in.
    }

    /**
     * Log in existing member or create new member.
     *
     * @param tokenClient Token SDK client
     * @return Logged-in member
     */
    private static Member initializeMember(TokenClient tokenClient) {
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
                .map(memberId -> loadMember(tokenClient, memberId))
                .orElseGet(() -> createMember(tokenClient));
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
