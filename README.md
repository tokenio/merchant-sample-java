## Token Merchant Checkout Sample: Java

This sample code shows how to enable the Token Merchant Checkout
experience on a simple web server.

To build this code, you need Java Development Kit (JDK) version 7 or later.

To build, `./gradlew shadowJar`.

To run, `java -jar app/build/libs/app-*.jar`

This starts up a server.

The first time you run the server, it creates a new Member (Token user account).
It saves the Member's private keys in the `app/keys` directory and its ID
in the `app/merchant_member_id.txt`. In subsequent runs, the server uses this ID
and these keys to log the Member in.

The server operates in Token's Sandbox environment. This testing environment
lets you try out UI and payment flows without moving real money.

The server shows a web page at `localhost:3000`. The page has a checkout button.
Clicking the button starts the Token merchant payment flow.
The server handles endorsed payments by redeeming tokens.

Test by going to `localhost:3000` and paying with the "Token PSD2" app,
installed from the App Store.
