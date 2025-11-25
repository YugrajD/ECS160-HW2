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
import java.util.ArrayList;
import java.util.List;

import com.ecs160.hw.model.Issue;
import com.ecs160.hw.model.Repo;
import static com.ecs160.hw.service.GitService.cloneRepo;
import com.ecs160.persistence.RedisDB;
import com.ecs160.hw.service.GitService;
import com.ecs160.hw.util.JsonHandler;

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
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();

        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }

    public static void main( String[] args ) {
        try {
            Path configPath = Path.of("selected_repo.dat");
            List<String> fileLines = Files.readAllLines(configPath);

            String repoName = fileLines.get(0).trim();
            List<String> cppFiles = fileLines.subList(1, fileLines.size());

            RedisDB redisDB = loadRedisDB();

            Repo repo = new Repo();
            repo.name = repoName;
            redisDB.load(repo);

            System.out.println("Repository Name: " + repo.name);
            System.out.println("Repository URL: " + repo.url);
            System.out.println("Repository Issues: " + repo.issues);

            cloneRepo(repo.url, repo.name);

            // calling microservice A
            List<String> summarizedIssues = new ArrayList<>();
            for (String issueID : repo.issues.split(",")) {
                Issue issue = new Issue();
                issue.issueID = issueID;
                redisDB.load(issue);

                String summarizeIssue = getRequestSender("summarize_issue", issue.description);
                summarizedIssues.add(summarizeIssue);
                System.out.println("Summarized Issue: " + summarizeIssue);
            }

            // calling microservice B
            List<String> bugFinder = new ArrayList<>();
            for (String cppFile : cppFiles) {
                String fileContent = GitService.readFile(repo.name, cppFile);
                String sendContent = getRequestSender("find_bugs", fileContent);
                bugFinder.add(sendContent);
                System.out.println("Bug Finder Output for " + cppFile + ": " + sendContent);
            }


            // Completed the comparator logic using JsonHandler
            String comparatorInput = JsonHandler.createComparatorInput(summarizedIssues, bugFinder);
            String comparator = getRequestSender("check_equivalence", comparatorInput);
            
            System.out.println("\nFinal Analysis Result:");
            System.out.println(comparator);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}