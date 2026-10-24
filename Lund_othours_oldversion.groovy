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

   //def hppStartDate = message.getHeaders().get("PPStartDate");
   //def hppEndDate = message.getHeaders().get("PPEndDate");
   def ppStartDate = ""
   def ppEndDate = ""
   String HeaderValue = message.getHeaders().get("OTHeader")
   String bookingDateStr = "";
   moreHours = new BigDecimal(0)
   OTHours = new BigDecimal(0)
   TotalOTHours = new BigDecimal(0)
   PrevOT = new BigDecimal(0)
   def oth
    def days = 0;   //parsing incoming payload 
  def payload = new XmlParser().parseText(body);
    
   
    payload.EmployeeTimeSheet.each{
        timeSheet ->
        // Check if HeaderValue is not empty before parsing
        oth = HeaderValue ? new XmlParser().parseText(HeaderValue)
              .'**'
              .find{it.startDate == timeSheet.startDate.text() && it.userId == timeSheet.userId.text()}?.overtimehours?.text() ?: 0 : 0
        ppStartDate = timeSheet.startDate.text()
        ppEndDate = timeSheet.endDate.text()    
        PrevOT = new BigDecimal(oth)

        timeSheet.employeeTimeValuationResult.each{
            valResult ->
            valResult.EmployeeTimeValuationResult.each{
                node ->

                if(node.payTypeName.text() == "USA_01_Z_OEH"){
                    bookingDateStr = node.bookingDate.text().substring(0,10)
                    if(ppStartDate <= bookingDateStr && bookingDateStr <= ppEndDate){
                        if(node.approvalStatus.text() == "APPROVED"){
                            days += 1;
                            println("\n BookingDate: " + bookingDateStr)
                            println("\n HoursMatched: " + node.hours.text())
                            moreHours = new BigDecimal(node.hours.text())
                            OTHours = OTHours + moreHours?.plus()
                            println("\n OtHours Acc: " + OTHours)
                        }
                    } 
                }
            }
        } 
        println("\n Prev OT:" + PrevOT.toString())
        OTHours = OTHours - 40;
        OTHours = OTHours - PrevOT;
        if(OTHours > 0){
            TotalOTHours = TotalOTHours + OTHours
        }  
        OTHours = new BigDecimal(0);
        moreHours = new BigDecimal(0);
        println("\n Current OT Hours: " + TotalOTHours.toString())
    }
   
    println("\n Total OT Hours: " + TotalOTHours.toString())
    return message;
}

// Use to set test data
def Message preLoad (Message message){
    def xmlContent = new File("othours_512845.xml").text
    def otHeader = new File("otheader.xml").text
    message.setBody(xmlContent)
    message.setHeader("PPStartDate", "2025-03-23")
    message.setHeader("PPEndDate", "2025-04-07")
    message.setHeader("OTHeader", otHeader)
    
}