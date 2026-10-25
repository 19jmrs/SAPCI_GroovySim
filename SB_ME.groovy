import Message;
import java.util.HashMap;
import groovy.xml.XmlUtil;
import groovy.xml.XmlParser;
import groovy.xml.MarkupBuilder;
import java.math.MathContext;

processData(new Message())

def Message processData(Message message) 
{
	message = preLoad(message)
	def body = (String)message.getBody();
	
	//parsing incoming payload
	def payload = new XmlParser().parseText(body);

	map = message.getProperties();
	def MULTI_EMPLOYMENTS_SAME_COMPANY = "FALSE";
	def str = "";
	def companyjobcurrent = ""
	def companyjobprvious = ""
	def end_dt = ""
	//looping through each employee
	payload.CompoundEmployee.each{CE ->
	int emp_count = CE.person.employment_information.size();
	def multi_exists = "FALSE";
	def PID;
	CE.person.each{PER->  
	PID = PER.person_id_external.text();
	int count = 0;
	
	// Sort employment_information entries in descending order by start_date
	// We need to access the NodeList's value and sort differently, as we can't directly assign to Node properties
	def sortedEmpInfo = PER.employment_information.toList().sort { a, b ->
		def aDate = a.start_date.text()
		def bDate = b.start_date.text()
		return bDate <=> aDate // Descending order
	}
	
	// Clear and repopulate the parent node with sorted entries
	def parent = PER.employment_information[0].parent()
	PER.employment_information.each { parent.remove(it) }
	sortedEmpInfo.each { parent.append(it) }

	// Check for multiple active employment_information entries in the same company
	def currentDate = new Date().format("yyyy-MM-dd")
	def activeEmploymentsByCompany = [:]
	
	PER.employment_information.each { EMP ->
		def endDate = EMP.end_date.text()
		def isActive = (endDate == "" || endDate == "null" || endDate == "9999-12-31" || endDate > currentDate)
		
		if (isActive) {
			// Get the most recent job_information by start_date
			def activeJobInfo = EMP.job_information.toList().sort { a, b ->
				def aDate = a.start_date.text()
				def bDate = b.start_date.text() 
				return bDate <=> aDate // Descending order by start_date
			}.find { jobInfo ->
				def jobEndDate = jobInfo.end_date.text()
				return (jobEndDate == "" || jobEndDate == "null" || jobEndDate == "9999-12-31" || jobEndDate > currentDate)
			} ?: EMP.job_information[0] // Fallback to first job_information if no active one found
			println "Active Job Info: " + activeJobInfo.company.text()
			println "Active Job Info: " + activeJobInfo.start_date.text()
			println "Active Job Info: " + activeJobInfo.end_date.text()
			// Use company from active job_information
			def company = activeJobInfo.company.text()
			if (!activeEmploymentsByCompany.containsKey(company)) {
				activeEmploymentsByCompany[company] = 0
			}
			activeEmploymentsByCompany[company]++
		}
	}
	
	// Check if any company has more than one active employment
	multi_exists = "FALSE"
	// Renamed parameter 'count' to 'empCount' to avoid conflict with existing variable
	activeEmploymentsByCompany.each { company, empCount ->
		if (empCount >= 2) {
			multi_exists = "TRUE"
		}
	}
		}
		if(multi_exists == "TRUE")
		{
			str = str+"\n"+PID;
			CE.replaceNode{};
		}
	}
	
	message.setProperty("MULTI_EMPLOYMENTS",str);
	message.setBody(XmlUtil.serialize(payload));
	println "MULTI_EMPLOYMENTS: " + str + "\n"
	return message;
}

// Use to set test data
def Message preLoad (Message message){
    def xmlContent = new File("testData.xml").text
    message.setBody(xmlContent)
    return message
}
