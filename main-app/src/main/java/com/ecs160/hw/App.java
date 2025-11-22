package com.ecs160.hw;

import java.lang.reflect.Constructor;

import com.ecs160.persistence.RedisDB;

/**
 * Hello world!
 *
 */
public class App 
{
    public static RedisDB loadRedisDB() throws Exception {
        Constructor<RedisDB> c = RedisDB.class.getDeclaredConstructor();
        c.setAccessible(true);
        return c.newInstance();
    }

    public static void main( String[] args ) {

    }
}
