package com.ecs160.persistence;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import redis.clients.jedis.Jedis;



public class RedisDB {

    private Jedis jedisSession;

    private RedisDB() {
        jedisSession = new Jedis("localhost", 6379);;
    }


    public boolean persist(Object obj) {
        return false;
    }


    public Object load(Object object)  {
        return null;
    }

}
