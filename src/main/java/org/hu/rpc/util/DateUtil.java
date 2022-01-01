package org.hu.rpc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/30 5:09 PM
 **/
public class DateUtil {
    private static Logger log = LoggerFactory.getLogger(DateUtil.class);

    /**
     * 时间转成字符串
     *
     * @param date
     * @return
     */
    public static String dateToStr(Date date) {
        DateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return f.format(date);
    }


    /**
     * 计算两个时间的差值
     * @param newdate 当前时间
     * @param enddate 自定义的时间
     * @return 返回相差的毫秒数
     */
    public static long dateMinusDate(Date newdate, String enddate) {
        DateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date parse = f.parse(enddate);
            long diff = newdate.getTime() - parse.getTime();
            return diff;
        } catch (ParseException e) {
            log.error("计算两个时间的差值失败：{}",e);
        }
        return -1;
    }


    /**
     * 对集合中的时间进行排序
     *
     * @param list
     */
    public static void sort(List<String> list) {
        Collections.sort(list, new Comparator<String>() {
            DateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            @Override
            public int compare(String o1, String o2) {

                try {
                    return f.parse(o1).compareTo(f.parse(o2));
                } catch (ParseException e) {
                    log.error("对两个时间进行比较失败：{}",e);
                }
                return 1;
            }
        });
    }
}
