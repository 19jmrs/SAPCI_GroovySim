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

   def ppStartDate = message.getHeaders().get("PPStartDate");
   def ppEndDate = message.getHeaders().get("PPEndDate");
    def approvalStatus = "APPROVED"
   String bookingDateStr = "";
 
   def regHours = 0
  def moreHours = 0
   
  def payload = new XmlParser().parseText(body);
    
   
    payload.EmployeeTimeSheet.each{
        timeSheet ->
        
        //ppStartDate = timeSheet.startDate.text()
        //ppEndDate = timeSheet.endDate.text()
        println("\n PPStartDate: " + ppStartDate)
        println("\n PPEndDate: " + ppEndDate) 
       
        timeSheet.employeeTimeSheetEntry.each{
            valResult ->
            valResult.EmployeeTimeSheetEntry.each{
                node ->

                if(node.timeTypeName.text() == "Meal Break"){
                    bookingDateStr = node.startDate.text().substring(0,10)
                    if(ppStartDate <= bookingDateStr && bookingDateStr <= ppEndDate){
                        if(approvalStatus == "APPROVED"){
                           
                            println("\n BookingDate: " + bookingDateStr)
                            println("\n HoursMatched: " + node.quantityInHours.text())
                            moreHours = new BigDecimal(node.quantityInHours.text())
                            regHours = regHours + moreHours?.plus()
                            println("\n Break Time Acc: " + regHours)
                        }
                    } 
                }
            }
        } 
        
    }
    
    println("\n Total Break Time: " + regHours)
    return message;
}

// Use to set test data
def Message preLoad (Message message){
    def xmlContent = new File("othours_512845.xml").text
    message.setBody(xmlContent)
    message.setHeader("PPStartDate", "2025-03-23")
    message.setHeader("PPEndDate", "2025-04-07")
    message.setHeader("OTHeader", "")
    
}