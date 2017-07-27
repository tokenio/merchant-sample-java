## Token Merchant Checkout Sample: Java

**To create a member:**

* Edit `setup/src/main/java/io/token/setup/Application.java` and replace the
  username `marianoTest5` with another alphanumeric string.
* Compile: `./gradlew shadowJar`
* Run the setup utility: `java -jar setup/build/libs/setup-1.0.1-all.jar`
* Notice the username output at the end of the output.

This creates a new member on the Token network and stores its private keys
in the `./keys` directory.

**To run the server:**

1. In `app/src/main/resources/index.html`, change the username to the one used above.
2. In `app/src/main/java/io/token/sample/Application.java`, in the `tokenIO.login`
   call, change the memberId to the output from the setup program.
4. Compile and run: `./gradlew shadowJar` ;
   `java -jar app/build/libs/app-1.0.1-all.jar`
5. Test by going to localhost:3000, and paying with the "Token PSD2" app, installed from the App Store
