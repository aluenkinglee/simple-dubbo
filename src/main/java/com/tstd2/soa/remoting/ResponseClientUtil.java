package com.tstd2.soa.remoting;

import com.esotericsoftware.reflectasm.MethodAccess;
import com.tstd2.soa.common.ReflectionCache;
import com.tstd2.soa.config.Service;
import com.tstd2.soa.remoting.ErrorCode;
import com.tstd2.soa.remoting.exchange.model.Request;
import com.tstd2.soa.remoting.exchange.model.Response;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.context.ApplicationContext;

public class ResponseClientUtil {

    public static Response response(Request request, Response response) {
        // 取出request对应sessionId
        response.setSessionId(request.getSessionId());

        try {
            Object result = reflect(request);
            response.setResult(result);
            response.setResultCode(ErrorCode.SUCCESS.errorCode);
        } catch (Exception e) {
            e.printStackTrace();
            response.setResultCode(ErrorCode.ERROR.errorCode);
            response.setErrorMsg(e.getMessage());
        }

        return response;
    }

    private static Object reflect(Request request) throws Exception {
        // 从spring服务实例对象
        ApplicationContext application = Service.getApplicationContext();
        Class<?> clazz = ReflectionCache.putAndGetClass(request.getClassName());
        Object serviceBean = application.getBean(clazz);

        // 代理对象里面方法名称和方法参数
//        Method method = clazz.getMethod(request.getMethodName(), request.getParametersType());
//        Object result = method.invoke(serviceBean, request.getParametersValue());

        // 用reflectasm提高反射性能
//        MethodAccess methodAccess = MethodAccess.get(serviceBean.getClass());
//        int methodIndex = methodAccess.getIndex(request.getMethodName(), request.getParametersType());
        MethodAccess methodAccess = ReflectionCache.putAndGetMethodAccess(serviceBean.getClass());
        int methodIndex = ReflectionCache.putAndGetMethodIndex(serviceBean.getClass(), request.getMethodName(), request.getParametersType(), methodAccess);
        Object result = methodAccess.invoke(serviceBean, methodIndex, request.getParametersValue());

        return result;
    }

}
