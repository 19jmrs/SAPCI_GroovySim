import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.xml.XmlUtil;
import groovy.util.XmlParser;
import groovy.xml.MarkupBuilder;

def Message processData(Message message) {
    def body = message.getBody(java.lang.String)
    def ppStartDate = ""
    def ppEndDate = ""
    String bookingDateStr = ""
    moreHours = new BigDecimal(0)
    OTHours = new BigDecimal(0)
    TotalOTHours = new BigDecimal(0)
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
    def otSplitFirstWeek = new BigDecimal(0)
	def fullWeekHours = new BigDecimal(0) //added
	def fullWeekPH = new BigDecimal(0) //added
    def splitFirstWeekPHBeforePP = new BigDecimal(0)  

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
                    
                    // ============================================================
                    // FIRST PASS - Detect PTO across entire week
                    // ============================================================
                    def hasPTO = false
                    timesheet.employeeTimeValuationResult.each { valResult ->
                        valResult.EmployeeTimeValuationResult.each { node ->
                            bookingDateStr = node.bookingDate.text().substring(0,10)
							// Check if PTO exists anywhere in the entire week
                            if (startDate <= bookingDateStr && bookingDateStr <= endDate) {
                                if (node.payTypeName.text() == "USA_PTO_BER" && 
                                    node.approvalStatus.text() == "APPROVED") {
                                    hasPTO = true
                                }
                            }
                        }
                    }
                    // ============================================================
                    
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
                        
                        // ============================================================
                        // OVERTIME CALCULATION - Only for regular hours if !hasPTO
                        // PH hours are ALWAYS added (independent stream)
                        // ============================================================
                        
                        // Special handling for split first week with worked hours after ppStartDate
                        if (isSplitFirstWeek && hasWorkedHoursAfterPPStart && !hasPTO) {
                            // Sort bookings by date
                            bookings.sort { a, b -> a.date <=> b.date }
                            
                            // Calculate total PH hours within pay period for threshold adjustment
                            def phWithinPeriod = new BigDecimal(0)
                            bookings.each { booking ->
                                if (booking.type == 'ph' && booking.date >= ppStartDate && booking.date <= ppEndDate) {
                                    phWithinPeriod += booking.hours
                                }
                            }
                            
                            def adjustedThreshold = 40 //- phWithinPeriod
                            
                            // Calculate running total and identify overtime after ppStartDate
                            def runningTotal = new BigDecimal(0)
                            def overtimeAfterPPStart = new BigDecimal(0)
                            def phBeforePPStart = new BigDecimal(0)                                                                                         
                            bookings.each { booking ->
                                // Only process regular hours for threshold calculation
                                if (booking.type == 'regular') {
                                    def hoursBeforeThreshold = new BigDecimal(0)
                                    def hoursAfterThreshold = new BigDecimal(0)
                                    
                                    if (runningTotal < adjustedThreshold) {
                                        // Check if this booking crosses the adjusted threshold
                                        def spaceToThreshold = adjustedThreshold - runningTotal
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

                                if (booking.type == 'ph' && booking.date < ppStartDate) {                                                                   
                                    phBeforePPStart += booking.hours                                                                                        
                                }    
                                // PH hours don't count toward threshold
                            }
                            splitFirstWeekPHBeforePP += phBeforePPStart
                            
                            // Calculate total overtime for the entire week (including before ppStartDate)
                            def totalWeekOT = OTHours - adjustedThreshold
                            
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
                            
                        } else if (!hasPTO) {
                            // Normal week or split first week without work after ppStartDate
                            // Only calculate if no PTO
                            def adjustedThreshold = 40 //- ph
                            OTHours = OTHours - adjustedThreshold
                            
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
                        }
                        
                        // ALWAYS add PH hours (independent of PTO)
                        TotalOTHours = TotalOTHours + ph

                    } else if(endDate > ppEndDate){
                        // Split last week processing
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
                        
                        // ============================================================
                        // OVERTIME CALCULATION - Only for regular hours if !hasPTO
                        // ============================================================
                        if (!hasPTO) {
                            def adjustedThreshold = 40 //- ph
                            OTHours = OTHours - adjustedThreshold//If adjustedThreshold
                            
                            if(OTHours > 0 && isSplitLastWeek){
                                splitWeek = "last"
                                message.setHeader("splitWeek", splitWeek)
                                message.setHeader("splitLastWeek", OTHours)
                                splitWeek = "none"
                                hasLastWeekSplit = true
                                TotalOTHours = TotalOTHours + OTHours
                            }
                            overTimeBeforePPEndDate = OTHours
                        }
                        
                        // ALWAYS add PH hours (independent of PTO)
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
    removeFromRegular = removeFromRegular - splitFirstWeekPHBeforePP

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
        // Only adjust if PTO doesn't exist (otSplitFirstWeek only set when !hasPTO)
        if (otSplitFirstWeek > 0) {
            removeFromRegular = removeFromRegular - otSplitFirstWeek
        }
    }
    
    if(hasFirstWeekSplit && hasWorkedHoursAfterPPStart){
        removeFromRegular = removeFromRegular - overtimeBeforePPStart
    }
   
    message.setProperty("TotalOTHours", TotalOTHours)
    message.setProperty("removeFromRegular", removeFromRegular)
    return message
}