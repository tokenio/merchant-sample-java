function initiatePayment() {
    var XHR = new XMLHttpRequest();

    // Set up our request
    XHR.open('POST', 'http://localhost:3000/transfer', true);

    XHR.setRequestHeader("Content-Type", "application/json; charset=utf-8");

    var data = $.param({
        merchantId: 'Merchant 123',
        amount: 4.99,
        currency: 'EUR',
        description: 'Book Purchase',
        destination: 'DE16700222000072880129'
     });

     // Define what happens on successful data submission
     XHR.addEventListener("load", function(event) {
       window.location.replace(event.target.responseURL);
     });

    // Send the data; HTTP headers are set automatically
    XHR.send(data);
}

document.getElementById("tokenPayBtn").onclick = initiatePayment;
