package com.ecs160;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;
import com.ecs160.persistence.annotations.LazyLoad;


import com.ecs160.persistence.RedisDB;

/**
 * Unit test for simple App.
 */


public class LazyLoadAppTest
{
    /**
     * Rigorous Test :-)
     */

    @PersistableObject
    public static class LazyLoadTestingRepo {
        @Id
        public String name;

        @PersistableField
        public String Url;

        @PersistableField
        public String Issues;

        @LazyLoad(field = "Issues")
        public String getIssues() {
            return Issues;
        }
    }

    @Test
    public void testLoadAndPresist() throws Exception {
        RedisDB db = RedisDB.getInstance();

        LazyLoadTestingRepo initialRepo = new LazyLoadTestingRepo();
        initialRepo.name = "LazyLoadingTestRepo";
        initialRepo.Url = "http://github.com/LazyLoadingTestRepo";
        initialRepo.Issues = "iss-1,iss-2,iss-3";

        boolean saved = db.persist(initialRepo);
        assertTrue(saved);

        LazyLoadTestingRepo loadedRepo = new LazyLoadTestingRepo();
        loadedRepo.name = "LazyLoadingTestRepo";
        loadedRepo = (LazyLoadTestingRepo) db.load(loadedRepo);
        assertEquals(initialRepo.name, loadedRepo.name);
        assertEquals(initialRepo.Url, loadedRepo.Url);
        // Issues should not be loaded yet
        assertNull(loadedRepo.Issues);
        // Calling getIssues() should trigger lazy loading
        String issues = loadedRepo.getIssues();
        assertEquals(initialRepo.Issues, loadedRepo.Issues);
    }
}