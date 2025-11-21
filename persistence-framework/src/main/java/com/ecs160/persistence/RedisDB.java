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
        jedisSession = new Jedis("localhost", 6379);
    }


    public boolean persist(Object obj) throws IllegalAccessException {
        Map<String, String> jedisMap = new HashMap<>();
        Object idValue = getId(obj);

        String jedisKey = obj.getClass().getSimpleName() + ":" + idValue.toString();

        for (Field f: obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(PersistableField.class)) {
                f.setAccessible(true);
                Object fieldVal = f.get(obj);

                if (fieldVal == null) {
                    continue;
                }

                if (List.class.isAssignableFrom(fieldVal.getClass())) {
                    List<?> childObjects = (List<?>) fieldVal;
                    List<String> childObjectIds = new ArrayList<>();

                    for (Object childObject : childObjects) {
                        if (childObject.getClass().isAnnotationPresent(PersistableObject.class)) {
                            persist(childObject);

                            Object childObjectId = getId(childObject);
                            childObjectIds.add(childObjectId.toString());
                        }
                    }

                    jedisMap.put(f.getName(), String.join(",", childObjectIds));
                }

                else if (fieldVal.getClass().isAnnotationPresent(PersistableObject.class)) {
                    persist(fieldVal);
                    Object childObjectId = getId(fieldVal);
                    jedisMap.put(f.getName(), childObjectId.toString());
                }

                else {
                    jedisMap.put(f.getName(), fieldVal.toString());
                }
            }
        }

        jedis.hset(jedisKey, jedisMap);
        return true;
    }


    public Object load(Object object)  {
        Object idValue = getId(object);
        String jedisKey = object.getClass().getSimpleName() + ":" + idValue.toString();
        Map<String, String> jedisData = jedis.hgetall(jedisKey);

        if (jedisData == null || jedisData.isEmpty()) {
            throw new RuntimeException(idValue + " does not exist or contains nothing");
        }
        
        for (Field f: object.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            if (List.class.isAssignableFrom(f.getType())) {
                List<Object> childObjects = (List<Object>) f.get(object);
                
                if (childObjects == null) {
                    childObjects = new ArrayList<>();
                    f.set(object, childObjects);
                }

                String childIds = jedisData.get(f.getName());
                String[] childIdsList = childIds.split(",");

                for (String childId : childIdsList) {
                    // Need to get type of things in the list
                    Object childObject = f.getType().getDeclaredConstructor().newInstance();
                    setId(childObject, childId);
                    load(childObject);
                    childObjects.add(childObject);
                }
            }

            else if (f.isAnnotationPresent(PersistableObject.class)) {
                Object childObject = f.get(object);

                if (childObject == null) {
                    childObject = f.getType().getDeclaredConstructor().newInstance();
                    f.set(object, childObject);
                }

                load(childObject);
            }

            else if (f.isAnnotationPresent(PersistableField.class)) {
                String fieldVal = jedisData.get(f.getName());

                if (fieldVal == null) {
                    continue;
                }
                // Need to make fieldVal become the type of Field f
                // fieldValue = 
                f.set(object, fieldValue);
            }
        }

        return object;
    }

    // Returns the Id of the object
    private Object getId(Object obj) throws IllegalAccessException{
        for (Field f: obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) {
                f.setAccessible(true);
                Object idValue = f.get(obj);

                if (idValue == null) {
                    throw new RuntimeException("Id field is null");
                }

                return idValue;
            }
        }

        throw new RuntimeException("No @Id annotation was found");
    }

    private void setId(Object obj, Object idValue) throws IllegalAccessException{
        for (Field f: obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) {
                f.setAccessible(true);
                f.set(obj, idValue);
                return;
            }
        }
        throw new RuntimeException("No @Id annotation was found");
    }

}
