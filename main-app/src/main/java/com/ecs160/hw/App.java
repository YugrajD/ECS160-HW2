package com.ecs160.hw;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.ecs160.hw.model.Issue;
import com.ecs160.hw.model.Repo;
import com.ecs160.hw.service.GitService;
import static com.ecs160.hw.service.GitService.cloneRepo;
import com.ecs160.hw.util.JsonHandler;
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
            List<String> cFiles = fileLines.subList(1, fileLines.size());

            RedisDB redisDB = loadRedisDB();

            Repo repo = new Repo();
            repo.name = repoName;
            redisDB.load(repo);

            System.out.println("Repository Name: " + repo.name);
            System.out.println("Repository URL: " + repo.Url + '/' + repo.name);
            System.out.println("Repository Issues: " + repo.Issues);

            File repoFolder = new File(repoName);
            if (repoFolder.exists()) {
                System.out.println("Repository has already been cloned.");
            } else {
                System.out.println("Cloning repository");
                cloneRepo(repo.Url + '/' + repo.name, repo.name);
                System.out.println("Cloning completed.");
            }

            // calling microservice A
            List<String> summarizedIssues = new ArrayList<>();
            for (String id : repo.Issues.split(",")) {
                Issue issue = new Issue();
                issue.id = id;
                redisDB.load(issue);

                String summarizeIssue = getRequestSender("summarize_issue", issue.Description);
                summarizedIssues.add(summarizeIssue);
                System.out.println("Summarized Issue: " + summarizeIssue);
            }

            // calling microservice B
            List<String> bugFinder = new ArrayList<>();
            for (String cFile : cFiles) {
                String fileContent = GitService.readFile(repo.name, cFile);
                String sendContent = getRequestSender("find_bugs", fileContent);
                bugFinder.add(sendContent);
                System.out.println("Bug Finder Output for " + cFile + ": " + sendContent);
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