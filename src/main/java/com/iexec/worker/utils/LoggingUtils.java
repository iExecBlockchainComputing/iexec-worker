package com.iexec.worker.utils;


public class LoggingUtils {

    public static String getHighlightedMessage(String message) {
        String hashtagSequence = new String(new char[message.length()]).replace('\0', '#');
        String spaceSequence = new String(new char[message.length()]).replace('\0', ' ');

        return "\n" +
                "##" + hashtagSequence  + "##\n" +
                "# " + spaceSequence    + " #\n" +
                "# " + message          + " #\n" +
                "# " + spaceSequence    + " #\n" +
                "##" + hashtagSequence  + "##\n" +
                "\n";
    }

    public static String getHeaderFooterHashMessage(String message) {
        String hashtagSequence = new String(new char[message.length()]).replace('\0', '#');

        return "\n" +
                "##" +  hashtagSequence  + "##\n" +
                        message          + "\n" +
                "##" +  hashtagSequence  + "##\n" +
                "\n";
    }

    public static void printHighlightedMessage(String message) {
        System.out.println(getHighlightedMessage(message));
    }

    public static String prettifyDeveloperLogs(String iexecInTree, String iexecOutTree, String stdout) {
        return "\n" +
                "#################### DEV MODE ####################\n" +
                "iexec_in folder\n" +
                "--------------------\n" +
                iexecInTree + "\n" +
                "\n" +
                "iexec_out folder\n" +
                "--------------------\n" +
                iexecOutTree + "\n" +
                "\n" +
                "stdout file\n" +
                "--------------------\n" +
                stdout + "\n" +
                "#################### DEV MODE ####################\n" +
                "\n";
    }


}