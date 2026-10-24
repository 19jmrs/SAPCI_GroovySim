import Message;
import java.util.HashMap;
import groovy.xml.XmlUtil;
import groovy.xml.XmlParser;
import groovy.xml.MarkupBuilder;

processData(new Message())

def Message processData(Message message) 
{  
    message = preLoad(message) 
    def body = message.getBody()
    def ppStartDate = ""
    def ppEndDate = ""
    String bookingDateStr = ""
    BigDecimal moreHours = new BigDecimal(0)
    BigDecimal OTHours = new BigDecimal(0)
    BigDecimal TotalOTHours = new BigDecimal(0)
    BigDecimal ph = new BigDecimal(0)
    BigDecimal moreHoursph = new BigDecimal(0)
    def payload = new XmlParser().parseText(body)
    def startDate = ""
    def endDate = ""
    def splitWeek = "none"
    def hasFirstWeekSplit = false
    def hasLastWeekSplit = false
    def hasWorkedHoursAfterPPStart = false
    def overtimeBeforePPStart = new BigDecimal(0)
    def removeFromRegular = new BigDecimal (0)
    def overTimeBeforePPEndDate = new BigDecimal(0)


    payload.children().each { messageNode ->
        if (messageNode.name().toString().contains("Message1")) {
            messageNode.root.row.each { row ->
                ppStartDate = row.PPStartDate.text()
                ppEndDate = row.PPEndDate.text()
            }
        } else if (messageNode.name().toString().contains("Message2")) {
            messageNode.EmployeeTimeSheet.each { outerTimesheet ->
                outerTimesheet.EmployeeTimeSheet.each { timesheet ->

                    startDate = timesheet.startDate.text().substring(0,10)
                    endDate = timesheet.endDate.text().substring(0,10)
                    
                    def isSplitFirstWeek = (startDate < ppStartDate)
                    def isSplitLastWeek = (endDate > ppEndDate)
                    
                    if (!(endDate > ppEndDate)) {
                        
                        // Collect all bookings with their dates and hours for chronological processing
                        def bookings = []

                        timesheet.employeeTimeValuationResult.each { valResult ->
                            valResult.EmployeeTimeValuationResult.each { node ->

                                if (node.payTypeName.text() == "USA_01_Z_OEH") {
                                    bookingDateStr = node.bookingDate.text().substring(0,10)
                                    def shouldIncludeBooking = false

                                    if (startDate < ppStartDate) {
                                        if (startDate <= bookingDateStr && bookingDateStr <= ppEndDate) {
                                            shouldIncludeBooking = true
                                            // Check if there are worked hours after ppStartDate in split first week
                                            if (bookingDateStr >= ppStartDate && node.approvalStatus.text() == "APPROVED") {
                                                def hours = new BigDecimal(node.hours.text())
                                                if (hours > 0) {
                                                    hasWorkedHoursAfterPPStart = true
                                                }
                                            }
                                        }
                                    } else {
                                        if (ppStartDate <= bookingDateStr && bookingDateStr <= endDate) {
                                            shouldIncludeBooking = true
                                        }
                                    }

                                    if (shouldIncludeBooking && node.approvalStatus.text() == "APPROVED") {
                                        moreHours = new BigDecimal(node.hours.text())
                                        OTHours = OTHours + moreHours?.plus()
                                        
                                        // Store booking for chronological processing
                                        bookings.add([
                                            date: bookingDateStr,
                                            hours: moreHours,
                                            type: 'regular'
                                        ])
                                    }

                                } else if (node.payTypeName.text() == "USA_TH4_D_H_CT" || node.payTypeName.text() == "USA_TH4_D_H") {
                                    bookingDateStr = node.bookingDate.text().substring(0,10)
                                    def shouldIncludeBooking = false

                                    if (startDate < ppStartDate) {
                                        if (startDate <= bookingDateStr && bookingDateStr <= ppEndDate) {
                                            shouldIncludeBooking = true
                                            // Check if there are worked hours after ppStartDate in split first week
                                            if (bookingDateStr >= ppStartDate && node.approvalStatus.text() == "APPROVED") {
                                                def hours = new BigDecimal(node.hours.text())
                                                if (hours > 0) {
                                                    hasWorkedHoursAfterPPStart = true
                                                }
                                            }
                                        }
                                    } else {
                                        if (ppStartDate <= bookingDateStr && bookingDateStr <= endDate) {
                                            shouldIncludeBooking = true
                                        }
                                    }

                                    if (shouldIncludeBooking && node.approvalStatus.text() == "APPROVED") {
                                        moreHoursph = new BigDecimal(node.hours.text())
                                        ph = ph + moreHoursph?.plus()
                                        
                                        // Store booking for chronological processing
                                        bookings.add([
                                            date: bookingDateStr,
                                            hours: moreHoursph,
                                            type: 'ph'
                                        ])
                                        moreHoursph = new BigDecimal(0)
                                    }
                                }
                            }
                        }
                        
                        // Special handling for split first week with worked hours after ppStartDate
                        if (isSplitFirstWeek && hasWorkedHoursAfterPPStart) {
                            // Sort bookings by date
                            bookings.sort { a, b -> a.date <=> b.date }
                            
                            // Calculate running total and identify overtime after ppStartDate
                            def runningTotal = new BigDecimal(0)
                            def overtimeAfterPPStart = new BigDecimal(0)
                            
                            bookings.each { booking ->
                                def hoursBeforeThreshold = new BigDecimal(0)
                                def hoursAfterThreshold = new BigDecimal(0)
                                
                                if (runningTotal < 40) {
                                    // Check if this booking crosses the 40-hour threshold
                                    def spaceToThreshold = 40 - runningTotal
                                    if (booking.hours > spaceToThreshold) {
                                        hoursBeforeThreshold = spaceToThreshold
                                        hoursAfterThreshold = booking.hours - spaceToThreshold
                                    } else {
                                        hoursBeforeThreshold = booking.hours
                                    }
                                } else {
                                    // Already past threshold, all hours are overtime
                                    hoursAfterThreshold = booking.hours
                                }
                                
                                runningTotal = runningTotal + booking.hours
                                
                                // If this booking is on or after ppStartDate and has overtime, count it
                                if (booking.date >= ppStartDate && hoursAfterThreshold > 0) {
                                    overtimeAfterPPStart = overtimeAfterPPStart + hoursAfterThreshold
                                }
                            }
                            
                            // Calculate total overtime for the entire week (including before ppStartDate)
                            def totalWeekOT = OTHours - 40
                            
                            // Calculate overtime before ppStartDate
                            if (totalWeekOT > 0) {
                                overtimeBeforePPStart = totalWeekOT - overtimeAfterPPStart
                            }
                            
                            // For split first week with work after ppStartDate
                            if (overtimeAfterPPStart > 0) {
                                splitWeek = "first"
                                message.setHeader("splitWeek", splitWeek)
                                message.setHeader("splitFirstWeek", overtimeAfterPPStart)
                                splitWeek = "none"
                                hasFirstWeekSplit = true
                            }
                            
                            // Set overtime before ppStartDate header
                            if (overtimeBeforePPStart > 0) {
                                message.setHeader("overtimeBeforePPStartDate", overtimeBeforePPStart)
                            }
                            
                            // Add ALL week overtime to TotalOTHours (including before ppStartDate)
                            if (totalWeekOT > 0) {
                                TotalOTHours = TotalOTHours + totalWeekOT
                            }
                            // Add public holiday hours
                            TotalOTHours = TotalOTHours + ph
                        } else {
                            // Normal week or split first week without work after ppStartDate
                            OTHours = OTHours - 40
                            if(OTHours > 0 && !isSplitLastWeek){
                                TotalOTHours = TotalOTHours + OTHours
                            }
                            
                            if(OTHours > 0 && isSplitFirstWeek){
                                splitWeek = "first"
                                message.setHeader("splitWeek", splitWeek)
                                message.setHeader("splitFirstWeek", OTHours)
                                splitWeek = "none"
                                hasFirstWeekSplit = true
                                otSplitFirstWeek = OTHours
                            }
                            
                            TotalOTHours = TotalOTHours + ph
                        }

                    } else if(endDate > ppEndDate){
                        timesheet.employeeTimeValuationResult.each { valResult ->
                            
                            valResult.EmployeeTimeValuationResult.each { node ->
                                
                                if (node.payTypeName.text() == "USA_01_Z_OEH") {
                                    bookingDateStr = node.bookingDate.text().substring(0,10)
                                    def shouldIncludeBooking = false

                                    if (startDate < ppStartDate) {
                                        if (startDate <= bookingDateStr && bookingDateStr <= ppEndDate) {
                                            shouldIncludeBooking = true
                                        }
                                    } else {
                                        if (ppStartDate <= bookingDateStr && bookingDateStr <= ppEndDate) {
                                            shouldIncludeBooking = true
                                        }
                                    }

                                    if (shouldIncludeBooking && node.approvalStatus.text() == "APPROVED") {
                                        moreHours = new BigDecimal(node.hours.text())
                                        OTHours = OTHours + moreHours?.plus()
                                        
                                    }

                                } else if (node.payTypeName.text() == "USA_TH4_D_H_CT" || node.payTypeName.text() == "USA_TH4_D_H") {
                                    bookingDateStr = node.bookingDate.text().substring(0,10)
                                    def shouldIncludeBooking = false

                                    if (startDate < ppStartDate) {
                                        if (startDate <= bookingDateStr && bookingDateStr <= ppEndDate) {
                                            shouldIncludeBooking = true
                                        }
                                    } else {
                                        if (ppStartDate <= bookingDateStr && bookingDateStr <= ppEndDate) {
                                            shouldIncludeBooking = true
                                        }
                                    }

                                    if (shouldIncludeBooking && node.approvalStatus.text() == "APPROVED") {
                                        moreHoursph = new BigDecimal(node.hours.text())
                                        ph = ph + moreHoursph?.plus()
                                        moreHoursph = new BigDecimal(0)
                                    }
                                }
                            }
                        }
                        
                        OTHours = OTHours - 40
                        if(OTHours > 0 && isSplitLastWeek){
                            splitWeek = "last"
                            message.setHeader("splitWeek", splitWeek)
                            message.setHeader("splitLastWeek", OTHours)
                            splitWeek = "none"
                            hasLastWeekSplit = true
                            TotalOTHours = TotalOTHours + OTHours
                        }
                        overTimeBeforePPEndDate = OTHours
                        TotalOTHours = TotalOTHours + ph

                    }

                    OTHours = new BigDecimal(0)
                    moreHours = new BigDecimal(0)
                    ph = new BigDecimal(0)
                }
            }
        }

    }
    
    removeFromRegular = TotalOTHours

    if(hasFirstWeekSplit && hasLastWeekSplit){
        message.setHeader("splitWeek", "both")
    }else if(hasFirstWeekSplit){
        message.setHeader("splitWeek", "first")
    } else if(hasLastWeekSplit){
        message.setHeader("splitWeek", "last")
    } 
    
    // Check if split first week exists but no worked hours after ppStartDate
    if(hasFirstWeekSplit && !hasWorkedHoursAfterPPStart){
        message.setHeader("firstWeekNotWorked", "true")
        removeFromRegular = removeFromRegular - otSplitFirstWeek //we don't want the OT
    }
    
    if(hasFirstWeekSplit && hasWorkedHoursAfterPPStart){
        removeFromRegular = removeFromRegular - overtimeBeforePPStart
    }
   
    //message.setProperty("TotalOTHours", TotalOTHours)
    //message.setProperty("removeFromRegular", removeFromRegular)
    println("TotalOTHours:" + TotalOTHours)
    println("removeFromRegular:" + removeFromRegular)

    return message
}

// Use to set test data
def Message preLoad (Message message){
    def xmlContent = new File("othours_512845.xml").text
    def otHeader = new File("otheader.xml").text
    message.setBody(xmlContent)
    message.setHeader("PPStartDate", "2026-01-23")
    message.setHeader("PPEndDate", "2026-02-07")
    //message.setHeader("OTHeader", otHeader)
    
}