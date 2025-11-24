package com.ecs160.hw;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.ecs160.hw.model.Repo;
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

    public static void cloneRepo(String url, String directoryName) {
        ProcessBuilder repoBuilder = new ProcessBuilder("git", "clone", url, directoryName);
        repoBuilder.inheritIO();

        try {
            Process p = repoBuilder.start();
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main( String[] args ) throws Exception {
        File file = new File("selected_repo.dat");
        Scanner scanner = new Scanner(file);

        String repoName = scanner.nextLine();

        List<String> cppFiles = new ArrayList<>();
        while (scanner.hasNextLine()) {
            cppFiles.add(scanner.nextLine());
        }

        scanner.close();

        RedisDB redisDB = loadRedisDB();

        Repo repo = new Repo();
        repo.name = repoName;
        redisDB.load(repo);

        System.out.println("Repository Name: " + repo.name);
        System.out.println("Repository URL: " + repo.url);
        System.out.println("Repository Issues: " + repo.issues);

        cloneRepo(repo.url, repo.name);

    }
}
