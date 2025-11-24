package com.ecs160.hw;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.ecs160.hw.model.Repo;
import static com.ecs160.hw.service.GitService.cloneRepo;
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

    public static String getRequestSender (String endpoint, String input) {
        try {
            String encodingUrl = URLEncoder.encode(input, "UTF-8");

            String url = "http://localhost:8080/" + endpoint + "?" + encodingUrl;
            
            HttpClient client = HttpClient.newHttpClient();

            URI uri = new URI(url);
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri);

            requestBuilder.GET();

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            return "Error sending the GET request";
            
        }
    }

    public static void main( String[] args ) throws Exception {
        // Reads all lines in selected_repo.dat
        List<String> fileLines = Files.readAllLines(Path.of("selected_repo.dat"));
        // The first line is the name of the repo
        String repoName = fileLines.get(0);
        // The rest are the cpp files
        List<String> cppFiles = fileLines.subList(1, fileLines.size());

        RedisDB redisDB = loadRedisDB();

        Repo repo = new Repo();
        repo.name = repoName;
        redisDB.load(repo);

        System.out.println("Repository Name: " + repo.name);
        System.out.println("Repository URL: " + repo.url);
        System.out.println("Repository Issues: " + repo.issues);

        cloneRepo(repo.url, repo.name);

        String summarizeIssue = getRequestSender("summarize_issue", repo.issues);
        System.out.println("Summarized Issue: " + summarizeIssue);

        String bugFinder = getRequestSender("find_bugs", summarizeIssue);
        System.out.println("Bug Finder Output: " + bugFinder);

        String comparator = getRequestSender("check_equivalence", bugFinder);
        System.out.println("Comparator Output: " + comparator);



    }
}
