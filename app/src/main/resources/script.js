function shippingCb(address, tokenCallback) {
    tokenCallback({                    // Can return different prices based on address
        shippingMethods: [
            {
                id: '0',
                name: 'Standard Ground (5-9 business days)',
                deliveryTime: '5 - 9 Business Days',
                cost: 0
            },
        ],
        tax: 0
    });
}

// Initializes the Quick Checkout Button
Token.bindPayButton(
    'tokenPayBtn',                  // ID of the page's button element
    {                               // Terms
        username: '{alias}',        // Merchant username (filled in by Application.java)
        amount: 4.99,               // Amount
        currency: 'EUR',            // Currency
        destinations: [{
            // Transfer destinations. If your bank supports Token payments, you can use
            // your Token member and account ID instead or in addition.
            account: {
                sepa: {
                    iban: 'DK5000440441116263'
                }
            }
        }],
    },
    shippingCb,          // Shipping callback
    function(data) {     // Success callback
        $.post('http://localhost:3000/transfer', data, function () {});
    },
    function(error) {    // Failure callback
        console.log('Something\'s wrong!', error);
    }
);
