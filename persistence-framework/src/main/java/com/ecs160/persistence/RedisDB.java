package com.ecs160.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;
import com.ecs160.persistence.annotations.LazyLoad;

import redis.clients.jedis.Jedis;



public class RedisDB {

    private Jedis jedisSession;
    private static RedisDB instance = null;

    private RedisDB() {
        this.jedisSession = new Jedis("localhost", 6379);
    }


    public boolean persist(Object obj) throws IllegalAccessException {
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

        for (Field f: obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(PersistableField.class)) {
                f.setAccessible(true);
                Object fieldVal = f.get(obj);

                if (fieldVal == null) {
                    continue;
                }
                // Stores list of child objects
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
                // Stores singular child object
                else if (fieldVal.getClass().isAnnotationPresent(PersistableObject.class)) {
                    persist(fieldVal);
                    Object childObjectId = getId(fieldVal);
                    jedisMap.put(f.getName(), childObjectId.toString());
                }
                // Stores field
                else {
                    jedisMap.put(f.getName(), fieldVal.toString());
                }
            }
        }
        // Stores object in Redis with its name + id as key
        jedisSession.hset(jedisKey, jedisMap);
        return true;
    }


    public Object load(Object object) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        Object idValue = getId(object);
        
        if (idValue == null) {
            return;
        }

        String className = object.getClass().getSimpleName();
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

        Map<String, String> jedisData = jedisSession.hgetAll(jedisKey);
        Map<Method, String> lazyLoadFields = new HashMap<>();

        if (jedisData == null || jedisData.isEmpty()) {
            throw new RuntimeException(idValue + " does not exist or contains nothing");
        }
        // Locates which method contains the @LazyLoad annotation
        for (Method m: object.getClass().getDeclaredMethods()) {
            m.setAccessible(true);
            // Maps the method with the name of the field
            if (m.isAnnotationPresent(LazyLoad.class)) {
                lazyLoadFields.put(m, m.getAnnotation(LazyLoad.class).field());
            }
        }

        for (Field f: object.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            // Loads list of child objects
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
            // Loads singular child object
            else if (f.isAnnotationPresent(PersistableObject.class)) {
                Object childObject = f.get(object);

                if (childObject == null) {
                    childObject = f.getType().getDeclaredConstructor().newInstance();
                    f.set(object, childObject);
                }

                load(childObject);
            }
            // Loads field
            else if (f.isAnnotationPresent(PersistableField.class)) {
                // Skips loading field if it is to be lazy loaded
                if (lazyLoadFields.containsValue(f.getName())) {
                    continue;
                }

                else {
                    String fieldVal = jedisData.get(f.getName());

                    if (fieldVal == null) {
                        continue;
                    }

                    Object fieldValue = convertType(fieldVal, f.getType());
                    f.set(object, fieldValue);
                }
            }
        }

        if (!lazyLoadFields.isEmpty()) {
            ProxyCreator proxyCreator = new ProxyCreator();
            object = proxyCreator.createProxy(object, this);
        }

        return object;
    }

    // Returns the Id of the object
    private Object getId(Object obj) throws IllegalAccessException {
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
    private void setId(Object obj, Object idValue) throws IllegalAccessException {
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

        else {
            throw new RuntimeException("Unsupported type: " + desiredType);
        }
    }


    private Class<?> getObjectType(Field f) {
        Type genericType = f.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type objectType = parameterizedType.getActualTypeArguments()[0];
            return (Class<?>) objectType;
        }

        return Object.class;
    }


    public Object lazyLoad(Object obj, Field fieldName) throws IllegalAccessException, IllegalArgumentException {
        fieldName.setAccessible(true);
        Object fieldValue = fieldName.get(obj);
        Object idValue = getId(obj);
        String jedisKey = obj.getClass().getSimpleName() + ":" + idValue.toString();
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
                    Object childObject = listObjectType.getDeclaredConstructor().newInstance();
                    setId(childObject, childId);
                    childObjects.add(childObject);
                }

                fieldName.set(obj, childObjects);
                return childObjects;
            }
            // Handles field being a singular child object
            else {
                if (childIdString == null || childIdString.isEmpty()) {
                    fieldName.set(obj, null);
                    return null;
                }

                Object childObject = fieldName.getType().getDeclaredConstructor().newInstance();
                setId(childObject, childIdString);
                fieldName.set(obj, childObject);
                return childObject;
            }
        }
        // Field is already loaded
        else {
            return fieldValue;
        }
    }

    public static RedisDB getInstance() {
        if (instance == null) {
            instance = new RedisDB();
        }

        return instance;
    }
}


// Inspired from Proxy Logging Class Demo
    public class ProxyCreator {
        private HashMap<String, Class<?>> proxyClassCache = new HashMap<>();

        class LazyLoadHandler implements MethodHandler {
            private Map<Method, String> lazyLoadFields;
            private Object target;
            private RedisDB redisDB;

            LazyLoadHandler(Map<Method,String> lazyLoadFields, Object target, RedisDB redisDB) {
                this.lazyLoadFields = lazyLoadFields;
                this.target = target;
                this.redisDB = redisDB;
            }

            @Override
            public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable, NoSuchFieldException {
                // Check if the method is annotated with @LazyLoad
                if (lazyLoadFields.containsKey(thisMethod)) {
                    String fieldName = lazyLoadFields.get(thisMethod);
                    Field field = target.getClass().getDeclaredField(fieldName);
                    return redisDB.lazyLoad(target, field);
                    // Lazy load
                }

                return proceed.invoke(self, args);
            }
        }

        public Object createProxy (Object obj, RedisDB redisDB) throws Exception {
        Class<?> clazz = obj.getClass();
        String className = clazz.getName();
        Class<?> proxyClass;
        // avoid creating the proxy each time
        if (proxyClassCache.containsKey(className)) {
            proxyClass = proxyClassCache.get(className);
        }

        else {
            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(clazz);
            proxyClass = factory.createClass();
            proxyClassCache.put(className, proxyClass);
        }

        // Locates which method contains the @LazyLoad annotation
        Map<Method, String> lazyLoadFields = new HashMap<>();
        for (Method m: clazz.getDeclaredMethods()) {
            m.setAccessible(true);
            // Maps the method with the name of the field
            if (m.isAnnotationPresent(LazyLoad.class)) {
                lazyLoadFields.put(m, m.getAnnotation(LazyLoad.class).field());
            }
        }

        Object proxyInstance = proxyClass.getDeclaredConstructor().newInstance();

        ((ProxyObject) proxyInstance).setHandler(new LazyLoadHandler(lazyLoadFields, obj, redisDB));

        return proxyInstance;
    }
    }