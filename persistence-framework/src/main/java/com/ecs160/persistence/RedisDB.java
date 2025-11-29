package com.ecs160.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;
import com.ecs160.persistence.annotations.LazyLoad;

import redis.clients.jedis.Jedis;

// Proxy Imports
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;

public class RedisDB {

    private Jedis jedisSession;
    private static RedisDB instance = null;

    private RedisDB() {
        this.jedisSession = new Jedis("localhost", 6379);
    }

    public static RedisDB getInstance() {
        if (instance == null) {
            instance = new RedisDB();
        }

        return instance;
    }

    public boolean persist(Object obj) throws IllegalAccessException {
        try {
            if (obj == null) {
                return false;
            }

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
            String className = clazz.getSimpleName();
            String jedisKey;

            if (className.equals("Repo")) {
                jedisKey = "reponame:" + idValue.toString();
            } 
            
            else if (className.equals("Issue")) {
                if (idValue.toString().startsWith("iss-")) {
                    jedisKey = idValue.toString();
                } 
                
                else {
                    jedisKey = "iss-" + idValue.toString();
                }
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
                            } 
                            
                            else {
                                sb.append(item.toString()).append(",");
                            }
                        }

                        if (sb.length() > 0) {
                            // Removes trailing comma
                            sb.setLength(sb.length() - 1);
                        }
                        // Stores list as comma-separated values
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
            // Stores the object in Redis if it has fields to store
            if (!jedisMap.isEmpty()) {
                jedisSession.hset(jedisKey, jedisMap);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Object load(Object obj) throws Exception {
        try {
            Class<?> clazz = obj.getClass();
            Object idValue = getId(obj);
            
            if (idValue == null) {
                return obj;
            }

            // Joins object name with its id to create key
            String className = clazz.getSimpleName();
            String jedisKey;

            if (className.equals("Repo")) {
                jedisKey = "reponame:" + idValue.toString();
            } 
            
            else if (className.equals("Issue")) {
                if (idValue.toString().startsWith("iss-")) {
                    jedisKey = idValue.toString();
                } 
                
                else {
                    jedisKey = "iss-" + idValue.toString();
                }
            } 
            
            else {
                jedisKey = className + ":" + idValue.toString();
            }

            Map<String, String> jedisData = jedisSession.hgetAll(jedisKey);
            
            // Locates which method contains the @LazyLoad annotation
            Map<Method, String> lazyLoadFields = new HashMap<>();
            for (Method m : clazz.getDeclaredMethods()) {
                m.setAccessible(true);
                // Maps the method with the name of the field
                if (m.isAnnotationPresent(LazyLoad.class)) {
                    lazyLoadFields.put(m, m.getAnnotation(LazyLoad.class).field());
                }
            }

            if (jedisData == null || jedisData.isEmpty()) {
                return obj;
            }

            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(PersistableField.class)) {
                    f.setAccessible(true);
                    
                    // Skips loading field if it is to be lazy loaded
                    if (lazyLoadFields.containsValue(f.getName())) {
                        continue; 
                    }

                    if (jedisData.containsKey(f.getName())) {
                        String jedisVal = jedisData.get(f.getName());

                        if (List.class.isAssignableFrom(f.getType())) {
                            // Handle loading Lists
                            Class<?> itemType = getObjectType(f);
                            List<Object> childObjects = new ArrayList<>();
                            
                            if (jedisVal != null && !jedisVal.isEmpty()) {
                                String[] items = jedisVal.split(",");

                                for (String itemStr : items) {
                                    // Check if the item type itself is a persistable object
                                    if (itemType.isAnnotationPresent(PersistableObject.class)) {
                                        Object childObj = itemType.getDeclaredConstructor().newInstance();
                                        // We need to set the ID on the child to load it
                                        setId(childObj, itemStr);
                                        // Recursively loads the child object
                                        load(childObj);
                                        childObjects.add(childObj);
                                    } 
                                    
                                    else {
                                        // Basic type list
                                        childObjects.add(convertType(itemStr, itemType));
                                    }
                                }
                            }

                            f.set(obj, childObjects);
                        } 
                        
                        else if (f.getType().isAnnotationPresent(PersistableObject.class)) {
                             // Handle loading single child object
                             Object childObj = f.getType().getDeclaredConstructor().newInstance();
                             setId(childObj, jedisVal);
                             load(childObj);
                             f.set(obj, childObj);
                        } 
                        
                        else {
                            // Basic types
                            f.set(obj, convertType(jedisVal, f.getType()));
                        }
                    }
                }
            }
            // Creates a proxy object if there is a lazy load field
            if (!lazyLoadFields.isEmpty()) {
                ProxyCreator proxyCreator = new ProxyCreator();
                obj = proxyCreator.createProxy(obj, this);
            }

            return obj;

        } catch (Exception e) {
            e.printStackTrace();

            return obj;
        }
    }

