"use strict";
var tokenController;
var button;

// Client side Token object for creating the Token button, handling the Token Controller, etc
var token = new window.Token({
    env: 'sandbox',
});

function clean() {
    if (button) {
        button.destroy();
        button = null;
    }

    if (tokenController && tokenController.destroy) {
        tokenController.destroy();
        tokenController = null;
    }
}

// set up a function using the item data to populate the request to fetch the TokenRequestFunction
function getTokenRequestUrl(done) {
    fetch('/transfer', {
        method: 'POST',
        mode: 'no-cors',
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
        },
        body: JSON.stringify({
            merchantId: 'Merchant 123',
            amount: 4.99,
            currency: 'EUR',
            description: 'Book Purchase',
            destination: '{"sepa":{"iban":"DE16700222000072880129"}}'
        }),
    })
    .then(function(response) {
        if (response.ok) {
            response.text()
                .then(function(data) {
                    // execute callback when successful response is received
                    done(data);
                    console.log('data: ', data);
                });
        }
    });
}

function createButton(buttonType) {
    // clean up instances
    clean();

    // get button placeholder element
    var element = document.getElementById('tokenPayBtn');

    // create the button
    button = token.createTokenButton(element, {
        label: 'Token Quick Checkout',
    });

    // create TokenController to handle messages
    tokenController = token.createController({
        onSuccess: function(data) { // Success Callback
            // build success URL
            var successURL = `/redeem?data=${window.encodeURIComponent(JSON.stringify(data))}`;
            // navigate to success URL
            window.location.assign(successURL);
        },
        onError: function(error) { // Failure Callback
            throw error;
        },
    });

    // bind the Token Button to the Token Controller when ready
    tokenController.bindButtonClick(
        button, // TokenButtonController
        getTokenRequestUrl, // token request function
        function(error) { // bindComplete callback
            // enable button after binding
            if (error) throw error;
            button.enable();
        },
        { // options
            desktop: buttonType,
        }
    );
}

function setupButtonTypeSelector() {
    var selector = document.getElementsByName('buttonTypeSelector');
    var selected;
    for (var i = 0; i < selector.length; i++) {
        if (selector[i].checked) {
            selected = selector[i].value;
        }
        selector[i].addEventListener('click', function(e) {
            var value = e.target.value;
            if (value === selected) return;
            selected = value;
            createButton(value);
        });
    }
    createButton();
}

setupButtonTypeSelector();
