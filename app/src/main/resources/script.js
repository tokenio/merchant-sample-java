function shippingCb(address, tokenCallback) {
    tokenCallback({ // Can return price based on address
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
Token.styleButton({            // Sets up the Quick Checkout button
    id: "tokenPayBtn",
    label: "Token Quick Checkout"
}).bindPayButton(
    {                               // Terms
        alias: {                    // Merchant alias
            type: 'EMAIL',
            value: '{alias}'        // (filled in by server)
        },
        amount: 4.99,               // Amount
        currency: 'EUR',            // Currency
	destinations: [{account: {sepa: { iban: "DE16700222000072880129"}}}]
    },
    shippingCb,          // Shipping callback (null for "virtual" goods)
    function(data) {     // Success callback
        $.post(
            'http://localhost:3000/transfer',
            data);
    },
    function(error) {    // Failure callback
        console.log('Something\'s wrong!', error);
    }
);
