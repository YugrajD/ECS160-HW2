package com.ecs160.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;

import redis.clients.jedis.Jedis;

public class RedisDB {

    private Jedis jedisSession;

    private RedisDB() {
        this.jedisSession = new Jedis("localhost", 6379);
    }

    public boolean persist(Object obj) throws IllegalAccessException {
        try {
            if (obj == null) return false;
            Class<?> clazz = obj.getClass();
            if (!clazz.isAnnotationPresent(PersistableObject.class)) {
                return false;
            }

            Map<String, String> jedisMap = new HashMap<>();
            Object idValue = getId(obj);

            if (idValue == null) {
                return false;
            }
            // Joins object name with its id to create key
            String className = obj.getClass().getSimpleName();
            String jedisKey;

            if (className.equals("Repo")) {
                jedisKey = "reponame:" + idValue.toString();
            }

            else if (className.equals("Issue")) {
                jedisKey = idValue.toString();
            }

            else {
                jedisKey = className + ":" + idValue.toString();
            }

            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(PersistableField.class)) {
                    f.setAccessible(true);
                    Object fieldVal = f.get(obj);

                    if (fieldVal == null) {
                        continue;
                    }

                    // Handle Lists
                    if (fieldVal instanceof List) {
                        List<?> list = (List<?>) fieldVal;
                        StringBuilder sb = new StringBuilder();
                        for (Object item : list) {
                            // Recursively persist if it's a PersistableObject
                            if (item.getClass().isAnnotationPresent(PersistableObject.class)) {
                                persist(item);
                                Object itemId = getId(item);
                                sb.append(itemId.toString()).append(",");
                            } else {
                                sb.append(item.toString()).append(",");
                            }
                        }
                        if (sb.length() > 0) {
                            sb.setLength(sb.length() - 1); // remove trailing comma
                        }
                        jedisMap.put(f.getName(), sb.toString());
                    } 
                    // Handle single persistable objects
                    else if (fieldVal.getClass().isAnnotationPresent(PersistableObject.class)) {
                        persist(fieldVal);
                        Object childId = getId(fieldVal);
                        jedisMap.put(f.getName(), childId.toString());
                    } 
                    // Handle primitives/strings
                    else {
                        jedisMap.put(f.getName(), fieldVal.toString());
                    }
                }
            }
            if (!jedisMap.isEmpty()) {
                jedisSession.hset(jedisKey, jedisMap);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void load(Object obj) {
        try {
            Class<?> clazz = obj.getClass();
            Object idValue = getId(obj);

            if (idValue == null) {
                return false;
            }
            // Joins object name with its id to create key
            String className = obj.getClass().getSimpleName();
            String jedisKey;

            if (className.equals("Repo")) {
                jedisKey = "reponame:" + idValue.toString();
            }

            else if (className.equals("Issue")) {
                jedisKey = idValue.toString();
            }

            else {
                jedisKey = className + ":" + idValue.toString();
            }
            
            Map<String, String> data = jedisSession.hgetAll(jedisKey);
            
            if (data == null || data.isEmpty()) return;

            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(PersistableField.class) && data.containsKey(f.getName())) {
                    f.setAccessible(true);
                    String redisVal = data.get(f.getName());

                    if (List.class.isAssignableFrom(f.getType())) {
                        // Handle loading Lists
                        Class<?> itemType = getObjectType(f);
                        List<Object> list = new ArrayList<>();
                        
                        if (redisVal != null && !redisVal.isEmpty()) {
                            String[] items = redisVal.split(",");
                            for (String itemStr : items) {
                                // Check if the item type itself is a persistable object
                                if (itemType.isAnnotationPresent(PersistableObject.class)) {
                                    Object childObj = itemType.getDeclaredConstructor().newInstance();
                                    // We need to set the ID on the child to load it
                                    setId(childObj, itemStr);
                                    // Recursive load
                                    load(childObj);
                                    list.add(childObj);
                                } else {
                                    // Basic type list
                                    list.add(convertType(itemStr, itemType));
                                }
                            }
                        }
                        f.set(obj, list);
                    } else if (f.getType().isAnnotationPresent(PersistableObject.class)) {
                         // Handle loading single child object
                         Object childObj = f.getType().getDeclaredConstructor().newInstance();
                         setId(childObj, redisVal);
                         load(childObj);
                         f.set(obj, childObj);
                    } else {
                        // Basic types
                        f.set(obj, convertType(redisVal, f.getType()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper to get ID value from object
    private Object getId(Object obj) throws IllegalAccessException {
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) {
                f.setAccessible(true);
                return f.get(obj);
            }
        }
        return null;
    }

    // Helper to set ID value on object
    private void setId(Object obj, String val) throws Exception {
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) {
                f.setAccessible(true);
                // Convert string val to actual ID type
                f.set(obj, convertType(val, f.getType()));
                return;
            }
        }
    }

    // Converts type from string to desiredType
    private Object convertType(String value, Class<?> desiredType) {
        if (desiredType == String.class) return value;
        if (desiredType == int.class || desiredType == Integer.class) return Integer.parseInt(value);
        if (desiredType == long.class || desiredType == Long.class) return Long.parseLong(value);
        if (desiredType == boolean.class || desiredType == Boolean.class) return Boolean.parseBoolean(value);
        if (desiredType == double.class || desiredType == Double.class) return Double.parseDouble(value);
        return value;
    }

    // Fix ?
    private Class<?> getObjectType(Field f) {
        Type genericType = f.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type objectType = pt.getActualTypeArguments()[0];
            return (Class<?>) objectType;
        }
        return f.getType();
    }
}