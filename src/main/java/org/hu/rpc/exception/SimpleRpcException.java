package org.hu.rpc.exception;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/31 4:26 PM
 **/
public class SimpleRpcException extends RuntimeException{

    public SimpleRpcException(String msg, Throwable cause){
        super(msg,cause);
    }

    public SimpleRpcException(String msg){

        super(msg);
    }
}
