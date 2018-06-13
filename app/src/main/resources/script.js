"use strict";

var elementId = "tokenPayBtn";

function createButton() {
    // Create button
    window.Token.styleButton({
        id: elementId,
        label: "Token Quick Checkout",
    }, bindButton); // execute bindButton when styling button is finished
}

function bindButton(button) {
    var XHR = new XMLHttpRequest();

    // Set up our request
    XHR.open('POST', 'http://localhost:3000/transfer', true);

    XHR.setRequestHeader("Content-Type", "application/json; charset=utf-8");

    var data = $.param({
        merchantId: 'Merchant 123',
        amount: 4.99,
        currency: 'EUR',
        description: 'Book Purchase',
        destination: '{"sepa":{"iban":"DE16700222000072880129"}}'
     });

     // Define what happens on successful data submission
     XHR.addEventListener("load", function(event) {
       button.bindPayButton(
            event.target.responseURL, // request token URL
            null, // shipping callback (deprecated)
            function(data) {
                // build success URL
                var successURL = "/redeem"
                    + "?tokenId=" + window.encodeURIComponent(data.tokenId);
                // navigate to success URL
                window.location.assign(successURL);
            },
            function(error) { // fail callback
                throw error;
            }
       );
     });

    // Send the data; HTTP headers are set automatically
    XHR.send(data);
}

createButton();
