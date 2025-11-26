package com.ecs160.hw.service;

import java.nio.file.Files;
import java.nio.file.Path;


public class GitService {
    public static void cloneRepo(String url, String directoryName) throws Exception{
        ProcessBuilder repoBuilder = new ProcessBuilder("git", "clone", "--depth", "1", url, directoryName);
        repoBuilder.inheritIO();
        Process cloningProcess = repoBuilder.start();
        cloningProcess.waitFor();
    }

    // reads the file in the directory at that path given
    public static String readFile(String repoDirectory, String path) throws Exception {
        Path filePath = Path.of(repoDirectory, path);
        return Files.readString(filePath);
    }
}
