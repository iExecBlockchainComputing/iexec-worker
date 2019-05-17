package com.iexec.worker.utils;

import java.util.Collections;

public class LoggingUtils {


    public static void printHighlightedMessage(String message) {
        String hashtagSequence = String.join("", Collections.nCopies(message.length() + 2, "#"));
        String spaceSequence = String.join("", Collections.nCopies(message.length(), " "));

        System.out.println();
        System.out.println("#"  +   hashtagSequence    + "#");
        System.out.println("# " +    spaceSequence     + " #");
        System.out.println("# " +       message        + " #");
        System.out.println("# " +    spaceSequence     + " #");
        System.out.println("#"  +   hashtagSequence    + "#");
        System.out.println();
    }
}