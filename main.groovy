import Message;

processData(new Message())

//normal cpi scripting
def processData(Message message) {
    message = preLoad(message)

    
    def body = message.getBody()
    println "Body: " + body + "\n"
    def oldHeader = message.getHeaders().get("oldHeader")
    println "Header: " + oldHeader + "\n"
    def oldProperty = message.getProperty("oldProperty")
    println "Property: " + oldProperty + "\n"
    message.setProperty("test", "ola")
    print(message.getProperty("test"))

    return message
}


// Use to set test data
def Message preLoad(Message message){
    message.setProperty("oldProperty", "Hello, World!") 
    message.setHeader("oldHeader", "HeaderValue")
    message.setBody("""<EmpJob>
        <EmpJob>
            <EmpID>123</EmpID>
            <JobID>456</JobID>
        </EmpJob>
        </EmpJob>""")
    return message
}