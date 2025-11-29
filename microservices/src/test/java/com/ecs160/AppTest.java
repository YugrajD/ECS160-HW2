package com.ecs160;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.ecs160.microservices.BugFinderMicroservice;
import com.ecs160.microservices.IssueComparatorMicroservice;
import com.ecs160.microservices.IssueSummarizerMicroservice;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */

    @Test
    public void testIssueSummarizerMicroservice() {
        IssueSummarizerMicroservice issueSummarizer = new IssueSummarizerMicroservice();
        String input = "{\"id\":67, \"title\":\"It boom up\"}";
        String result = issueSummarizer.summarizeIssue(input);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testBugFinderMicroservice() {
        BugFinderMicroservice bugFinder = new BugFinderMicroservice();
        String input = "int main() { return 0; }";
        String result = bugFinder.findBugs(input);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testIssueComparatorMicroservice() {
        IssueComparatorMicroservice issueComparator = new IssueComparatorMicroservice();
        String input = "[ [{\"bug_type\":\"BOOM\"}], [{\"bug_type\":\"BOOM\"}] ]";
        String result = issueComparator.checkEquivalence(input);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
