import Message;
import java.util.HashMap;
import javax.xml.xpath.XPathFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import org.w3c.dom.NodeList;
import javax.xml.xpath.XPathConstants;
import groovy.json.*;

processData(new Message())

def Message processData(Message message) 
{
   message = preLoad(message)
    def jsonbody = (String)message.getBody();
    def parsedJson = new JsonSlurper().parseText(jsonbody)
    def fromDate  = new Date().plus(0).format("yyyy-MM-dd'T'00:00:00'Z'");
    
     parsedJson.User.each
    {
   
        // Move Current Manager Field
        it.managerUserId = '';
        if (it.manager != null && it.manager.isEmpty() == false)
        {
            it.managerUserId = it.manager.User?.userId;
        }
        // it.managerUserId = it.manager.User.userId;

        // Move EmpInfo Fields
        it.empInfoSeniorityDate    = it.empInfo.EmpEmployment.seniorityDate;
        it.empInfoCustomString1    = it.empInfo.EmpEmployment.customString1;
        it.empInfoEndDate          = it.empInfo.EmpEmployment.endDate;
        it.empInfoPersonIdExternal = it.empInfo.EmpEmployment.personIdExternal;
        it.empInfoLastDateWorked   = it.empInfo.EmpEmployment.lastDateWorked;
        println(it.empInfo.EmpEmployment.personIdExternal)
        if( it.empInfo.EmpEmployment.personNav.PerPerson.emailNav == null || it.empInfo.EmpEmployment.personNav.PerPerson.emailNav.isEmpty()){
            it.otherEmail = '';
        } else { 
            it.otherEmail = it.empInfo.EmpEmployment.personNav.PerPerson.emailNav?.PerEmail?.emailAddress?:'';
        }
        if( it.empInfo.EmpEmployment.personNav.PerPerson.phoneNav == null || it.empInfo.EmpEmployment.personNav.PerPerson.phoneNav.isEmpty()){
            it.otherPhone = '';
        } else { 
            
            it.otherPhone = it.empInfo.EmpEmployment.personNav.PerPerson.phoneNav?.PerPhone?.phoneNumber?:'';   
        }
        //choose the workOrder with the biggest endDate
        it.vendor = '';
        def latestWorkOrder = null
        
        // Access the list of work orders safely
        def workOrderNavHolder = it.empInfo?.EmpEmployment?.workOrderNav

        // Check if workOrderNav is a Map before accessing its WorkOrder property to prevent MissingPropertyException.
        if (workOrderNavHolder instanceof Map) {        
            // Ensure workOrdersList is always a list, even if WorkOrder is a single object from JSON
            def rawWorkOrderData = workOrderNavHolder.WorkOrder // Previously problematic line, now safe
            List workOrdersListToProcess = [] // Initialize as an empty list

            if (rawWorkOrderData != null) {
                if (rawWorkOrderData instanceof List) {
                    workOrdersListToProcess = rawWorkOrderData
                } else if (rawWorkOrderData instanceof Map && !rawWorkOrderData.isEmpty()) {
                    // If it's a single map (not empty), wrap it in a list
                    workOrdersListToProcess = [rawWorkOrderData]
                }
              
            }
        
            // Check if workOrdersListToProcess is not empty before iterating
            if (!workOrdersListToProcess.isEmpty()) {
                workOrdersListToProcess.each { workOrder -> // workOrder is now definitely a Map from the list
                
                    if (workOrder.endDate) { // Ensure endDate exists in the workOrder map
                        if (latestWorkOrder == null) {
                            latestWorkOrder = workOrder
                        } else {
                            // Compare dates lexicographically
                            if (workOrder.endDate > latestWorkOrder.endDate) {
                                latestWorkOrder = workOrder
                            }
                        }
                    }
                }
            }
            // Assign cust_vendor from the determined latestWorkOrder
            it.vendor = latestWorkOrder?.cust_vendor ?: '';
        }
        
        def keepRecord;
        Boolean isArray = it.empInfo.EmpEmployment.jobInfoNav.EmpJob.startDate[0].length() != 1;
        // Move JobInfo
        if (isArray)
        {
            // Locate the earliest record valid from today ("fromDate")
            it.empInfo.EmpEmployment.jobInfoNav.EmpJob.each
            {
                if (keepRecord == null) 
                {
                    keepRecord = it;
                }
                else 
                {
                  //if ((it.startDate > keepRecord.startDate) && (it.startDate <= fromDate && it.endDate >= fromDate))
                  if ( (it.startDate <= fromDate && it.endDate >= fromDate) 
                       && 
                       ( 
                         (it.startDate > keepRecord.startDate) || (it.startDate == keepRecord.startDate && it.endDate > keepRecord.endDate)
                       )
                     )

                     keepRecord = it;
                }
            }
        }
        else
        {
            // only one record, so that's the one
            keepRecord = it.empInfo.EmpEmployment.jobInfoNav.EmpJob;
        }
            

        // Transfer record
        if (keepRecord != null)
        {
            it.empJobStartDate     = keepRecord.startDate;
            it.empJobEndDate       = keepRecord.endDate;
            // it.empJobManagerId     = keepRecord.managerId;  // Deleted 
            it.empJobCompany       = keepRecord.company;
            it.empJobBusinessUnit  = keepRecord.businessUnit;
            it.empJobDepartment    = keepRecord.department;
            it.empJobLocation      = keepRecord.location;
            it.empJobCostCenter    = keepRecord.costCenter;
            it.empJobPosition      = keepRecord.position;
            
            if (it.department != null && it.department != 'N/A')
              it.orgUnit = it.department;
            else if (it.division != null && it.division != 'N/A')
              it.orgUnit = it.division;
            else if (it.custom05 != null && it.custom05 != 'N/A')
              it.orgUnit = it.custom05;
            else
              it.orgUnit = '';
              
            it.empJobEmployeeGroup = keepRecord.customString5Nav.cust_employeeGroup.externalCode;
            it.empJobEmployeeType  = keepRecord.customString6Nav.cust_employeeSubGroup.cust_employeeType;
        }
        
        
        it.cust_building = '';
        it.cust_RoomNumber = '';

        // Safely access cust_Builiding and cust_RoomNumber after checking if the path is valid and objects are Maps
        def workLocationNav = it.externalCodeOfcust_work_locationNav
        if (workLocationNav instanceof Map) {
            def workLocation = workLocationNav.cust_work_location
            if (workLocation instanceof Map) {
                if(workLocation.containsKey("cust_Builiding"))
                {
                    it.cust_building = workLocation.cust_Builiding;
                }
                if(workLocation.containsKey("cust_RoomNumber"))
                {
                    it.cust_RoomNumber = workLocation.cust_RoomNumber;
                }
            }
        }

        // Add BusinessPartner 
        it.BusinessPartner = '';
        if (it.containsKey("IAM_BUSINESS_PARTNER") == true)
        {
            it.BusinessPartner = it.IAM_BUSINESS_PARTNER.Partnerno;
            it.remove("IAM_BUSINESS_PARTNER");
        }
        
        // Cleanup
        it.remove("empInfo")
        it.remove("manager");
        it.remove("externalCodeOfcust_work_locationNav");
    }

    // Convert back to JSON
    message.setBody(new JsonBuilder(parsedJson).toString());
    //write body to file
    def outputFile = new File("topsoeiamOutput.json");
    outputFile.text = message.getBody();
    
    return message;
}

// Use to set test data
def Message preLoad (Message message){
    def xmlContent = new File("topsoeiam.json").text
    message.setBody(xmlContent)
    
}