package org.hu.rpc.zk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Enumeration;

/**
 * @Author: hu.chen
 * @Description: IP获取类
 * @DateTime: 2021/12/29 1:12 PM
 **/
public class IpUtils {
   private static Logger log = LoggerFactory.getLogger(IpUtils.class);

    /**
     * 获取本机ip地址
     **/
    public static String getLocalIpAddr() {

        String sIP = "";
        InetAddress ip = null;
        try {
            boolean bFindIP = false;
            Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
                if (bFindIP) {
                    break;
                }
                NetworkInterface ni = netInterfaces.nextElement();
                Enumeration<InetAddress> ips = ni.getInetAddresses();
                while (ips.hasMoreElements()) {
                    ip = ips.nextElement();
                    if (!ip.isLoopbackAddress()
                            && ip.getHostAddress().matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
                        bFindIP = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取ip地址失败：{}",e);
        }
        if (null != ip) {
            sIP = ip.getHostAddress();
        }
        return sIP;
    }



}
