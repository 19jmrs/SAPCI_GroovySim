import Message;
import java.util.HashMap;
import groovy.xml.XmlUtil;
import groovy.xml.XmlParser;
import groovy.xml.MarkupBuilder;


processData(new Message())

def Message processData(Message message) 
{
    message = preLoad(message)
    def body = (String)message.getBody();
    
    // Parse the XML content
    def payload = new XmlParser().parseText(body);
      // Find all PerPhone elements in the document
    def allPhones = payload.depthFirst().findAll { it.name() == 'PerPhone' }
    
    // Find PerPhone elements to remove (where phoneType is not 5638)
    def phonesToRemove = allPhones.findAll { phone ->
        phone.phoneType.text() != '5638'
    }
    
    // Remove the identified PerPhone elements from their parent nodes
    phonesToRemove.each { phone ->
        def parent = phone.parent()
        parent.remove(phone)
    }
    
    // Find all PerEmail elements in the document
    def allEmails = payload.depthFirst().findAll { it.name() == 'PerEmail' }
    
    // Find PerEmail elements to remove (where emailType is not 5622)
    def emailsToRemove = allEmails.findAll { email ->
        email.emailType.text() != '5622'
    }
    
    // Remove the identified PerEmail elements from their parent nodes
    emailsToRemove.each { email ->
        def parent = email.parent()
        parent.remove(email)
    }
    
    // Convert the modified XML back to a string
    def modifiedXml = XmlUtil.serialize(payload)
    message.setBody(modifiedXml)
   
    println message.getBody()
    return message
}

// Use to set test data
def Message preLoad (Message message){
    def xmlContent = new File("testData.xml").text
    message.setBody(xmlContent)
    return message
}