class Message {
    String body;
    def headers = [:]
    def properties = [:]

    def Map getHeaders(){
        return headers
    }
    
    def setHeader(String key, String value) {
        headers[key] = value
        return this
    }

    def Map getProperties(){
        return properties
    }
    
    // Added method to set a property with key-value pair
    def setProperty(String key, String value) {
        properties[key] = value
        return this
    }
    
    def setBody(String body) {
        this.body = body
        return this
    }

    public String getBody(){
        return body;
    }
}