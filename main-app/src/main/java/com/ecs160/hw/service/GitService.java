package com.ecs160.hw.service;


public class GitService {
    public static void cloneRepo(String url, String directoryName) {
        ProcessBuilder repoBuilder = new ProcessBuilder("git", "clone", url, directoryName);
        repoBuilder.inheritIO();

        try {
            Process cloningProcess = repoBuilder.start();
            cloningProcess.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
