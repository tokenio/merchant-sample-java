"use strict";
var tokenController;
var button;
var data = {
    amount: 4.99,
    currency: 'EUR',
    description: 'Book Purchase',
};
var selectedTransferType;

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

function createRedirectButton(transferType) {
    // clean up instances
    clean();

    // Client side Token object for creating the Token button, handling the Token Controller, etc
    var token = new window.Token({
        env: 'sandbox',
    });

    // get button placeholder element
    var element = document.getElementById('tokenPayBtn');

    // create the button
    button = token.createTokenButton(element, {
        label: 'Token Quick Checkout',
    });

    // create TokenController to handle messages
    tokenController = token.createController();

    // bind the Token Button to the Token Controller when ready
    tokenController.bindButtonClick(
        button, // Token Button
        function() {
            redirectTokenRequest(transferType) // redirect token request function
        },
        function(error) { // bindComplete callback
            if (error) throw error;
            // enable button after binding
            button.enable();
        },
    );
}

function createPopupButton() {
    // clean up instances
    clean();

    // Client side Token object for creating the Token button, handling the Token Controller, etc
    var token = new window.Token({
        env: 'sandbox',
    });

    // get button placeholder element
    var element = document.getElementById('tokenPayBtn');

    // create the button
    button = token.createTokenButton(element, {
        label: 'Token Quick Checkout',
    });

    console.log("selectedTransferType:"+selectedTransferType);
    // create TokenController to handle messages
    var path = "";
    
    if (selectedTransferType === 'STANDING_ORDER') {
        path = '/redeem-standing-order-popup';
    } else if (selectedTransferType === 'ONE_STEP') {
        path = '/redeem-one-step-payment-popup';
    } else {
        path = '/redeem-popup';
    }
    
    tokenController = token.createController({
        onSuccess: function(data) { // Success Callback
            // build success URL
            var successURL = `${path}?data=${window.encodeURIComponent(JSON.stringify(data))}`;
            // navigate to success URL
            window.location.assign(successURL);
        },
        onError: function(error) { // Failure Callback
            throw error;
        },
    });

    // bind the Token Button to the Token Controller when ready
    tokenController.bindButtonClick(
        button, // Token Button
        getTokenRequestUrl, // token request function
        function(error) { // bindComplete callback
            if (error) throw error;
            // enable button after binding
            button.enable();
        },
        { // options
            desktop: 'POPUP',
        }
    );
}

function redirectTokenRequest(transferType) {
    // format data as URL query string
    var queryString = Object.keys(data).map(key => key + '=' + window.encodeURIComponent(data[key])).join('&');

    // go to transfer or standing-order or one step
    var path = "";
    
    if (selectedTransferType === 'STANDING_ORDER') {
        path = '/standing-order?';
    } else if (selectedTransferType === 'ONE_STEP') {
        path = '/one-step-payment?';
    } else {
        path = '/transfer?';
    }    document.location.assign(path + queryString);
}

// set up a function using the item data to populate the request to fetch the TokenRequestFunction
function getTokenRequestUrl(done) {
    
	var path="";
	// fetch Token Request URL
	if (selectedTransferType === 'STANDING_ORDER') {
        path = '/standing-order-popup';
    } else if (selectedTransferType === 'ONE_STEP') {
        path = '/one-step-payment-popup';
    } else {
        path = '/transfer-popup';
    }   
	
	fetch(path, {
        method: 'POST',
        mode: 'no-cors',
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
        },
        body: JSON.stringify(data),
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

function setupButtonTypeSelector() {
    var transferTypeSelector = document.getElementsByName('transferTypeSelector');
    var modeSelector = document.getElementsByName('buttonTypeSelector');
    var selectedMode = modeSelector[0].value;
    selectedTransferType  = transferTypeSelector[0].value;

    for (var i = 0; i < transferTypeSelector.length; i++) {
        transferTypeSelector[i].addEventListener('click', function(e) {
            var value = e.target.value;
            if (value === selectedTransferType) return;
            selectedTransferType = value;
            createTokenRequestButton(selectedMode)
        });
    }

    for (var i = 0; i < modeSelector.length; i++) {
        modeSelector[i].addEventListener('click', function(e) {
            var value = e.target.value;
            if (value === selectedMode) return;
            selectedMode = value;
            createTokenRequestButton(selectedMode)
        });
    }
    createTokenRequestButton(selectedMode);
}

function createTokenRequestButton(selectedMode) {
    if (selectedMode === 'POPUP') {
        createPopupButton();
    } else if (selectedMode === 'REDIRECT'){
        createRedirectButton();
    }
}

setupButtonTypeSelector();
