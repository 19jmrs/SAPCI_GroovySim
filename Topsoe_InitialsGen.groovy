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
    

    //Get properties
    def firstName = message.getProperty('firstName').toUpperCase().trim()
    def middleName = message.getProperty('middleName').toUpperCase().trim()
    def lastName = message.getProperty('lastName').toUpperCase().trim()
    def camelLoopIndex = message.getProperty('CamelLoopIndex').toInteger();

    if (camelLoopIndex == 0) { // Rule #1: First rule to generate the initials is to get 2 characters from First Name and 2 from Last Name (If it exist then follow rule 2)
        if(firstName.length() < 2 || lastName.length() < 2) {
            message.setProperty('NewUsername', '');
        }
        
        else {
            
            def firstTwoCharactersFirstName = firstName.substring(0,2);
            def firstTwoCharactersLastName = lastName.substring(0,2);
            
            firstTwoCharactersFirstName = replaceSpecialCharacters(firstTwoCharactersFirstName, firstName);
            firstTwoCharactersLastName = replaceSpecialCharacters(firstTwoCharactersLastName, lastName);

            def newUsername = firstTwoCharactersFirstName.concat(firstTwoCharactersLastName);
            println("Entrou: " + newUsername)
            message.setProperty('NewUsername', newUsername.toUpperCase());
        }
        
    }
    else if (camelLoopIndex == 1) { // Rule #2: Second rule to generate the initials is to get 1 character from First Name and 3 from Last Name (If it exist then follow rule 3)
        if(firstName.length() < 1 || lastName.length() < 3) {
            message.setProperty('NewUsername', '');
        }
        
        else {
            def firstCharacterFirstName = firstName.substring(0,1);
            def firstThreeCharactersLastName = lastName.substring(0,3);
            
            firstCharacterFirstName = replaceSpecialCharacters(firstCharacterFirstName, firstName);
            firstThreeCharactersLastName = replaceSpecialCharacters(firstThreeCharactersLastName, lastName);

            def newUsername = firstCharacterFirstName.concat(firstThreeCharactersLastName);
            
            
            message.setProperty('NewUsername', newUsername.toUpperCase());
        }
        
    }
    else if (camelLoopIndex == 2) { // Rule #3: Third rule to generate the initials is to get 3 characters from First Name and 1 from Last Name (If it exist then follow rule 4)
        if(firstName.length() < 3 || lastName.length() < 1) {
            message.setProperty('NewUsername', '');
        }
        
        else {
            def firstThreeCharactersFirstName = firstName.substring(0,3);
            def firstCharacterLastName = lastName.substring(0,1);
            
            firstThreeCharactersFirstName = replaceSpecialCharacters(firstThreeCharactersFirstName, firstName);
            firstCharacterLastName = replaceSpecialCharacters(firstCharacterLastName, lastName);
            def newUsername = firstThreeCharactersFirstName.concat(firstCharacterLastName);
            
            
            message.setProperty('NewUsername', newUsername.toUpperCase());
        }
        
    }
    else if (camelLoopIndex == 3) { // Rule #4: Forth rule to generate the initials is to get 2 characters from First Name , 1 from Middle Name and 1 from Last Name (If it exist then follow rule 5)
        if(firstName.length() < 2 || middleName.length() < 1 || lastName.length() < 1) {
            message.setProperty('NewUsername', '');
        }
        
        else {
            def firstTwoCharactersFirstName = firstName.substring(0,2);
            def firstCharacterMiddleName = middleName.substring(0,1);
            def firstCharacterLastName = lastName.substring(0,1);
            
            firstTwoCharactersFirstName = replaceSpecialCharacters(firstTwoCharactersFirstName, firstName);
            firstCharacterMiddleName = replaceSpecialCharacters(firstCharacterMiddleName, middleName);
            firstCharacterLastName = replaceSpecialCharacters(firstCharacterLastName, lastName);
            def newUsername = firstTwoCharactersFirstName.concat(firstCharacterMiddleName).concat(firstCharacterLastName);
            
            
            message.setProperty('NewUsername', newUsername.toUpperCase());
        }
        
    }
    else if (camelLoopIndex == 4) { // Rule #5: Fifth rule to generate the initials is to get 1 characters from First Name and 1 from Middle Name (if exist else add Z) and 2 from Last Name
        if(firstName.length() < 1 || lastName.length() < 2) {
            message.setProperty('NewUsername', '');
        }
        
        else {
            def firstCharacterFirstName = firstName.substring(0,1);
            def firstCharacterMiddleName = middleName != '' ? middleName.substring(0,1) : 'Z';
            def firstTwoCharactersLastName = lastName.substring(0,2);
            
            firstCharacterFirstName = replaceSpecialCharacters(firstCharacterFirstName, firstName);
            firstCharacterMiddleName = replaceSpecialCharacters(firstCharacterMiddleName, middleName);
            firstTwoCharactersLastName = replaceSpecialCharacters(firstTwoCharactersLastName, lastName);
            def newUsername = firstCharacterFirstName.concat(firstCharacterMiddleName).concat(firstTwoCharactersLastName);
           
            
            message.setProperty('NewUsername', newUsername.toUpperCase());
        }
        
    }
    else {
        message.setProperty('NewUsername', '');
    }
    println(message.getProperty('NewUsername'))
    return message;
}


// Replace special characters with next available character that wasn't in original initials, considering case sensitivity
def replaceSpecialCharacters(String initials, String fullName) {
    def specialChars = ['-', ' ', '.', '_','`','´' ,'@', '#', '$', '%', '^', '&', '*', '(', ')', '+', '=', '{', '}', '[', ']', ':', ';', '"', "'", '<', '>', ',', '?', '/', '\\', 'Æ'];
    
    def result = initials
    
    // Get all non-special characters from original initials (case-insensitive)
     specialChars.each { specialChar ->
        if (result.contains(specialChar)) {
            def currentInitials = result.replace(specialChar, '')
            def searchIndex = 0
            
            // Find position after current initials in fullName
            currentInitials.each { initial ->
                def foundIndex = fullName.toUpperCase().indexOf(initial.toString(), searchIndex)
                if (foundIndex != -1) {
                    searchIndex = foundIndex + 1
                }
            }
            
            // Find next available alphabetic character
            while (searchIndex < fullName.length()) {
                def nextChar = fullName[searchIndex].toUpperCase()
                if (nextChar.matches(/[A-Z]/)) {
                    result = result.replace(specialChar, nextChar)
                    break
                }
                searchIndex++
            }
        }
    }
    return result
}

// Use to set test data
def Message preLoad (Message message){
    message.setProperty("CamelLoopIndex", '4');
    message.setProperty("firstName", "Jo-Ann");
    message.setProperty("middleName", "Eve");
    message.setProperty("lastName", "Smith");

    return message;
    
}