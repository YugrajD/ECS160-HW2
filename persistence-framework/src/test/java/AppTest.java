package com.ecs160;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;


import com.ecs160.persistence.RedisDB;

/**
 * Unit test for simple App.
 */


public class AppTest
{
    /**
     * Rigorous Test :-)
     */

    @PersistableObject
    public static class TestingRepo {
        @Id
        public String name;

        @PersistableField
        public String Url;

        @PersistableField
        public String Issues;
    }

    @Test
    public void testLoadAndPresist() throws Exception {
        RedisDB db = RedisDB.getInstance();

        TestingRepo intialRepo = new TestingRepo();
        intialRepo.name = "TestRepo";
        intialRepo.Url = "http://github.com/TestRepo";
        intialRepo.Issues = "iss-1,iss-2,iss-3";

        boolean saved = db.persist(intialRepo);
        assertTrue(saved);

        TestingRepo loadedRepo = new TestingRepo();
        loadedRepo.name = "TestRepo";
        loadedRepo = (TestingRepo) db.load(loadedRepo);
        
        assertEquals(intialRepo.name, loadedRepo.name);
        assertEquals(intialRepo.Url, loadedRepo.Url);
        assertEquals(intialRepo.Issues, loadedRepo.Issues);
    }
}