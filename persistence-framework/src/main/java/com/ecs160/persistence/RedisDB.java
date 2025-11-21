package com.ecs160.persistence;

import com.ecs160.persistence.annotations.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
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
        this.jedisSession = new Jedis("localhost", 6379);
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

        jedisSession.hset(jedisKey, jedisMap);
        return true;
    }


    public Object load(Object object) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        Object idValue = getId(object);
        String jedisKey = object.getClass().getSimpleName() + ":" + idValue.toString();
        Map<String, String> jedisData = jedisSession.hgetAll(jedisKey);

        if (jedisData == null || jedisData.isEmpty()) {
            throw new RuntimeException(idValue + " does not exist or contains nothing");
        }
        
        for (Field f: object.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            if (List.class.isAssignableFrom(f.getType())) {
                String childIdString = jedisData.get(f.getName());

                if (childIdString == null) {
                    continue;
                }

                String[] childIdsList = childIdString.split(",");

                Class<?> listObjectType = getObjectType(f);
                List<Object> childObjects = (List<Object>) f.get(object);
                
                if (childObjects == null) {
                    childObjects = new ArrayList<>();
                    f.set(object, childObjects);
                }

                for (String childId : childIdsList) {
                    Object childObject = listObjectType.getDeclaredConstructor().newInstance();
                    Object id = childId;
                    setId(childObject, id);
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

                Object fieldValue = convertType(fieldVal, f.getType());
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

    // Sets id for object
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

    // Converts type from string to desiredType
    private Object convertType(String value, Class<?> desiredType) {
        if (desiredType == String.class) {
            return value;
        }

        else if (desiredType == int.class || desiredType == Integer.class) {
            return Integer.parseInt(value);
        }

        else if (desiredType == long.class || desiredType == Long.class) {
            return Long.parseLong(value);
        }

        else if (desiredType == boolean.class || desiredType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }

        else if (desiredType == double.class || desiredType == Double.class) {
            return Double.parseDouble(value);
        }

        throw new RuntimeException("Unsupported type: " + desiredType);
    }


    private Class<?> getObjectType(Field f) {
        Type genericType = f.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type objectType = pt.getActualTypeArguments()[0];
            return (Class<?>) objectType;
        }

        return Object.class;
    }
}