    // Lazy loads a field
    public Object lazyLoad(Object obj, Field fieldName) throws Exception {
        fieldName.setAccessible(true);
        Object fieldValue = fieldName.get(obj);
        Object idValue = getId(obj);
        
        // Reconstruct Key
        String className = obj.getClass().getSuperclass().getSimpleName(); // Use superclass because obj is a Proxy now
        String jedisKey;

        if (className.equals("Repo")) {
            jedisKey = "reponame:" + idValue.toString();
        } 
        
        else if (className.equals("Issue")) {
            jedisKey = (idValue.toString().startsWith("iss-") ? "" : "iss-") + idValue.toString();
        } 
        
        else {
            jedisKey = className + ":" + idValue.toString();
        }

        String childIdString = jedisSession.hget(jedisKey, fieldName.getName());

        // Field is NOT loaded yet
        if (fieldValue == null || (fieldValue instanceof List<?> list && list.isEmpty())) {
            // Handles field being a list
            if (List.class.isAssignableFrom(fieldName.getType())) {
                Class<?> listObjectType = getObjectType(fieldName);
                List<Object> childObjects = new ArrayList<>();

                if (childIdString == null || childIdString.isEmpty()) {
                    fieldName.set(obj, childObjects);

                    return childObjects;
                }

                String[] childIdsList = childIdString.split(",");

                for (String childId : childIdsList) {
                    // Check if the item type itself is a persistable object
                    if (listObjectType.isAnnotationPresent(PersistableObject.class)) {
                        Object childObject = listObjectType.getDeclaredConstructor().newInstance();
                        // We need to set the ID on the child to load it
                        setId(childObject, childId);
                        // Recursively loads the child object
                        load(childObject);
                        childObjects.add(childObject);
                    } 
                    
                    else {
                        // Basic types
                        childObjects.add(convertType(childId, listObjectType));
                    }
                }

                fieldName.set(obj, childObjects);

                return childObjects;
            } 
            // Handles field being a singular child object
            else if (fieldName.getType().isAnnotationPresent(PersistableObject.class)) {
                if (childIdString == null || childIdString.isEmpty()) {
                    fieldName.set(obj, null);

                    return null;
                }

                Object childObject = fieldName.getType().getDeclaredConstructor().newInstance();
                setId(childObject, childIdString);
                load(childObject);
                fieldName.set(obj, childObject);

                return childObject;
            }
            // Handles field being a basic type
            else {
                Object basicValue = convertType(childIdString, fieldName.getType());
                fieldName.set(obj, basicValue);
                return basicValue;
            }
        } 
        // Field is already loaded
        else {
            return fieldValue;
        }
    }

    // Helper to get ID value from object
    private Object getId(Object obj) throws IllegalAccessException {
        Class<?> clazz = obj.getClass();
        // If proxy, check superclass for annotations
        if (obj instanceof ProxyObject) {
            clazz = clazz.getSuperclass();
        }

        for (Field f : clazz.getDeclaredFields()) {
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

    // Helper to convert type from string to desiredType
    private Object convertType(String value, Class<?> desiredType) {
        if (desiredType == String.class) {
            return value;
        }
        
        if (desiredType == int.class || desiredType == Integer.class) {
            return Integer.parseInt(value);
        }

        if (desiredType == long.class || desiredType == Long.class) {
            return Long.parseLong(value);
        }

        if (desiredType == boolean.class || desiredType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }

        if (desiredType == double.class || desiredType == Double.class) {
            return Double.parseDouble(value);
        }

        return value;
    }

    // Helper to get the object type of a list field
    private Class<?> getObjectType(Field f) {
        Type genericType = f.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type objectType = pt.getActualTypeArguments()[0];
            return (Class<?>) objectType;
        }

        return f.getType();
    }

    // Inspired from Proxy Logging Class Demo
    public static class ProxyCreator {
        private HashMap<String, Class<?>> proxyClassCache = new HashMap<>();

        static class LazyLoadHandler implements MethodHandler {
            private Map<Method, String> lazyLoadFields;
            private Object target;
            private RedisDB redisDB;

            LazyLoadHandler(Map<Method, String> lazyLoadFields, Object target, RedisDB redisDB) {
                this.lazyLoadFields = lazyLoadFields;
                this.target = target;
                this.redisDB = redisDB;
            }
            // Loads the lazy load field when its getter is called
            @Override
            public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
                // Check if method is intercepted for lazy loading
                // We need to match method names/signatures since the Proxy method object might differ from the Target method object
                for (Map.Entry<Method, String> entry : lazyLoadFields.entrySet()) {
                    Method lazyMethod = entry.getKey();

                    if (lazyMethod.getName().equals(thisMethod.getName()) && 
                        java.util.Arrays.equals(lazyMethod.getParameterTypes(), thisMethod.getParameterTypes())) {
                        
                        String fieldNameStr = entry.getValue();
                        Field field = target.getClass().getDeclaredField(fieldNameStr);
                        field.setAccessible(true);

                        return redisDB.lazyLoad(self, field);
                    }
                }

                return proceed.invoke(target, args);
            }
        }

        // Creates a proxy object of the given object
        public Object createProxy(Object obj, RedisDB redisDB) throws Exception {
            Class<?> clazz = obj.getClass();
            String className = clazz.getName();
            Class<?> proxyClass;

            // avoid creating the proxy each time
            if (proxyClassCache.containsKey(className)) {
                proxyClass = proxyClassCache.get(className);
            } else {
                ProxyFactory factory = new ProxyFactory();
                factory.setSuperclass(clazz);
                proxyClass = factory.createClass();
                proxyClassCache.put(className, proxyClass);
            }

            // Locates which method contains the @LazyLoad annotation
            Map<Method, String> lazyLoadFields = new HashMap<>();
            
            for (Method m : clazz.getDeclaredMethods()) {
                m.setAccessible(true);
                // Maps the method with the name of the field
                if (m.isAnnotationPresent(LazyLoad.class)) {
                    lazyLoadFields.put(m, m.getAnnotation(LazyLoad.class).field());
                }
            }

            Object proxyInstance = proxyClass.getDeclaredConstructor().newInstance();
            ((ProxyObject) proxyInstance).setHandler(new LazyLoadHandler(lazyLoadFields, obj, redisDB));
            
            // Copy fields from original to proxy
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object val = field.get(obj);
                field.set(proxyInstance, val);
            }

            return proxyInstance;
        }
    }
}