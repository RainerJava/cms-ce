self.onmessage = function (event) {
    var message;
    var url = "../" + event.data.url;

    var request = new XMLHttpRequest();
    request.open('GET', url, false);
    try {
        request.send(null);

        if (request.status === 200) {
            message = {
                "operation": event.data.operation,
                "success": true,
                "jsonData": request.responseText
            };
            self.postMessage(message);
        }
        else {
            message = {
                "operation": event.data.operation,
                "success": false,
                "errorMessage": "HTTP response: " + request.status
            };
            self.postMessage(message);
        }
    }
    catch (error) {
        message = {
            "operation": event.data.operation,
            "success": false,
            "errorMessage": error
        };
        self.postMessage(message);
    }
};