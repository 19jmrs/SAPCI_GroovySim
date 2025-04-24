class Message {
    String body;
    def headers = [:]
    def properties = [:]

    def getHeader(String key) {
        return headers[key]
    }

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

    def getProperty(String key){
        return properties[key]
    }
    
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