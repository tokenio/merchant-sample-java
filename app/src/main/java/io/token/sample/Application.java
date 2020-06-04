package io.token.sample;

import static com.google.common.base.Charsets.UTF_8;
import static io.grpc.Status.Code.NOT_FOUND;
import static io.token.TokenClient.TokenCluster.SANDBOX;
import static io.token.proto.common.alias.AliasProtos.Alias.Type.EMAIL;
import static io.token.util.Util.generateNonce;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.io.Resources;
import io.grpc.StatusRuntimeException;
import io.token.proto.common.account.AccountProtos.BankAccount;
import io.token.proto.common.alias.AliasProtos.Alias;
import io.token.proto.common.member.MemberProtos.Profile;
import io.token.proto.common.submission.SubmissionProtos.StandingOrderSubmission;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.proto.common.transfer.TransferProtos.Transfer;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos.TransferDestination;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos.TransferEndpoint;
import io.token.security.UnsecuredFileSystemKeyStore;
import io.token.tokenrequest.TokenRequest;
import io.token.tokenrequest.TokenRequest.TransferBuilder;
import io.token.tokenrequest.TokenRequestResult;
import io.token.tpp.Member;
import io.token.tpp.TokenClient;
import io.token.tpp.tokenrequest.TokenRequestCallback;
import spark.QueryParamsMap;
import spark.Response;
import spark.Spark;

/**
 * Application main entry point. To execute, one needs to run something like:
 * <p>
 *
 * <pre>
 * ./gradlew :app:shadowJar
 * java -jar ./app/build/libs/app-1.0.0-all.jar
 * </pre>
 */
public class Application {
    private static final String BANK_ID = "ngp-swed";

    private static final String CSRF_TOKEN_KEY = "csrf_token";
    private static final String TOKEN_REQUEST_ID_KEY = "request_id";
    private static final TokenClient tokenClient = initializeSDK();
    private static final Member merchantMember = initializeMember(tokenClient);

