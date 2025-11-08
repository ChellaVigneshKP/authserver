package com.chellavignesh.authserver.adminportal.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {
    public static String getISO8601Date(Date date) {
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        return sdf.format(date);
    }
}
