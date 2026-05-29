package org.schabi.newpipe.extractor.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class LogUtil {

    public static void logTree(String tag) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            StackTraceElement current = stack[3];
            String className = current.getClassName();
            String methodName = current.getMethodName();
            int lineNumber = current.getLineNumber();
            System.out.println(tag + " ==> " + className + "#" + methodName + " (line " + lineNumber + ")");
        } else {
            System.out.println(tag + " ==> <unknown>");
        }
    }

    public static void logTreeDeep(String tag, int depth) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        System.out.println(tag + " ==> Call stack:");

        for (int i = 3; i < 3 + depth && i < stack.length; i++) {
            StackTraceElement e = stack[i];
            String className = e.getClassName();
            String methodName = e.getMethodName();
            int lineNumber = e.getLineNumber();
            String fileName = e.getFileName();
            //String fullPath = getFullPath(className);

            System.out.println("  ↳ "
                                + className
                                + "#"
                                + methodName
                                + " (file:"
                                + fileName
                                + ", line:"
                                + lineNumber
                                //+ ", path:"
                                //+ fullPath
                                + ")"
                                );
        }
    }

    public static void logWithMessage(String tag, String message) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            StackTraceElement current = stack[3];
            String className = current.getClassName();
            String methodName = current.getMethodName();
            int lineNumber = current.getLineNumber();
            System.out.println(tag + " ==> " + className + "#" + methodName + " (line " + lineNumber + ") : " + message);
        } else {
            System.out.println(tag + " ==> <unknown>: " + message);
        }
    }

    // [Note] Debugging was unsuccessful.
    // 1) This method attempts to retrieve the full file path of the class.
    // 2) However, in Android, due to class loading and resource
    //    handling limitations, this method is likely to fail
    //    and won't return the expected results.
    // 3) It seems Android doesn't support retrieving the absolute
    //    class file path directly like traditional Java environments.
    private static String getFullPath(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            URL resource = clazz.getResource(clazz.getSimpleName() + ".class");

            if (resource != null) {
                try {
                    File file = new File(resource.toURI());
                    return file.getAbsolutePath();  // Return the absolute path of the class file
                } catch (URISyntaxException e) {
                    return "Invalid URI format: " + resource.toString();
                }
            } else {
                return getClassLocation(clazz);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return "Class not found";
        }
    }

    // [Note] Debugging was unsuccessful.
    //        Always return "Code source not available"!
    // 1) This method attempts to get the class location,
    //   i.e., where the class is loaded from.
    // 2) In Android, the class location might not be accessible
    //   due to the environment's unique handling of classes.
    // 3) If the class location can be accessed,
    //   it will return the path; otherwise, it will return a message
    //   indicating failure.
    private static String getClassLocation(Class<?> clazz) {
        if (clazz == null)
            return "Class is null";

        try {
            if (clazz.getProtectionDomain() != null && clazz.getProtectionDomain().getCodeSource() != null) {
                URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
                if (location != null)
                    return location.getPath();
                else
                    return "path not found";
            } else {
                return "Code source not available";
            }
        } catch (Exception e) {
            return "Failed to get class location: " + e.getMessage();
        }
    }
}