    /**
     * Main function.
     *
     * @param args command line arguments
     * @throws IOException thrown on errors
     */
    public static void main(String[] args) throws IOException {
        // Initializes the server
        Spark.port(3000);

        // Endpoint for transfer payment, called by client side to initiate a payment.
        Spark.get("/transfer", (req, res) -> {
            Map<String, String> params = toMap(req.queryMap());
            String callbackUrl = req.scheme() + "://" + req.host() + "/redeem";

            String tokenRequestUrl = initializeTokenRequestUrl(params, callbackUrl, res, "DEFAULT");

            // send a 302 redirect
            res.status(302);
            res.redirect(tokenRequestUrl);
            return null;
        });

        Spark.get("/standing-order", (req, res) -> {
            Map<String, String> params = toMap(req.queryMap());
            String callbackUrl = req.scheme() + "://" + req.host() + "/redeem-standing-order";

            String tokenRequestUrl =
                    initializeStandingOrderTokenRequestUrl(params, callbackUrl, res);

            // send a 302 redirect
            res.status(302);
            res.redirect(tokenRequestUrl);
            return null;
        });

        Spark.get("/one-step-payment", (req, res) -> {
            Map<String, String> params = toMap(req.queryMap());
            String callbackUrl = req.scheme() + "://" + req.host() + "/redirect-one-step-payment";

            String tokenRequestUrl =
                    initializeTokenRequestUrl(params, callbackUrl, res, "ONE_STEP");

            // send a 302 redirect
            res.status(302);
            res.redirect(tokenRequestUrl);
            return null;
        });

        // for redirect flow, use Token.parseTokenRequestCallbackUrl()
        Spark.get("/redirect-one-step-payment", (req, res) -> {
            res.status(200);
            return "Success! One Step Payment " + req.queryParams("transferId");
        });

        // for redirect flow, use Token.parseTokenRequestCallbackUrl()
        Spark.get("/redeem", (req, res) -> {
            return redeem(req, res);
        });

        // for redirect flow, use Token.parseTokenRequestCallbackUrl()
        Spark.get("/redeem-standing-order", (req, res) -> {
            String callbackUrl = req.url() + "?" + req.queryString();

            // retrieve CSRF token from browser cookie
            String csrfToken = req.cookie(CSRF_TOKEN_KEY);

            // check CSRF token and retrieve state and token ID from callback parameters
            TokenRequestCallback callback =
                    tokenClient.parseTokenRequestCallbackUrlBlocking(callbackUrl, csrfToken);

            // redeem the token at the server to move the funds
            StandingOrderSubmission standingOrderSubmission =
                    merchantMember.redeemStandingOrderTokenBlocking(callback.getTokenId());
            res.status(200);
            return "Success! Redeemed standing order " + standingOrderSubmission.getId();
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

    private static String redeem(spark.Request req, spark.Response res) {
        String tokenRequestId;
        if (!req.queryParams().contains(TOKEN_REQUEST_ID_KEY)) {
            System.out.println("Received callback from the bank");
            tokenRequestId = merchantMember.onBankAuthCallbackBlocking(BANK_ID, req.queryString());
        } else {
            tokenRequestId = req.queryParams(TOKEN_REQUEST_ID_KEY);
            System.out.println("Received callback from Token: " + tokenRequestId);
        }

        TokenRequestResult result = tokenClient.getTokenRequestResultBlocking(tokenRequestId);
        Transfer transfer;
        if (result.getTransferId().isPresent()) {
            System.out.println("Found redeemed token: " + result.getTransferId().get());
            transfer = merchantMember.getTransferBlocking(result.getTransferId().get());
        } else {
            Token token = merchantMember.getTokenBlocking(result.getTokenId());
            transfer = merchantMember.redeemTokenBlocking(token);
        }
        res.status(200);
        return "Success! Redeemed transfer " + transfer.getId();
    }

    private static String initializeTokenRequestUrl(Map<String, String> params, String callbackUrl,
            Response response, String transferType) {
        double amount = Double.parseDouble(params.get("amount"));
        String currency = params.get("currency");
        String description = params.get("description");
        TransferDestination destination = TransferDestination.newBuilder()
                .setSepa(TransferDestination.Sepa.newBuilder().setBic("bic")
                        .setIban("DE16700222000072880129").build())
                .setCustomerData(TransferInstructionsProtos.CustomerData.newBuilder()
                        .addLegalNames("merchant-sample-java").build())
                .build();

        // This is testing account for one of bank to test one step support
        // Will need bank credentials to complete the flow. Test Credentials are available.
        String bankId = "ngp-cbi-05034";
        TransferEndpoint source = TransferEndpoint.newBuilder()
                .setAccount(BankAccount.newBuilder()
                        .setIban(BankAccount.Iban.newBuilder()
                                .setIban("IT77O0848283352871412938123").build())
                        .build())
                .setBankId(bankId).build();

        // generate CSRF token
        String csrfToken = generateNonce();

        // generate a reference ID for the token
        String refId = generateNonce();

        // set CSRF token in browser cookie
        response.cookie(CSRF_TOKEN_KEY, csrfToken);

        // create the token request builder
        TransferBuilder tokenRequestBuilder =
                TokenRequest.transferTokenRequestBuilder(amount, currency)
                        .setDescription(description).addDestination(destination).setRefId(refId)
                        .setToAlias(merchantMember.firstAliasBlocking())
                        .setToMemberId(merchantMember.memberId()).setRedirectUrl(callbackUrl)
                        .addDestination(TransferDestination.newBuilder()
                                .setSepa(TransferDestination.Sepa.newBuilder()
                                        .setIban("FILLME")
                                        .setBic("FILLME")
                                        .build())
                                .build())
                        .setSource(TransferEndpoint.newBuilder()
                                .setBankId(BANK_ID)
                                .setAccount(BankAccount.newBuilder()
                                        .setIban(BankAccount.Iban.newBuilder()
                                                .setIban("FILLME")))
                                .build())
                        .setCsrfToken(csrfToken);

        if (transferType.equals("ONE_STEP")) {
            tokenRequestBuilder.setSource(source);
            tokenRequestBuilder.setBankId(bankId);
        }

        String requestId = merchantMember.storeTokenRequestBlocking(tokenRequestBuilder.build());

        // generate Token Request URL
        return merchantMember.getBankAuthUrlBlocking(BANK_ID, requestId);
    }

    private static String initializeStandingOrderTokenRequestUrl(Map<String, String> params,
            String callbackUrl, Response response) {
        double amount = Double.parseDouble(params.get("amount"));
        String currency = params.get("currency");
        String description = params.get("description");
        TransferDestination destination = TransferDestination.newBuilder()
                .setSepa(TransferDestination.Sepa.newBuilder().setBic("bic")
                        .setIban("DE16700222000072880129").build())
                .setCustomerData(TransferInstructionsProtos.CustomerData.newBuilder()
                        .addLegalNames("merchant-sample-java").build())
                .build();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1);
        String refId = generateNonce();

        // generate CSRF token
        String csrfToken = generateNonce();
        // set CSRF token in browser cookie
        response.cookie(CSRF_TOKEN_KEY, csrfToken);

        // create the token request
        TokenRequest request = TokenRequest
                .standingOrderRequestBuilder(amount, currency, "MNTH", startDate.toString(),
                        endDate.toString(), Collections.singletonList(destination))
                .setDescription(description).setRefId(refId)
                .setToAlias(merchantMember.firstAliasBlocking())
                .setToMemberId(merchantMember.memberId()).setRedirectUrl(callbackUrl)
                .setCsrfToken(csrfToken).build();

        String requestId = merchantMember.storeTokenRequestBlocking(request);

        // generate Token Request URL
        return merchantMember.getBankAuthUrlBlocking(BANK_ID, requestId);
    }

    /**
     * Initializes the SDK, pointing it to the specified environment and the directory where keys
     * are being stored.
     *
     * @return TokenClient SDK instance
     */
    private static TokenClient initializeSDK() {
        Path keys;
        try {
            keys = Files.createDirectories(Paths.get("./keys"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return TokenClient.builder().connectTo(SANDBOX)
                // This KeyStore reads private keys from files.
                // Here, it's set up to read the ./keys dir.
                .withKeyStore(new UnsecuredFileSystemKeyStore(keys.toFile())).build();
    }

    /**
     * Using a TokenClient SDK client and the merchantMember ID of a previously-created Member
     * (whose private keys we have stored locally).
     *
     * @param tokenClient SDK
     * @param memberId ID of merchantMember
     * @return Logged-in merchantMember.
     */
    private static Member loadMember(TokenClient tokenClient, String memberId) {
        try {
            return tokenClient.getMemberBlocking(memberId);
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() == NOT_FOUND) {
                // We think we have a merchantMember's ID and keys, but we can't log in.
                // In the sandbox testing environment, this can happen:
                // Sometimes, the merchantMember service erases the test members.
                throw new RuntimeException(
                        "Couldn't log in saved merchantMember, not found. Remove keys dir and try again.");
            } else {
                throw new RuntimeException(sre);
            }
        }
    }

    /**
     * Using a TokenClient SDK client, create a new Member. This has the side effect of storing the
     * new Member's private keys in the ./keys directory.
     *
     * @param tokenClient Token SDK client
     * @return newly-created merchantMember
     */
    private static Member createMember(TokenClient tokenClient) {
        // Generate a random username.
        // If we try to create a merchantMember with an already-used name,
        // it will fail.
        // If a domain alias is used instead of an email, please contact Token
        // with the domain and merchantMember ID for verification.
        // See https://developer.token.io/sdk/#aliases for more information.
        String email = "msjava-" + generateNonce().toLowerCase() + "+noverify@example.com";
        Alias alias = Alias.newBuilder().setType(EMAIL).setValue(email).build();
        Member member = tokenClient.createMemberBlocking(alias);
        // set merchantMember profile: the name and the profile picture
        member.setProfileBlocking(Profile.newBuilder().setDisplayNameFirst("Demo")
                .setDisplayNameLast("Merchant").build());
        try {
            byte[] pict = Resources.toByteArray(Resources.getResource("southside.png"));
            member.setProfilePictureBlocking("image/png", pict);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return member;
        // The newly-created merchantMember is automatically logged in.
    }

    /**
     * Log in existing merchantMember or create new merchantMember.
     *
     * @param tokenClient Token SDK client
     * @return Logged-in merchantMember
     */
    private static Member initializeMember(TokenClient tokenClient) {
        // The UnsecuredFileSystemKeyStore stores keys in a directory
        // named on the merchantMember's memberId, but with ":" replaced by "_".
        // Look for such a directory.
        // If found, try to log in with that memberId
        // If not found, create a new merchantMember.
        File keysDir = new File("./keys");
        String[] paths = keysDir.list();

        return Arrays.stream(paths).filter(p -> p.contains("_")) // find dir names containing "_"
                .map(p -> p.replace("_", ":")) // merchantMember ID
                .findFirst().map(memberId -> loadMember(tokenClient, memberId))
                .orElseGet(() -> createMember(tokenClient));
    }

    private static Map<String, String> toMap(QueryParamsMap queryData) {
        return queryData.toMap().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()[0]));
    }
}
