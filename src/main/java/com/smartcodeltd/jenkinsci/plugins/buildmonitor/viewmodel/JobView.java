package com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel;

import hudson.model.*;
import hudson.scm.ChangeLogSet;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.*;

import static hudson.model.Result.SUCCESS;

/**
 * @author Jan Molak
 */
public class JobView {
    private final Date systemTime;
    private final Job<?, ?> job;

    public static JobView of(Job<?, ?> job) {
        return new JobView(job, new Date());
    }

    public static JobView of(Job<?, ?> job, Date systemTime) {
        return new JobView(job, systemTime);
    }

    @JsonProperty
    public String name() {
        return (null != job.getDisplayNameOrNull())
                ? job.getDisplayName()
                : job.getName();
    }

    @JsonProperty
    public String url() {
        return job.getUrl();
    }

    @JsonProperty
    public String status() {
        String status = isSuccessful() ? "successful" : "failing";

        if (isRunning()) {
            status += " running";
        }

        for (JobView downstream : downstreamJobs()) {
            if (downstream.status().equals("failing")) {
                status = "failing";
            }
        }

        return status;
    }

    @JsonProperty
    public String buildName() {
        return job.getLastBuild() != null
                ? job.getLastBuild().getDisplayName()
                : null;
    }

    @JsonProperty
    public String buildUrl() {
        return job.getLastBuild() != null
                ? job.getLastBuild().getUrl()
                : null;
    }

    @JsonProperty
    public int progress() {
        if (! isRunning()) {
            return 0;
        }

        final long now      = systemTime.getTime(),
                   duration = now - whenTheLastBuildStarted();

        if (duration > estimatedDuration()) {
            return 100;
        }

        if (estimatedDuration() > 0) {
            return (int) ((float) duration / (float) estimatedDuration() * 100);
        }

        return 100;
    }

    @JsonProperty
    public Set<String> culprits() {
        Set<String> culprits = new HashSet<String>();

        Run<?, ?> run = job.getLastBuild();

        while (run != null && ! SUCCESS.equals(run.getResult())) {

            if (run instanceof AbstractBuild<?, ?>) {
                AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;

                if (! (isRunning(build))) {
                    for (User culprit : build.getCulprits()) {
                        culprits.add(culprit.getFullName());
                    }
                }
            }

            run = run.getPreviousBuild();
        }

        return culprits;
    }

    @JsonProperty
    public List<String> changes() {
        Run<?, ?> run = job.getLastBuild();
        List<String> changes = new ArrayList<String>();

        if (run instanceof AbstractBuild<?, ?>) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;

            for (ChangeLogSet.Entry e : build.getChangeSet()) {
                changes.add(e.getCommitId() + ": " + e.getAuthor() + " - " + e.getMsg());
            }
        }

        return changes;
    }

    @JsonProperty
    public List<JobView> downstreamJobs() {
        List<JobView> jobs = new ArrayList<JobView>();

        Run<?, ?> run = job.getLastBuild();
        if (run instanceof AbstractBuild<?, ?>) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            List<AbstractProject> downstreamProjects = build.getProject().getDownstreamProjects();
            for (AbstractProject ap : downstreamProjects) {
                jobs.add(JobView.of(ap));
            }
        }

        return jobs;
    }

    public String toString() {
        return name();
    }


    private JobView(Job<?, ?> job, Date systemTime) {
        this.job = job;
        this.systemTime = systemTime;
    }

    private long whenTheLastBuildStarted() {
        return job.getLastBuild().getTimestamp().getTimeInMillis();
    }

    private long estimatedDuration() {
        return job.getLastBuild().getEstimatedDuration();
    }

    private Result lastResult() {
        Run<?, ?> lastBuild = job.getLastBuild();
        if (isRunning()) {
            lastBuild = lastBuild.getPreviousBuild();
        }

        return lastBuild != null
                ? lastBuild.getResult()
                : Result.NOT_BUILT;
    }

    private boolean isSuccessful() {
        return lastResult() == Result.SUCCESS;
    }

    private boolean isRunning() {
        return isRunning(job.getLastBuild());
    }

    private boolean isRunning(Run<?, ?> build) {
        return (build != null)
                && (build.hasntStartedYet() || build.isBuilding() || build.isLogUpdated());
    }
}