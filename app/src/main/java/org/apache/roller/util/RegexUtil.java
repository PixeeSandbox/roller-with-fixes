/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Hex;


/**
 * Regular expressions utility class.
 */
public final class RegexUtil {
    
    public static final Pattern MAILTO_PATTERN =
            Pattern.compile("mailto:([a-zA-Z0-9\\.\\-]+@[a-zA-Z0-9\\.\\-]+\\.[a-zA-Z0-9]+)");

    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[a-zA-Z0-9\\.\\-]+(@)([a-zA-Z0-9\\.\\-]+)(\\.)([a-zA-Z0-9]+)\\b");


    public static String encodeEmail(String str) {
        // obfuscate mailto's: turns them into hex encoded,
        // so that browsers can still understand the mailto link
        Matcher mailtoMatch = MAILTO_PATTERN.matcher(str);
        while (mailtoMatch.find()) {
            String email = mailtoMatch.group(1);
            //System.out.println("email=" + email);
            String hexed = encode(email);
            str = str.replaceFirst(Pattern.quote("mailto:"+email), "mailto:"+hexed);
        }
        
        return obfuscateEmail(str);
    }
    
    
    /**
     * obfuscate plaintext emails: makes them
     * "human-readable" - still too easy for
     * machines to parse however.
     */
    public static String obfuscateEmail(String str) {
        Matcher emailMatch = EMAIL_PATTERN.matcher(str);
        while (emailMatch.find()) {
            String at = emailMatch.group(1);
            //System.out.println("at=" + at);
            str = str.replaceFirst(Pattern.quote(at), "-AT-");
            
            String dot = emailMatch.group(2) + emailMatch.group(3) + emailMatch.group(4);
            String newDot = emailMatch.group(2) + "-DOT-" + emailMatch.group(4);
            //System.out.println("dot=" + dot);
            str = str.replaceFirst(Pattern.quote(dot), newDot);
        }
        return str;
    }
    
    
    /**
     * Return the specified match "groups" from the pattern.
     * For each group matched a String will be entered in the ArrayList.
     *
     * @param pattern The Pattern to use.
     * @param match The String to match against.
     * @param group The group number to return in case of a match.
     * @return List of matched groups from the pattern.
     */
    public static List<String> getMatches(Pattern pattern, String match, int group) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(match);
        while (matcher.find()) {
            matches.add( matcher.group(group) );
        }
        return matches;
    }
    
    
    /**
     * Thanks to the folks at Blojsom (http://sf.net/projects/blojsom)
     * for showing me what I was doing wrong with the Hex class.
     *
     * @param email
     * @return
     */
    public static String encode(String email) {
        StringBuilder result = new StringBuilder(16);
        char[] hexString = Hex.encodeHex(email.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < hexString.length; i++) {
            if (i % 2 == 0) {
                result.append('%');
            }
            result.append(hexString[i]);
        }
        
        return result.toString();
    }
    
}
